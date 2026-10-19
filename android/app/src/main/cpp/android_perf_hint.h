#ifndef S5LAN_ANDROID_PERF_HINT_H
#define S5LAN_ANDROID_PERF_HINT_H

#include <stdbool.h>
#include <stdint.h>

typedef struct android_perf_hint android_perf_hint_t;

android_perf_hint_t *android_perf_hint_start(int64_t target_work_duration_ns);
bool android_perf_hint_report(android_perf_hint_t *hint,
                              int64_t actual_work_duration_ns);
void android_perf_hint_stop(android_perf_hint_t *hint);

#endif
