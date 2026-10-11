#include <jni.h>
#include <atomic>
#include <string>
#include "whisper.h"

struct Control { std::atomic<bool> cancelled{false}; };
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
    const char* path=env->GetStringUTFChars(model,nullptr);
    auto cp=whisper_context_default_params();cp.use_gpu=false;
    auto* ctx=whisper_init_from_file_with_params(path,cp);
    env->ReleaseStringUTFChars(model,path);
    if(!ctx) {fail(env,"No se pudo cargar el modelo Whisper local.");return nullptr;}
    jfloat* samples=env->GetFloatArrayElements(input,nullptr);
    if(!samples) {whisper_free(ctx);return nullptr;}
    auto params=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads=2;
    params.language="auto";
    params.translate=false;
    params.no_context=true;
    params.print_realtime=false;params.print_progress=false;params.print_timestamps=false;params.print_special=false;
    params.abort_callback=[](void* p){return ((Control*)p)->cancelled.load();};
    params.abort_callback_user_data=control;
    int status=whisper_full(ctx,params,samples,count);
    env->ReleaseFloatArrayElements(input,samples,JNI_ABORT);
    if(status!=0 || control->cancelled) {whisper_free(ctx);fail(env,"La transcripción se interrumpió; el original se conserva.");return nullptr;}
    std::string result="{\"segments\":[";
    for(int i=0;i<whisper_full_n_segments(ctx);i++) {
        if(i) result+=',';
        result+="{\"start_ms\":"+std::to_string(whisper_full_get_segment_t0(ctx,i)*10);
        result+=",\"end_ms\":"+std::to_string(whisper_full_get_segment_t1(ctx,i)*10);
        result+=",\"text\":"+quote(whisper_full_get_segment_text(ctx,i))+"}";
    }
    result+="],\"language\":"+quote(whisper_lang_str(whisper_full_lang_id(ctx)))+"}";
    whisper_free(ctx);
    jbyteArray bytes=env->NewByteArray((jsize)result.size());
    if(bytes) env->SetByteArrayRegion(bytes,0,(jsize)result.size(),(const jbyte*)result.data());
    return bytes;
}
