#include "android_perf_hint.h"

#include <dlfcn.h>
#include <pthread.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

typedef struct APerformanceHintManager APerformanceHintManager;
typedef struct APerformanceHintSession APerformanceHintSession;

typedef APerformanceHintManager *(*get_manager_fn)(void);
typedef APerformanceHintSession *(*create_session_fn)(
    APerformanceHintManager *, const int32_t *, size_t, int64_t);
typedef int (*report_duration_fn)(APerformanceHintSession *, int64_t);
typedef void (*close_session_fn)(APerformanceHintSession *);

typedef struct {
    void *library;
    get_manager_fn get_manager;
    create_session_fn create_session;
    report_duration_fn report_duration;
    close_session_fn close_session;
} android_perf_hint_api_t;

struct android_perf_hint {
    APerformanceHintSession *session;
};

static pthread_once_t api_once = PTHREAD_ONCE_INIT;
static android_perf_hint_api_t api;

static bool load_symbol(void *library, const char *name,
                        void *target, size_t target_size)
{
    void *symbol;

    if (target_size != sizeof symbol) return false;
    symbol = dlsym(library, name);
    if (!symbol) return false;
    memcpy(target, &symbol, sizeof symbol);
    return true;
}

/* Keep API 26 loadable by resolving the API 31 NDK entry points at runtime. */
static void load_api(void)
{
    void *library = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) return;

    if (!load_symbol(library, "APerformanceHint_getManager",
                     &api.get_manager, sizeof api.get_manager) ||
        !load_symbol(library, "APerformanceHint_createSession",
                     &api.create_session, sizeof api.create_session) ||
        !load_symbol(library, "APerformanceHint_reportActualWorkDuration",
                     &api.report_duration, sizeof api.report_duration) ||
        !load_symbol(library, "APerformanceHint_closeSession",
                     &api.close_session, sizeof api.close_session)) {
        dlclose(library);
        memset(&api, 0, sizeof api);
        return;
    }

    api.library = library;
}

android_perf_hint_t *android_perf_hint_start(int64_t target_work_duration_ns)
{
    APerformanceHintManager *manager;
    APerformanceHintSession *session;
    android_perf_hint_t *hint;
    int32_t thread_id;

    if (target_work_duration_ns <= 0 || pthread_once(&api_once, load_api) != 0 ||
        !api.get_manager || !api.create_session || !api.close_session)
        return NULL;

    manager = api.get_manager();
    if (!manager) return NULL;

    thread_id = (int32_t)gettid();
    if (thread_id <= 0) return NULL;

    session = api.create_session(manager, &thread_id, 1u,
                                 target_work_duration_ns);
    if (!session) return NULL;

    hint = malloc(sizeof *hint);
    if (!hint) {
        api.close_session(session);
        return NULL;
    }

    hint->session = session;
    return hint;
}

bool android_perf_hint_report(android_perf_hint_t *hint,
                              int64_t actual_work_duration_ns)
{
    if (!hint || !hint->session || actual_work_duration_ns <= 0 ||
        !api.report_duration)
        return false;

    return api.report_duration(hint->session, actual_work_duration_ns) == 0;
}

void android_perf_hint_stop(android_perf_hint_t *hint)
{
    if (!hint) return;
    if (hint->session && api.close_session)
        api.close_session(hint->session);
    free(hint);
}
