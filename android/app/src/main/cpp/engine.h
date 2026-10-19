/*
 * S5LAndBox — the Android engine facade.
 *
 * This is the Android counterpart of app/Sources/VMEngine.m: one thread owns
 * the s5l8900 machine for its whole life, and every other thread publishes
 * input or reads a copied frame under a mutex.  None of the emulator core is
 * modified.  All platform-independent pieces of the iOS app (firmware import,
 * instances, snapshots, touch/button queues, audio ring) are compiled straight
 * into this library and reused unchanged.
 */
#ifndef S5LAND_ENGINE_H
#define S5LAND_ENGINE_H

#include <android/native_window.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct engine engine_t;

typedef enum {
    ENGINE_STOP_PLAIN = 0,     /* stop, keep whatever resume pair exists      */
    ENGINE_STOP_CHECKPOINT = 1,/* save the automatic resume pair, then stop   */
    ENGINE_STOP_POWER_OFF = 2  /* stop and disarm automatic resume            */
} engine_stop_mode_t;

engine_t *engine_create(const char *root_directory);
void engine_destroy(engine_t *engine);

/* ---------------------------------------------------------------- firmware */
/*
 * Import an IPSW from an already-open descriptor.  The descriptor remains the
 * caller's: this reads through pread and never closes it.  Runs synchronously
 * on the calling thread; the caller must use a background thread.
 */
int engine_import_ipsw(engine_t *engine, int fd, uint64_t size,
                       bool skip_stock_checksums,
                       char *detail, size_t detail_capacity);
void engine_import_cancel(engine_t *engine);
/* Writes: active, artefact, stage, done, total. */
void engine_import_progress(engine_t *engine, int *active, int *artefact,
                            int *stage, uint64_t *done, uint64_t *total);
/* {"ok":bool,"detail":...,"artefacts":[{"name",...,"state",...,"detail"}]} */
int engine_firmware_json(engine_t *engine, char *out, size_t capacity);

/* --------------------------------------------------------------- machines */
int engine_machines_json(engine_t *engine, char *out, size_t capacity);
int engine_machine_create(engine_t *engine, const char *name,
                          const bool *options, unsigned option_count,
                          char *new_id, size_t id_capacity,
                          char *detail, size_t detail_capacity);
int engine_machine_rename(engine_t *engine, const char *id, const char *name,
                          char *detail, size_t detail_capacity);
int engine_machine_delete(engine_t *engine, const char *id,
                          char *detail, size_t detail_capacity);
int engine_options_json(engine_t *engine, char *out, size_t capacity);
int engine_machine_options_json(engine_t *engine, const char *id,
                                char *out, size_t capacity);
int engine_machine_set_options(engine_t *engine, const char *id,
                               const bool *options, unsigned option_count,
                               char *detail, size_t detail_capacity);

/* -------------------------------------------------------------------- run */
/* Returns 0 when the start was scheduled; detail says why otherwise. */
int engine_start(engine_t *engine, const char *instance_id,
                 char *detail, size_t detail_capacity);
void engine_stop(engine_t *engine, engine_stop_mode_t mode);
void engine_set_paused(engine_t *engine, bool paused);
void engine_set_instruction_cap(engine_t *engine, uint64_t cap);
/* {"phase":...,"running":...,...}; safe to call from any thread. */
int engine_status_json(engine_t *engine, char *out, size_t capacity);
/* Returns bytes written, NUL-terminated; drains the pending UART text. */
size_t engine_console_drain(engine_t *engine, char *out, size_t capacity);

/* ---------------------------------------------------------------- display */
void engine_set_surface(engine_t *engine, ANativeWindow *window);

/* ------------------------------------------------------------------ input */
/* `x`/`y` are pixels in a view of `view_w` x `view_h`; the guest panel is
 * 320x480 and the picture is letterboxed exactly as vm_touch_map() assumes. */
bool engine_touch(engine_t *engine, double x, double y,
                  double view_w, double view_h, int phase);
bool engine_button(engine_t *engine, unsigned app_button, bool pressed);
bool engine_button_pressed(engine_t *engine, unsigned app_button);

/* -------------------------------------------------------------- snapshots */
int engine_snapshots_json(engine_t *engine, const char *id,
                          char *out, size_t capacity);
int engine_snapshot_take(engine_t *engine, const char *id,
                         char *snapshot_id, size_t id_capacity,
                         char *detail, size_t detail_capacity);
int engine_snapshot_restore(engine_t *engine, const char *id,
                            const char *snapshot_id,
                            char *detail, size_t detail_capacity);
int engine_snapshot_delete(engine_t *engine, const char *id,
                           const char *snapshot_id,
                           char *detail, size_t detail_capacity);
bool engine_snapshot_restore_needs_unlock(engine_t *engine, const char *id,
                                          const char *snapshot_id);

/* ------------------------------------------------------------------ audio */
int engine_render_audio(engine_t *engine, float *interleaved, unsigned frames);
void engine_set_microphone(engine_t *engine, bool enabled);

/* -------------------------------------------------------------------- USB */
bool engine_usb_ready(engine_t *engine);
/* Returns a connected local socket for one guest USB service, or -1. */
int engine_usb_open(engine_t *engine, const char *service,
                    char *detail, size_t detail_capacity);

#ifdef __cplusplus
}
#endif

#endif /* S5LAND_ENGINE_H */
