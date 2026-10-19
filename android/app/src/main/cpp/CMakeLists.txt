cmake_minimum_required(VERSION 3.22.1)
project(s5landbox C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(S5LBOX_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../..")
get_filename_component(S5LBOX_ROOT "${S5LBOX_ROOT}" ABSOLUTE)

# The portable emulator core.  Android uses the interpreter; S5LBOX_JIT and the
# static AArch64 engine stay off exactly as they are on iOS.
add_subdirectory("${S5LBOX_ROOT}/core" core-build)

# Everything in app/Sources is portable C11.  VMFirmwareHLE.c is already a
# member of host_vm_firmware_hle, so it is excluded from the glob to avoid
# duplicate symbols.
file(GLOB S5LAND_APP_SOURCES CONFIGURE_DEPENDS "${S5LBOX_ROOT}/app/Sources/*.c")
list(FILTER S5LAND_APP_SOURCES EXCLUDE REGEX "/VMFirmwareHLE\\.c$")

add_library(s5landbox SHARED
    engine.c
    engine_ipa.c
    jni_bridge.c
    ${S5LAND_APP_SOURCES})

target_include_directories(s5landbox PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
    "${S5LBOX_ROOT}/core/include"
    "${S5LBOX_ROOT}/app/Sources"
    "${S5LBOX_ROOT}/tools")

target_compile_definitions(s5landbox PRIVATE
    _FILE_OFFSET_BITS=64
    S5LBOX_ANDROID=1
    S5LBOX_IOS3_HLE_EXPERIMENT=1
    # The same real-time pacing the iOS app asks of the core.
    S5LBOX_IOS_WFI_REALTIME_PACING=1
    S5LBOX_IOS_ACTIVE_REALTIME_CLOCK=1)

target_compile_options(s5landbox PRIVATE
    -O3
    -fomit-frame-pointer
    -Wall
    -Wextra
    -Wno-unused-parameter
    -Wno-missing-field-initializers)

target_compile_options(emucore PRIVATE
    -O3
    -fomit-frame-pointer)

target_link_libraries(s5landbox PRIVATE
    emucore
    nethost
    host_file_block
    host_rootfs_work
    host_ios3_bringup_gate
    host_ios3_kernel_patch
    host_ios3_hle
    host_vm_firmware_hle
    host_guest_patch
    host_payload_tar
    host_sha256
    android
    log
    m)

# Android 15+ devices can use 16 KiB pages; AGP also requires the flag.
target_link_options(s5landbox PRIVATE "-Wl,-z,max-page-size=16384")
