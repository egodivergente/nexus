cmake_minimum_required(VERSION 3.22)
set(ANDROID_STL c++_shared CACHE STRING "" FORCE)
project(nexus_whisper LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_JOB_POOLS compile_pool=1)
set(CMAKE_JOB_POOL_COMPILE compile_pool)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
include(FetchContent)
FetchContent_Declare(whisper
    URL https://codeload.github.com/ggml-org/whisper.cpp/tar.gz/refs/tags/v1.7.6
    URL_HASH SHA256=166140e9a6d8a36f787a2bd77f8f44dd64874f12dd8359ff7c1f4f9acb86202e)
FetchContent_MakeAvailable(whisper)
add_library(nexus_whisper SHARED nexus_whisper.cpp)
target_compile_options(nexus_whisper PRIVATE -O2)
target_link_libraries(nexus_whisper PRIVATE whisper android log)

add_library(nexus_inpaint SHARED nexus_inpaint.cpp)
target_compile_options(nexus_inpaint PRIVATE -O2)
find_library(NEXUS_CPP_SHARED_LIBRARY c++_shared REQUIRED)
add_library(nexus_cpp_shared SHARED IMPORTED)
set_target_properties(nexus_cpp_shared PROPERTIES IMPORTED_LOCATION "${NEXUS_CPP_SHARED_LIBRARY}")
target_link_libraries(nexus_inpaint PRIVATE nexus_cpp_shared)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    include(ExternalProject)
    FetchContent_Declare(nexus_sd
        URL https://codeload.github.com/leejet/stable-diffusion.cpp/tar.gz/95ff76230889898e1bf40058ba272a3699b50dc6
        URL_HASH SHA256=84b455a57d257ead942152d916ffe7235c5fff24a8c0a432e583b93c161535bd)
    FetchContent_Declare(nexus_sd_ggml
        URL https://codeload.github.com/leejet/ggml/tar.gz/a5915eb0e47adef21c3f25b889a008925ea838c2
        URL_HASH SHA256=886e4106862d0789c3e44a251d314b6d118dde31c1ee89e4a67fb9cea02597ac)
    FetchContent_Populate(nexus_sd)
    FetchContent_Populate(nexus_sd_ggml)
    set(NEXUS_SD_LIBRARY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libstable-diffusion.so")
    ExternalProject_Add(nexus_sd_build
        SOURCE_DIR "${nexus_sd_SOURCE_DIR}"
        BINARY_DIR "${CMAKE_CURRENT_BINARY_DIR}/sd-build"
        CMAKE_ARGS
            -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
            -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
            -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26
            -DANDROID_STL=c++_shared -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON
            -DCMAKE_BUILD_TYPE=Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON
            -DCMAKE_SHARED_LINKER_FLAGS=-Wl,-Bsymbolic,--exclude-libs,ALL
            -DSDCPP_BUILD_COMMIT=95ff76230889898e1bf40058ba272a3699b50dc6
            -DSDCPP_BUILD_VERSION=pinned -DGIT_EXE=OFF
            -DSD_BUILD_SHARED_LIBS=ON -DSD_BUILD_EXAMPLES=OFF -DSD_WEBP=OFF -DSD_WEBM=OFF
            -DSD_GGML_SOURCE_DIR=${nexus_sd_ggml_SOURCE_DIR}
            -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_CPU_KLEIDIAI=OFF
            -DGGML_BACKEND_DL=OFF
        BUILD_COMMAND ${CMAKE_COMMAND} --build <BINARY_DIR> --target stable-diffusion --parallel 1
        INSTALL_COMMAND ${CMAKE_COMMAND} -E copy <BINARY_DIR>/bin/libstable-diffusion.so "${NEXUS_SD_LIBRARY}"
        BUILD_BYPRODUCTS "${NEXUS_SD_LIBRARY}")
    add_library(nexus_sd_runtime SHARED IMPORTED)
    set_target_properties(nexus_sd_runtime PROPERTIES IMPORTED_LOCATION "${NEXUS_SD_LIBRARY}")
    add_dependencies(nexus_sd_runtime nexus_sd_build)
    target_include_directories(nexus_inpaint PRIVATE "${nexus_sd_SOURCE_DIR}/include")
    target_link_libraries(nexus_inpaint PRIVATE nexus_sd_runtime)
endif()
