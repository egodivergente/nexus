#include <jni.h>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <thread>
#include <sys/stat.h>
#include "whisper.h"

struct Control { std::atomic<bool> cancelled{false}; };

static std::mutex g_whisper_mutex;
static whisper_context* g_cached_ctx = nullptr;
static std::string g_cached_model_path;
static time_t g_model_mtime = 0;
static off_t g_model_size = 0;

static void fail(JNIEnv* env,const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),message);
}
static std::string quote(const char* text) {
    if(!text) return "\"\"";
    std::string out="\"";
    for(const unsigned char* p=(const unsigned char*)text;*p;p++) {
        if(*p=='"' || *p=='\\') {out+='\\';out+=(char)*p;}
        else if(*p=='\n') out+="\\n";
        else if(*p=='\r') out+="\\r";
        else if(*p=='\t') out+="\\t";
        else if(*p<32) out+=' ';
        else out+=(char)*p;
    }
    return out+'"';
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_vidadomu_nexus_assistant_WhisperNative_createControl(JNIEnv*,jobject) {return (jlong)new Control();}
extern "C" JNIEXPORT void JNICALL
Java_com_vidadomu_nexus_assistant_WhisperNative_cancel(JNIEnv*,jobject,jlong handle) {
    if(handle) ((Control*)handle)->cancelled=true;
}
extern "C" JNIEXPORT void JNICALL
Java_com_vidadomu_nexus_assistant_WhisperNative_freeControl(JNIEnv*,jobject,jlong handle) {delete (Control*)handle;}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_vidadomu_nexus_assistant_WhisperNative_transcribe(JNIEnv* env,jobject,jstring model,jfloatArray input,jlong handle) {
    auto* control=(Control*)handle;
    const jsize count=env->GetArrayLength(input);
    if(!control || count<=0 || count>16000*900) {fail(env,"Audio fuera del límite de 15 minutos.");return nullptr;}
    const char* raw_path=env->GetStringUTFChars(model,nullptr);
    if(!raw_path) return nullptr;
    std::string path_str(raw_path);
    if(raw_path) env->ReleaseStringUTFChars(model,raw_path);

    std::lock_guard<std::mutex> lock(g_whisper_mutex);
    if(control->cancelled) {fail(env,"La transcripción se canceló.");return nullptr;}
    struct stat model_stat{};
    if(stat(path_str.c_str(), &model_stat)!=0) {fail(env,"El modelo Whisper no está disponible.");return nullptr;}
    if(!g_cached_ctx || g_cached_model_path!=path_str || g_model_mtime!=model_stat.st_mtime || g_model_size!=model_stat.st_size) {
        if(g_cached_ctx) {
            whisper_free(g_cached_ctx);
            g_cached_ctx=nullptr;
            g_cached_model_path.clear();
        }
        auto cp=whisper_context_default_params();cp.use_gpu=false;
        g_cached_ctx=whisper_init_from_file_with_params(path_str.c_str(),cp);
        if(g_cached_ctx) {g_cached_model_path=path_str;g_model_mtime=model_stat.st_mtime;g_model_size=model_stat.st_size;}
    }
    if(!g_cached_ctx) {fail(env,"No se pudo cargar el modelo Whisper local.");return nullptr;}
    jfloat* samples=env->GetFloatArrayElements(input,nullptr);
    if(!samples) return nullptr;
    auto params=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    int hw=(int)std::thread::hardware_concurrency();
    params.n_threads=std::min(4, std::max(1, hw > 2 ? hw - 2 : 1));
    params.language="auto";
    params.translate=false;
    params.no_context=true;
    params.print_realtime=false;params.print_progress=false;params.print_timestamps=false;params.print_special=false;
    params.abort_callback=[](void* p){return ((Control*)p)->cancelled.load();};
    params.abort_callback_user_data=control;
    int status=whisper_full(g_cached_ctx,params,samples,count);
    env->ReleaseFloatArrayElements(input,samples,JNI_ABORT);
    if(status!=0 || control->cancelled) {fail(env,"La transcripción se interrumpió; el original se conserva.");return nullptr;}
    std::string result="{\"segments\":[";
    for(int i=0;i<whisper_full_n_segments(g_cached_ctx);i++) {
        if(i) result+=',';
        result+="{\"start_ms\":"+std::to_string(whisper_full_get_segment_t0(g_cached_ctx,i)*10);
        result+=",\"end_ms\":"+std::to_string(whisper_full_get_segment_t1(g_cached_ctx,i)*10);
        result+=",\"text\":"+quote(whisper_full_get_segment_text(g_cached_ctx,i))+"}";
    }
    result+="],\"language\":"+quote(whisper_lang_str(whisper_full_lang_id(g_cached_ctx)))+"}";
    jbyteArray bytes=env->NewByteArray((jsize)result.size());
    if(bytes) env->SetByteArrayRegion(bytes,0,(jsize)result.size(),(const jbyte*)result.data());
    return bytes;
}

// Never free a context while transcription holds it, and never wait on the UI thread.
extern "C" JNIEXPORT void JNICALL
Java_com_vidadomu_nexus_assistant_WhisperNative_releaseCache(JNIEnv*,jobject) {
    std::unique_lock<std::mutex> lock(g_whisper_mutex, std::try_to_lock);
    if(!lock.owns_lock()) return;
    if(g_cached_ctx) whisper_free(g_cached_ctx);
    g_cached_ctx=nullptr;g_cached_model_path.clear();g_model_mtime=0;g_model_size=0;
}
