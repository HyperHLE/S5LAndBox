/*
 * S5LAndBox — JNI surface for the Kotlin NativeEngine.
 *
 * Thin by design: every method copies its arguments into the portable types the
 * C engine already uses and copies the answer back out.  Nothing here owns a
 * machine, a thread or a file; the handle is an engine_t * handed to Kotlin as
 * a jlong and never dereferenced by anything else.
 */
#include <android/native_window_jni.h>
#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "engine.h"

#define JNI_PACKAGE "com_tayga16_s5landbox_nativebridge"
#define JNI_CLASS "NativeEngine"

#define RESULT_CAP 65536
#define OPTION_CAP 32

static engine_t *handle_of(jlong handle)
{
    return (engine_t *)(uintptr_t)handle;
}

static void json_escape(const char *text, char *out, size_t capacity)
{
    size_t used = 0;
    if (!out || capacity == 0) return;
    if (!text) text = "";
    for (const unsigned char *p = (const unsigned char *)text; *p; p++) {
        const char *piece = NULL;
        char unicode[8];
        switch (*p) {
        case '"':  piece = "\\\""; break;
        case '\\': piece = "\\\\"; break;
        case '\n': piece = "\\n"; break;
        case '\r': piece = "\\r"; break;
        case '\t': piece = "\\t"; break;
        default:
            if (*p < 0x20) {
                snprintf(unicode, sizeof unicode, "\\u%04x", *p);
                piece = unicode;
            }
            break;
        }
        if (piece) {
            size_t n = strlen(piece);
            if (used + n + 1u >= capacity) break;
            memcpy(out + used, piece, n);
            used += n;
        } else {
            if (used + 2u >= capacity) break;
            out[used++] = (char)*p;
        }
    }
    out[used] = '\0';
}

static void result_json(char *out, size_t capacity, bool ok,
                        const char *id, const char *detail)
{
    char escaped[2048];
    if (!out || capacity == 0) return;
    json_escape(detail, escaped, sizeof escaped);
    if (id && id[0]) {
        char escaped_id[128];
        json_escape(id, escaped_id, sizeof escaped_id);
        snprintf(out, capacity,
                 "{\"ok\":%s,\"id\":\"%s\",\"detail\":\"%s\"}",
                 ok ? "true" : "false", escaped_id, escaped);
    } else {
        snprintf(out, capacity, "{\"ok\":%s,\"detail\":\"%s\"}",
                 ok ? "true" : "false", escaped);
    }
}

static char *string_from_jni(JNIEnv *env, jstring text)
{
    const char *chars;
    char *copy;
    if (!text) return NULL;
    chars = (*env)->GetStringUTFChars(env, text, NULL);
    if (!chars) return NULL;
    copy = strdup(chars);
    (*env)->ReleaseStringUTFChars(env, text, chars);
    return copy;
}

static jstring new_string(JNIEnv *env, const char *text)
{
    return (*env)->NewStringUTF(env, text ? text : "");
}

/* Run a JSON-returning engine call into a fixed buffer and hand it to Java. */
static jstring engine_json_string(JNIEnv *env, jlong handle,
                                  int (*fn)(engine_t *, char *, size_t),
                                  size_t capacity)
{
    engine_t *e = handle_of(handle);
    char *buffer;
    jstring result;
    if (!e) return new_string(env, "");
    buffer = malloc(capacity);
    if (!buffer) return new_string(env, "{\"ok\":false}");
    buffer[0] = '\0';
    (void)fn(e, buffer, capacity);
    result = new_string(env, buffer);
    free(buffer);
    return result;
}

static jstring engine_id_json_string(JNIEnv *env, jlong handle, jstring id,
                                     int (*fn)(engine_t *, const char *,
                                               char *, size_t),
                                     size_t capacity)
{
    engine_t *e = handle_of(handle);
    char *wanted;
    char *buffer;
    jstring result;
    if (!e || !id) return new_string(env, "");
    wanted = string_from_jni(env, id);
    if (!wanted) return new_string(env, "");
    buffer = malloc(capacity);
    if (!buffer) {
        free(wanted);
        return new_string(env, "{\"ok\":false}");
    }
    buffer[0] = '\0';
    (void)fn(e, wanted, buffer, capacity);
    result = new_string(env, buffer);
    free(buffer);
    free(wanted);
    return result;
}

