#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <fstream>
#include <string>

int main(int argc, char** argv) {
    if (argc < 2) {
        std::fprintf(stderr, "Usage: inpaint-probe /absolute/path/libstable-diffusion.so [companion libraries...]\n");
        return 2;
    }
    for (int index = 2; index < argc; ++index) {
        if (!dlopen(argv[index], RTLD_NOW | RTLD_LOCAL)) {
            std::fprintf(stderr, "COMPANION_LOAD_FAILED: %s\n", dlerror());
            return 13;
        }
        std::printf("COMPANION_LOAD_PASS: %s\n", argv[index]);
    }
    void* library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!library) {
        std::fprintf(stderr, "ENGINE_LOAD_FAILED: %s\n", dlerror());
        return 10;
    }
    for (const char* symbol : {"new_sd_ctx", "generate_image", "sd_cancel_generation", "free_sd_ctx", "sd_get_system_info"}) {
        if (!dlsym(library, symbol)) {
            std::fprintf(stderr, "MISSING_SYMBOL: %s\n", symbol);
            dlclose(library);
            return 11;
        }
    }
    auto system_info = reinterpret_cast<const char* (*)()>(dlsym(library, "sd_get_system_info"));
    std::printf("ENGINE_LOAD_PASS\n%s\n", system_info());
    auto commit = reinterpret_cast<const char* (*)()>(dlsym(library, "sd_commit"));
    if (commit) std::printf("ENGINE_COMMIT=%s\n", commit());
    std::ifstream memory("/proc/meminfo");
    std::string line;
    unsigned long available_kib = 0;
    while (std::getline(memory, line)) {
        if (line.rfind("MemAvailable:", 0) == 0) std::sscanf(line.c_str(), "MemAvailable: %lu", &available_kib);
    }
    std::printf("MEM_AVAILABLE_KIB=%lu\nSAFETY_RESERVE_KIB=3145728\nINFERENCE_NOT_RUN\n", available_kib);
    dlclose(library);
    if (!available_kib) return 12;
    if (available_kib < 3145728) {
        std::printf("BLOCKED_BY_APP_SAFETY_RESERVE_NOT_HARDWARE_INCOMPATIBILITY\n");
        return 41;
    }
    std::printf("MEMORY_PREFLIGHT_PASS_NOT_GENERATION_PROOF\n");
    return 0;
}
