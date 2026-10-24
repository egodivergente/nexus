#include <jni.h>
#include <atomic>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include <algorithm>
#include <cstdint>
#if defined(__aarch64__)
#include "stable-diffusion.h"
#endif

static std::mutex execution_mutex;
static std::mutex context_mutex;
static std::mutex output_mutex;
static std::atomic<int> state{0};
static std::atomic<int> percent{0};
static std::atomic<bool> cancelled{false};
static std::vector<uint8_t> output;
#if defined(__aarch64__)
static sd_ctx_t* active_context = nullptr;
#endif

extern "C" JNIEXPORT jint JNICALL Java_com_vidadomu_nexus_inpaint_InpaintNative_status(JNIEnv*, jobject) { return state.load(); }
extern "C" JNIEXPORT jint JNICALL Java_com_vidadomu_nexus_inpaint_InpaintNative_progress(JNIEnv*, jobject) { return percent.load(); }
extern "C" JNIEXPORT void JNICALL Java_com_vidadomu_nexus_inpaint_InpaintNative_cancel(JNIEnv*, jobject) {
    cancelled = true;
#if defined(__aarch64__)
    std::lock_guard<std::mutex> guard(context_mutex);
    if (active_context) sd_cancel_generation(active_context, SD_CANCEL_ALL);
#endif
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_vidadomu_nexus_inpaint_InpaintNative_result(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> guard(output_mutex);
    if (state != 4 || output.empty()) return nullptr;
    auto result = env->NewByteArray(static_cast<jsize>(output.size()));
    if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(output.size()), reinterpret_cast<const jbyte*>(output.data()));
    return result;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_vidadomu_nexus_inpaint_InpaintNative_start(
    JNIEnv* env, jobject, jstring folder, jbyteArray prompt, jbyteArray rgb, jbyteArray mask, jint width, jint height, jint steps, jlong seed) {
#if !defined(__aarch64__)
    return false;
#else
    std::lock_guard<std::mutex> launch_guard(execution_mutex);
    if (state == 1 || state == 2 || state == 3 || width < 64 || height < 64 || width > 512 || height > 512 ||
        width % 64 || height % 64 || steps < 4 || steps > 40 || !folder || !prompt || !rgb || !mask ||
        env->GetArrayLength(rgb) != width * height * 3 || env->GetArrayLength(mask) != width * height) return false;
    const char* path_chars = env->GetStringUTFChars(folder, nullptr);
    if (!path_chars) return false;
    std::string path(path_chars);
    env->ReleaseStringUTFChars(folder, path_chars);
    std::string instruction(env->GetArrayLength(prompt), '\0');
    env->GetByteArrayRegion(prompt, 0, static_cast<jsize>(instruction.size()), reinterpret_cast<jbyte*>(instruction.data()));
    std::vector<uint8_t> pixels(width * height * 3), coverage(width * height);
    env->GetByteArrayRegion(rgb, 0, static_cast<jsize>(pixels.size()), reinterpret_cast<jbyte*>(pixels.data()));
    env->GetByteArrayRegion(mask, 0, static_cast<jsize>(coverage.size()), reinterpret_cast<jbyte*>(coverage.data()));
    if (env->ExceptionCheck() || instruction.empty() || std::none_of(coverage.begin(), coverage.end(), [](uint8_t value) { return value != 0; })) return false;
    cancelled = false;
    percent = 0;
    state = 1;
    { std::lock_guard<std::mutex> guard(output_mutex); output.clear(); }
    try {
        std::thread([path, instruction, pixels = std::move(pixels), coverage = std::move(coverage), width, height, steps, seed]() mutable {
            sd_ctx_t* context = nullptr;
            sd_image_t* images = nullptr;
            int count = 0;
            int final_state = 5;
            try {
                sd_set_log_callback([](sd_log_level_t, const char*, void*) {}, nullptr);
                sd_set_progress_callback([](int step, int total, float, void*) {
                    state = 2;
                    percent = total > 0 ? step * 100 / total : 0;
                }, nullptr);
                const auto clip = path + "/clip.safetensors";
                const auto unet = path + "/unet.safetensors";
                const auto vae = path + "/vae.safetensors";
                sd_ctx_params_t config;
                sd_ctx_params_init(&config);
                config.clip_l_path = clip.c_str();
                config.diffusion_model_path = unet.c_str();
                config.vae_path = vae.c_str();
                config.n_threads = 2;
                config.wtype = SD_TYPE_Q4_0;
                config.enable_mmap = true;
                config.flash_attn = true;
                config.backend = "CPU";
                context = new_sd_ctx(&config);
                { std::lock_guard<std::mutex> guard(context_mutex); active_context = context; }
                if (context && !cancelled) {
                    sd_img_gen_params_t params;
                    sd_img_gen_params_init(&params);
                    params.prompt = instruction.c_str();
                    params.init_image = {static_cast<uint32_t>(width), static_cast<uint32_t>(height), 3, pixels.data()};
                    params.mask_image = {static_cast<uint32_t>(width), static_cast<uint32_t>(height), 1, coverage.data()};
                    params.width = width;
                    params.height = height;
                    params.batch_count = 1;
                    params.seed = seed;
                    params.strength = 1.0f;
                    params.sample_params.sample_steps = steps;
                    params.sample_params.sample_method = EULER_SAMPLE_METHOD;
                    params.sample_params.guidance.txt_cfg = 7.0f;
                    params.vae_tiling_params.enabled = true;
                    params.vae_tiling_params.tile_size_w = 256;
                    params.vae_tiling_params.tile_size_h = 256;
                    if (generate_image(context, &params, &images, &count) && count == 1 && !cancelled &&
                        images[0].width == static_cast<uint32_t>(width) && images[0].height == static_cast<uint32_t>(height) && images[0].channel == 3) {
                        state = 3;
                        std::lock_guard<std::mutex> guard(output_mutex);
                        output.assign(images[0].data, images[0].data + width * height * 3);
                        final_state = 4;
                    }
                }
            } catch (...) { final_state = 5; }
            if (images) free_sd_images(images, count);
            { std::lock_guard<std::mutex> guard(context_mutex); active_context = nullptr; }
            if (context) free_sd_ctx(context);
            state = cancelled ? 6 : final_state;
        }).detach();
    } catch (...) { state = 5; return false; }
    return true;
#endif
}