static void read_options(JNIEnv *env, jbooleanArray array,
                         bool *out, unsigned *count)
{
    *count = 0;
    memset(out, 0, OPTION_CAP * sizeof(bool));
    if (!array) return;
    jsize length = (*env)->GetArrayLength(env, array);
    jboolean *values = (*env)->GetBooleanArrayElements(env, array, NULL);
    if (!values) return;
    if (length > (jsize)OPTION_CAP) length = (jsize)OPTION_CAP;
    for (jsize i = 0; i < length; i++) out[i] = values[i] != JNI_FALSE;
    *count = (unsigned)length;
    (*env)->ReleaseBooleanArrayElements(env, array, values, JNI_ABORT);
}

JNIEXPORT jlong JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeCreate(
    JNIEnv *env, jclass clazz, jstring root)
{
    char *path;
    engine_t *e;
    (void)clazz;
    if (!root) return 0;
    path = string_from_jni(env, root);
    if (!path) return 0;
    e = engine_create(path);
    free(path);
    return (jlong)(uintptr_t)e;
}

JNIEXPORT void JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeDestroy(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    engine_destroy(handle_of(handle));
}

/* --------------------------------------------------------------- packages --- */

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativePackagesJson(
    JNIEnv *env, jclass clazz, jlong handle, jstring id)
{
    (void)clazz;
    return engine_id_json_string(env, handle, id, engine_packages_json, 49152);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeInstallPackages(
    JNIEnv *env, jclass clazz, jlong handle, jstring id,
    jstring package_directory, jboolean include_appsync)
{
    engine_t *e = handle_of(handle);
    char *machine = NULL;
    char *directory = NULL;
    char detail[512];
    char out[4096];
    int rc;
    (void)clazz;

    out[0] = '\0';
    if (!e) return new_string(env, "{\"ok\":false,\"detail\":\"No engine.\"}");
    machine = string_from_jni(env, id);
    directory = string_from_jni(env, package_directory);
    detail[0] = '\0';
    rc = engine_install_packages(e, machine, directory,
                                 include_appsync == JNI_TRUE,
                                 out, sizeof out, detail, sizeof detail);
    free(machine);
    free(directory);
    if (!out[0]) result_json(out, sizeof out, rc == 0, "", detail);
    return new_string(env, out);
}

JNIEXPORT jlongArray JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeInstallProgress(
    JNIEnv *env, jclass clazz, jlong handle)
{
    engine_t *e = handle_of(handle);
    jlong values[4] = {0, 0, 0, 0};
    jlongArray array;
    int active = 0, phase = 0;
    uint64_t done = 0, total = 0;
    (void)clazz;
    if (e) {
        engine_install_progress(e, &active, &phase, &done, &total);
        values[0] = active;
        values[1] = phase;
        values[2] = (jlong)done;
        values[3] = (jlong)total;
    }
    array = (*env)->NewLongArray(env, 4);
    if (!array) return NULL;
    (*env)->SetLongArrayRegion(env, array, 0, 4, values);
    return array;
}

/* --------------------------------------------------------------- firmware --- */

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeImportIpsw(
    JNIEnv *env, jclass clazz, jlong handle, jint fd, jlong size,
    jboolean skip)
{
    engine_t *e = handle_of(handle);
    char detail[512];
    int rc;
    char out[2048];
    (void)clazz;
    if (!e) return new_string(env, "{\"ok\":false,\"detail\":\"No engine.\"}");
    detail[0] = '\0';
    rc = engine_import_ipsw(e, (int)fd, (uint64_t)size,
                            skip == JNI_TRUE, detail, sizeof detail);
    result_json(out, sizeof out, rc == 0, "", detail);
    return new_string(env, out);
}

JNIEXPORT void JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeImportCancel(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    engine_import_cancel(handle_of(handle));
}

JNIEXPORT jlongArray JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeImportProgress(
    JNIEnv *env, jclass clazz, jlong handle)
{
    engine_t *e = handle_of(handle);
    jlong values[5] = {0, 0, 0, 0, 0};
    jlongArray array;
    int active = 0, artefact = 0, stage = 0;
    uint64_t done = 0, total = 0;
    (void)clazz;
    if (e) {
        engine_import_progress(e, &active, &artefact, &stage, &done, &total);
        values[0] = active;
        values[1] = artefact;
        values[2] = stage;
        values[3] = (jlong)done;
        values[4] = (jlong)total;
    }
    array = (*env)->NewLongArray(env, 5);
    if (!array) return NULL;
    (*env)->SetLongArrayRegion(env, array, 0, 5, values);
    return array;
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeFirmwareJson(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)clazz;
    return engine_json_string(env, handle, engine_firmware_json, 8192);
}

/* --------------------------------------------------------------- machines --- */

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeMachinesJson(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)clazz;
    return engine_json_string(env, handle, engine_machines_json, 32768);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeMachineCreate(
    JNIEnv *env, jclass clazz, jlong handle, jstring name,
    jbooleanArray options, jint disk_gib)
{
    engine_t *e = handle_of(handle);
    char *wanted;
    bool values[OPTION_CAP];
    unsigned count = 0;
    char id[64];
    char detail[512];
    char out[2048];
    int rc;
    (void)clazz;
    if (!e || !name)
        return new_string(env, "{\"ok\":false,\"detail\":\"No engine.\"}");
    wanted = string_from_jni(env, name);
    if (!wanted) return new_string(env, "{\"ok\":false}");
    read_options(env, options, values, &count);
    id[0] = '\0';
    detail[0] = '\0';
    rc = engine_machine_create(e, wanted, values, count, (unsigned)disk_gib,
                               id, sizeof id, detail, sizeof detail);
    free(wanted);
    result_json(out, sizeof out, rc == 0, id, detail);
    return new_string(env, out);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeMachineSetDiskSize(
    JNIEnv *env, jclass clazz, jlong handle, jstring id, jint disk_gib)
{
    engine_t *e = handle_of(handle);
    char *wanted_id;
    char detail[512];
    char out[1024];
    int rc;
    (void)clazz;
    if (!e || !id) return new_string(env, "{\"ok\":false}");
    wanted_id = string_from_jni(env, id);
    if (!wanted_id) return new_string(env, "{\"ok\":false}");
    detail[0] = '\0';
    rc = engine_machine_set_disk_size(e, wanted_id, (unsigned)disk_gib, detail, sizeof detail);
    free(wanted_id);
    result_json(out, sizeof out, rc == 0, "", detail);
    return new_string(env, out);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeMachineRename(
    JNIEnv *env, jclass clazz, jlong handle, jstring id, jstring name)
{
    engine_t *e = handle_of(handle);
    char *wanted_id, *wanted_name;
    char detail[512];
    char out[1024];
    int rc;
    (void)clazz;
    if (!e) return new_string(env, "{\"ok\":false}");
    wanted_id = string_from_jni(env, id);
    wanted_name = string_from_jni(env, name);
    if (!wanted_id || !wanted_name) {
        free(wanted_id);
        free(wanted_name);
        return new_string(env, "{\"ok\":false}");
    }
    detail[0] = '\0';
    rc = engine_machine_rename(e, wanted_id, wanted_name, detail,
                               sizeof detail);
    free(wanted_id);
    free(wanted_name);
    result_json(out, sizeof out, rc == 0, "", detail);
    return new_string(env, out);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeMachineDelete(
    JNIEnv *env, jclass clazz, jlong handle, jstring id)
{
    engine_t *e = handle_of(handle);
    char *wanted;
    char detail[512];
    char out[1024];
    int rc;
    (void)clazz;
    if (!e) return new_string(env, "{\"ok\":false}");
    wanted = string_from_jni(env, id);
    if (!wanted) return new_string(env, "{\"ok\":false}");
    detail[0] = '\0';
    rc = engine_machine_delete(e, wanted, detail, sizeof detail);
    free(wanted);
    result_json(out, sizeof out, rc == 0, "", detail);
    return new_string(env, out);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeIpaInspect(
    JNIEnv *env, jclass clazz, jstring ipa_path)
{
    char *path;
    char out[2048];
    (void)clazz;
    if (!ipa_path) return new_string(env, "{\"ok\":false,\"detail\":\"No path.\"}");
    path = string_from_jni(env, ipa_path);
    if (!path) return new_string(env, "{\"ok\":false,\"detail\":\"Invalid path.\"}");
    engine_ipa_inspect_json(path, out, sizeof out);
    free(path);
    return new_string(env, out);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeMachineInstallIpa(
    JNIEnv *env, jclass clazz, jlong handle, jstring id, jstring cache_dir, jstring ipa_path)
{
    engine_t *e = handle_of(handle);
    char *wanted_id = NULL;
    char *wanted_cache = NULL;
    char *wanted_ipa = NULL;
    char detail[512];
    char out[1024];
    int rc;
    (void)clazz;
    if (!e || !id || !ipa_path) return new_string(env, "{\"ok\":false,\"detail\":\"Invalid parameters.\"}");
    wanted_id = string_from_jni(env, id);
    wanted_cache = cache_dir ? string_from_jni(env, cache_dir) : NULL;
    wanted_ipa = string_from_jni(env, ipa_path);
    detail[0] = '\0';
    rc = engine_machine_install_ipa(e, wanted_id ? wanted_id : "",
                                    wanted_cache ? wanted_cache : "",
                                    wanted_ipa ? wanted_ipa : "",
                                    detail, sizeof detail);
    free(wanted_id);
    free(wanted_cache);
    free(wanted_ipa);
    result_json(out, sizeof out, rc == 0, "", detail);
    return new_string(env, out);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeOptionsJson(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)clazz;
    return engine_json_string(env, handle, engine_options_json, 32768);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeMachineOptionsJson(
    JNIEnv *env, jclass clazz, jlong handle, jstring id)
{
    (void)clazz;
    return engine_id_json_string(env, handle, id,
                                 engine_machine_options_json, 4096);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeMachineSetOptions(
    JNIEnv *env, jclass clazz, jlong handle, jstring id,
    jbooleanArray options)
{
    engine_t *e = handle_of(handle);
    char *wanted;
    bool values[OPTION_CAP];
    unsigned count = 0;
    char detail[512];
    char out[1024];
    int rc;
    (void)clazz;
    if (!e) return new_string(env, "{\"ok\":false}");
    wanted = string_from_jni(env, id);
    if (!wanted) return new_string(env, "{\"ok\":false}");
    read_options(env, options, values, &count);
    detail[0] = '\0';
    rc = engine_machine_set_options(e, wanted, values, count, detail,
                                    sizeof detail);
    free(wanted);
    result_json(out, sizeof out, rc == 0, "", detail);
    return new_string(env, out);
}

/* -------------------------------------------------------------------- run --- */

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeStart(
    JNIEnv *env, jclass clazz, jlong handle, jstring id)
{
    engine_t *e = handle_of(handle);
    char *wanted = NULL;
    char detail[512];
    char out[1024];
    int rc;
    (void)clazz;
    if (!e) return new_string(env, "{\"ok\":false,\"detail\":\"No engine.\"}");
    if (id) wanted = string_from_jni(env, id);
    detail[0] = '\0';
    rc = engine_start(e, wanted, detail, sizeof detail);
    free(wanted);
    result_json(out, sizeof out, rc == 0, "", detail);
    return new_string(env, out);
}

JNIEXPORT void JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeStop(
    JNIEnv *env, jclass clazz, jlong handle, jint mode)
{
    (void)env;
    (void)clazz;
    engine_stop(handle_of(handle), (engine_stop_mode_t)mode);
}

JNIEXPORT void JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeSetPaused(
    JNIEnv *env, jclass clazz, jlong handle, jboolean paused)
{
    (void)env;
    (void)clazz;
    engine_set_paused(handle_of(handle), paused == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeSetInstructionCap(
    JNIEnv *env, jclass clazz, jlong handle, jlong cap)
{
    (void)env;
    (void)clazz;
    engine_set_instruction_cap(handle_of(handle), (uint64_t)cap);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeStatusJson(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)clazz;
    return engine_json_string(env, handle, engine_status_json, 8192);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeConsoleDrain(
    JNIEnv *env, jclass clazz, jlong handle)
{
    engine_t *e = handle_of(handle);
    char buffer[4096];
    size_t got;
    (void)clazz;
    if (!e) return new_string(env, "");
    got = engine_console_drain(e, buffer, sizeof buffer);
    (void)got;
    return new_string(env, buffer);
}

JNIEXPORT jint JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeGetWebLogPort(
    JNIEnv *env, jclass clazz, jlong handle)
{
    engine_t *e = handle_of(handle);
    (void)env;
    (void)clazz;
    if (!e) return 0;
    return (jint)engine_get_web_log_port(e);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeGetLogsJson(
    JNIEnv *env, jclass clazz, jlong handle, jlong lastId)
{
    engine_t *e = handle_of(handle);
    char buffer[65536];
    size_t len;
    (void)clazz;
    if (!e) return new_string(env, "[]");
    len = engine_log_entries_json(e, (uint32_t)lastId, buffer, sizeof buffer);
    if (len == 0) return new_string(env, "[]");
    return new_string(env, buffer);
}

JNIEXPORT void JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeSetSurface(
    JNIEnv *env, jclass clazz, jlong handle, jobject surface)
{
    engine_t *e = handle_of(handle);
    ANativeWindow *window = NULL;
    (void)clazz;
    if (!e) return;
    if (surface) window = ANativeWindow_fromSurface(env, surface);
    /* Ownership of the reference passes to the engine. */
    engine_set_surface(e, window);
}

/* ------------------------------------------------------------------ input --- */

JNIEXPORT jboolean JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeTouch(
    JNIEnv *env, jclass clazz, jlong handle, jdouble x, jdouble y,
    jdouble view_w, jdouble view_h, jint phase)
{
    (void)env;
    (void)clazz;
    return engine_touch(handle_of(handle), x, y, view_w, view_h, phase)
               ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeButton(
    JNIEnv *env, jclass clazz, jlong handle, jint button, jboolean pressed)
{
    (void)env;
    (void)clazz;
    if (button < 0) return JNI_FALSE;
    return engine_button(handle_of(handle), (unsigned)button,
                         pressed == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeButtonPressed(
    JNIEnv *env, jclass clazz, jlong handle, jint button)
{
    (void)env;
    (void)clazz;
    if (button < 0) return JNI_FALSE;
    return engine_button_pressed(handle_of(handle), (unsigned)button)
               ? JNI_TRUE : JNI_FALSE;
}

/* -------------------------------------------------------------- snapshots --- */

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeSnapshotsJson(
    JNIEnv *env, jclass clazz, jlong handle, jstring id)
{
    (void)clazz;
    return engine_id_json_string(env, handle, id, engine_snapshots_json,
                                 32768);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeSnapshotTake(
    JNIEnv *env, jclass clazz, jlong handle, jstring id)
{
    engine_t *e = handle_of(handle);
    char *wanted;
    char snapshot_id[64];
    char detail[512];
    char out[1024];
    int rc;
    (void)clazz;
    if (!e) return new_string(env, "{\"ok\":false}");
    wanted = string_from_jni(env, id);
    if (!wanted) return new_string(env, "{\"ok\":false}");
    snapshot_id[0] = '\0';
    detail[0] = '\0';
    rc = engine_snapshot_take(e, wanted, snapshot_id, sizeof snapshot_id,
                              detail, sizeof detail);
    free(wanted);
    result_json(out, sizeof out, rc == 0, snapshot_id, detail);
    return new_string(env, out);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeSnapshotRestore(
    JNIEnv *env, jclass clazz, jlong handle, jstring id, jstring snapshot_id)
{
    engine_t *e = handle_of(handle);
    char *wanted_id, *wanted_snapshot;
    char detail[512];
    char out[1024];
    int rc;
    (void)clazz;
    if (!e) return new_string(env, "{\"ok\":false}");
    wanted_id = string_from_jni(env, id);
    wanted_snapshot = string_from_jni(env, snapshot_id);
    if (!wanted_id || !wanted_snapshot) {
        free(wanted_id);
        free(wanted_snapshot);
        return new_string(env, "{\"ok\":false}");
    }
    detail[0] = '\0';
    rc = engine_snapshot_restore(e, wanted_id, wanted_snapshot, detail,
                                 sizeof detail);
    free(wanted_id);
    free(wanted_snapshot);
    result_json(out, sizeof out, rc == 0, "", detail);
    return new_string(env, out);
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeSnapshotDelete(
    JNIEnv *env, jclass clazz, jlong handle, jstring id, jstring snapshot_id)
{
    engine_t *e = handle_of(handle);
    char *wanted_id, *wanted_snapshot;
    char detail[512];
    char out[1024];
    int rc;
    (void)clazz;
    if (!e) return new_string(env, "{\"ok\":false}");
    wanted_id = string_from_jni(env, id);
    wanted_snapshot = string_from_jni(env, snapshot_id);
    if (!wanted_id || !wanted_snapshot) {
        free(wanted_id);
        free(wanted_snapshot);
        return new_string(env, "{\"ok\":false}");
    }
    detail[0] = '\0';
    rc = engine_snapshot_delete(e, wanted_id, wanted_snapshot, detail,
                                sizeof detail);
    free(wanted_id);
    free(wanted_snapshot);
    result_json(out, sizeof out, rc == 0, "", detail);
    return new_string(env, out);
}

JNIEXPORT jboolean JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeSnapshotNeedsUnlock(
    JNIEnv *env, jclass clazz, jlong handle, jstring id, jstring snapshot_id)
{
    engine_t *e = handle_of(handle);
    char *wanted_id, *wanted_snapshot;
    bool needs = false;
    (void)clazz;
    if (!e) return JNI_FALSE;
    wanted_id = string_from_jni(env, id);
    wanted_snapshot = string_from_jni(env, snapshot_id);
    if (wanted_id && wanted_snapshot)
        needs = engine_snapshot_restore_needs_unlock(e, wanted_id,
                                                     wanted_snapshot);
    free(wanted_id);
    free(wanted_snapshot);
    return needs ? JNI_TRUE : JNI_FALSE;
}

/* ------------------------------------------------------------------ audio --- */

JNIEXPORT jint JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeRenderAudio(
    JNIEnv *env, jclass clazz, jlong handle, jfloatArray buffer, jint frames)
{
    engine_t *e = handle_of(handle);
    jfloat *samples;
    jsize capacity;
    int rendered;
    (void)clazz;
    if (!e || !buffer || frames <= 0) return 0;
    capacity = (*env)->GetArrayLength(env, buffer);
    if ((jsize)frames * 2 > capacity) frames = capacity / 2;
    if (frames <= 0) return 0;
    samples = (*env)->GetFloatArrayElements(env, buffer, NULL);
    if (!samples) return 0;
    rendered = engine_render_audio(e, samples, (unsigned)frames);
    (*env)->ReleaseFloatArrayElements(env, buffer, samples, 0);
    return rendered;
}

JNIEXPORT void JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeSetMicrophone(
    JNIEnv *env, jclass clazz, jlong handle, jboolean enabled)
{
    (void)env;
    (void)clazz;
    engine_set_microphone(handle_of(handle), enabled == JNI_TRUE);
}

/* -------------------------------------------------------------------- USB --- */

JNIEXPORT jboolean JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeUsbReady(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    return engine_usb_ready(handle_of(handle)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_tayga16_s5landbox_nativebridge_NativeEngine_nativeUsbOpen(
    JNIEnv *env, jclass clazz, jlong handle, jstring service)
{
    engine_t *e = handle_of(handle);
    char *wanted;
    char detail[512];
    char out[1024];
    int fd;
    (void)clazz;
    if (!e) return new_string(env, "{\"ok\":false,\"fd\":-1}");
    wanted = string_from_jni(env, service);
    if (!wanted) return new_string(env, "{\"ok\":false,\"fd\":-1}");
    detail[0] = '\0';
    fd = engine_usb_open(e, wanted, detail, sizeof detail);
    free(wanted);
    result_json(out, sizeof out, fd >= 0, "", detail);
    /* Insert the descriptor so the caller never has to parse it out of text. */
    if (fd >= 0) {
        size_t used = strlen(out);
        if (used + 32u < sizeof out)
            snprintf(out + used - 1u, sizeof out - used,
                     ",\"fd\":%d}", fd);
    }
    return new_string(env, out);
}
