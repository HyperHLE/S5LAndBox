#include "web_log_server.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdarg.h>
#include <time.h>
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <errno.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define RING_BUFFER_CAP  1024u
#define MAX_CLIENTS      8u
#define MAX_LINE_LEN     512u

typedef struct {
    uint32_t id;
    char time_str[16];
    char category[16];
    char text[MAX_LINE_LEN];
} log_entry_t;

typedef struct {
    int fd;
    bool is_sse;
} client_t;

static const char HTML_PAGE[] =
"<!DOCTYPE html>\n"
"<html lang=\"en\">\n"
"<head>\n"
"<meta charset=\"UTF-8\">\n"
"<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
"<title>S5LAndBox &bull; Live Inspector</title>\n"
"<style>\n"
"* { box-sizing: border-box; margin: 0; padding: 0; }\n"
"body {\n"
"  background-color: #0d1117;\n"
"  color: #c9d1d9;\n"
"  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n"
"  height: 100vh;\n"
"  display: flex;\n"
"  flex-direction: column;\n"
"}\n"
"header {\n"
"  background: #161b22;\n"
"  border-bottom: 1px solid #30363d;\n"
"  padding: 10px 16px;\n"
"  display: flex;\n"
"  align-items: center;\n"
"  justify-content: space-between;\n"
"  gap: 12px;\n"
"  flex-wrap: wrap;\n"
"}\n"
".brand {\n"
"  display: flex;\n"
"  align-items: center;\n"
"  gap: 10px;\n"
"  font-weight: 700;\n"
"  font-size: 1.05rem;\n"
"  color: #f0f6fc;\n"
"}\n"
".badge {\n"
"  font-size: 0.72rem;\n"
"  padding: 3px 8px;\n"
"  border-radius: 12px;\n"
"  font-weight: 600;\n"
"  text-transform: uppercase;\n"
"  letter-spacing: 0.5px;\n"
"}\n"
".badge.live { background: #238636; color: #fff; }\n"
".badge.reconn { background: #d29922; color: #000; }\n"
".stats-bar {\n"
"  display: flex;\n"
"  align-items: center;\n"
"  gap: 16px;\n"
"  font-size: 0.82rem;\n"
"  color: #8b949e;\n"
"}\n"
".stat-item strong { color: #58a6ff; font-weight: 600; }\n"
".toolbar {\n"
"  background: #161b22;\n"
"  border-bottom: 1px solid #30363d;\n"
"  padding: 8px 16px;\n"
"  display: flex;\n"
"  align-items: center;\n"
"  gap: 10px;\n"
"  flex-wrap: wrap;\n"
"}\n"
".filter-group {\n"
"  display: flex;\n"
"  gap: 4px;\n"
"  background: #0d1117;\n"
"  padding: 3px;\n"
"  border-radius: 6px;\n"
"  border: 1px solid #30363d;\n"
"}\n"
".filter-btn {\n"
"  background: transparent;\n"
"  border: none;\n"
"  color: #8b949e;\n"
"  padding: 4px 10px;\n"
"  border-radius: 4px;\n"
"  cursor: pointer;\n"
"  font-size: 0.8rem;\n"
"  font-weight: 500;\n"
"  transition: all 0.15s ease;\n"
"}\n"
".filter-btn:hover { color: #f0f6fc; }\n"
".filter-btn.active { background: #21262d; color: #58a6ff; font-weight: 600; }\n"
".search-box {\n"
"  background: #0d1117;\n"
"  border: 1px solid #30363d;\n"
"  color: #c9d1d9;\n"
"  padding: 5px 12px;\n"
"  border-radius: 6px;\n"
"  font-size: 0.82rem;\n"
"  outline: none;\n"
"  min-width: 180px;\n"
"}\n"
".search-box:focus { border-color: #58a6ff; }\n"
".actions {\n"
"  margin-left: auto;\n"
"  display: flex;\n"
"  align-items: center;\n"
"  gap: 8px;\n"
"}\n"
".btn {\n"
"  background: #21262d;\n"
"  border: 1px solid #30363d;\n"
"  color: #c9d1d9;\n"
"  padding: 5px 12px;\n"
"  border-radius: 6px;\n"
"  font-size: 0.8rem;\n"
"  cursor: pointer;\n"
"  transition: all 0.15s ease;\n"
"}\n"
".btn:hover { background: #30363d; color: #f0f6fc; }\n"
".chk-label {\n"
"  font-size: 0.8rem;\n"
"  color: #8b949e;\n"
"  display: flex;\n"
"  align-items: center;\n"
"  gap: 5px;\n"
"  cursor: pointer;\n"
"}\n"
"#logs {\n"
"  flex: 1;\n"
"  overflow-y: auto;\n"
"  padding: 12px 16px;\n"
"  font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, Liberation Mono, monospace;\n"
"  font-size: 0.82rem;\n"
"  line-height: 1.5;\n"
"}\n"
".log-line {\n"
"  display: flex;\n"
"  gap: 10px;\n"
"  white-space: pre-wrap;\n"
"  word-break: break-all;\n"
"  padding: 2px 4px;\n"
"  border-radius: 3px;\n"
"}\n"
".log-line:hover { background: rgba(255, 255, 255, 0.04); }\n"
".time { color: #484f58; min-width: 90px; user-select: none; }\n"
".tag {\n"
"  font-weight: 600;\n"
"  min-width: 72px;\n"
"  text-align: center;\n"
"  border-radius: 4px;\n"
"  padding: 0 4px;\n"
"  font-size: 0.72rem;\n"
"  height: 20px;\n"
"  line-height: 20px;\n"
"  user-select: none;\n"
"}\n"
".tag-kernel { color: #38bdf8; background: rgba(56, 189, 248, 0.12); }\n"
".tag-app    { color: #34d399; background: rgba(52, 211, 153, 0.12); font-weight: 600; }\n"
".tag-gles   { color: #4ade80; background: rgba(74, 222, 128, 0.12); }\n"
".tag-dyld   { color: #c084fc; background: rgba(192, 132, 252, 0.12); }\n"
".tag-crash  { color: #ef4444; background: rgba(239, 68, 68, 0.22); font-weight: 700; }\n"
".tag-info   { color: #f59e0b; background: rgba(245, 158, 11, 0.12); }\n"
".msg { flex: 1; }\n"
".msg.crash-msg { color: #fca5a5; font-weight: 600; }\n"
"</style>\n"
"</head>\n"
"<body>\n"
"<header>\n"
"  <div class=\"brand\">\n"
"    <span>&#x1F4F1; S5LAndBox Inspector</span>\n"
"    <span id=\"status-badge\" class=\"badge live\">Connecting...</span>\n"
"  </div>\n"
"  <div class=\"stats-bar\">\n"
"    <span class=\"stat-item\">FPS: <strong id=\"stat-fps\">0.0</strong></span>\n"
"    <span class=\"stat-item\">Retired: <strong id=\"stat-retired\">0</strong></span>\n"
"    <span class=\"stat-item\">TTBR0: <strong id=\"stat-space\">0x00000000</strong></span>\n"
"    <span class=\"stat-item\">Events: <strong id=\"stat-count\">0</strong></span>\n"
"  </div>\n"
"</header>\n"
"<div class=\"toolbar\">\n"
"  <div class=\"filter-group\">\n"
"    <button class=\"filter-btn active\" data-cat=\"all\">All</button>\n"
"    <button class=\"filter-btn\" data-cat=\"app\">App</button>\n"
"    <button class=\"filter-btn\" data-cat=\"kernel\">Kernel</button>\n"
"    <button class=\"filter-btn\" data-cat=\"gles\">GLES</button>\n"
"    <button class=\"filter-btn\" data-cat=\"dyld\">dyld</button>\n"
"    <button class=\"filter-btn\" data-cat=\"crash\">Crash</button>\n"
"    <button class=\"filter-btn\" data-cat=\"info\">Info</button>\n"
"  </div>\n"
"  <input type=\"text\" id=\"search\" class=\"search-box\" placeholder=\"Filter text / symbol...\">\n"
"  <div class=\"actions\">\n"
"    <label class=\"chk-label\">\n"
"      <input type=\"checkbox\" id=\"autoscroll\" checked>\n"
"      Auto-scroll\n"
"    </label>\n"
"    <button class=\"btn\" id=\"btn-pause\">Pause</button>\n"
"    <button class=\"btn\" id=\"btn-clear\">Clear</button>\n"
"    <a href=\"/log.txt\" download=\"s5landbox_log.txt\" class=\"btn\" style=\"text-decoration:none;\">Download .txt</a>\n"
"  </div>\n"
"</div>\n"
"<div id=\"logs\"></div>\n"
"<script>\n"
"let activeFilter = 'all';\n"
"let searchQuery = '';\n"
"let isPaused = false;\n"
"let autoScroll = true;\n"
"let totalCount = 0;\n"
"const logsEl = document.getElementById('logs');\n"
"const badgeEl = document.getElementById('status-badge');\n"
"const statCount = document.getElementById('stat-count');\n"
"const statFps = document.getElementById('stat-fps');\n"
"const statRetired = document.getElementById('stat-retired');\n"
"const statSpace = document.getElementById('stat-space');\n"
"const searchInput = document.getElementById('search');\n"
"const btnPause = document.getElementById('btn-pause');\n"
"const btnClear = document.getElementById('btn-clear');\n"
"const chkAuto = document.getElementById('autoscroll');\n"
"\n"
"chkAuto.onchange = (e) => { autoScroll = e.target.checked; };\n"
"btnPause.onclick = () => {\n"
"  isPaused = !isPaused;\n"
"  btnPause.textContent = isPaused ? 'Resume' : 'Pause';\n"
"  btnPause.style.color = isPaused ? '#ef4444' : '#c9d1d9';\n"
"};\n"
"btnClear.onclick = () => { logsEl.innerHTML = ''; };\n"
"\n"
"document.querySelectorAll('.filter-btn').forEach(btn => {\n"
"  btn.onclick = () => {\n"
"    document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));\n"
"    btn.classList.add('active');\n"
"    activeFilter = btn.getAttribute('data-cat');\n"
"    applyFilters();\n"
"  };\n"
"});\n"
"\n"
"searchInput.oninput = (e) => {\n"
"  searchQuery = e.target.value.toLowerCase();\n"
"  applyFilters();\n"
"};\n"
"\n"
"function applyFilters() {\n"
"  const lines = logsEl.children;\n"
"  for (let i = 0; i < lines.length; i++) {\n"
"    const el = lines[i];\n"
"    const cat = el.getAttribute('data-cat');\n"
"    const txt = el.getAttribute('data-txt');\n"
"    const matchCat = (activeFilter === 'all' || cat === activeFilter);\n"
"    const matchSearch = (!searchQuery || txt.includes(searchQuery));\n"
"    el.style.display = (matchCat && matchSearch) ? 'flex' : 'none';\n"
"  }\n"
"}\n"
"\n"
"function appendLog(item) {\n"
"  totalCount++;\n"
"  statCount.textContent = totalCount;\n"
"  if (isPaused) return;\n"
"  const row = document.createElement('div');\n"
"  row.className = 'log-line';\n"
"  row.setAttribute('data-cat', item.cat || 'info');\n"
"  row.setAttribute('data-txt', (item.msg || '').toLowerCase());\n"
"  const matchCat = (activeFilter === 'all' || item.cat === activeFilter);\n"
"  const matchSearch = (!searchQuery || row.getAttribute('data-txt').includes(searchQuery));\n"
"  if (!matchCat || !matchSearch) row.style.display = 'none';\n"
"  const timeSpan = document.createElement('span');\n"
"  timeSpan.className = 'time';\n"
"  timeSpan.textContent = item.time || '';\n"
"  const tagSpan = document.createElement('span');\n"
"  tagSpan.className = 'tag tag-' + (item.cat || 'info');\n"
"  tagSpan.textContent = (item.cat || 'info').toUpperCase();\n"
"  const msgSpan = document.createElement('span');\n"
"  msgSpan.className = 'msg' + (item.cat === 'crash' ? ' crash-msg' : '');\n"
"  msgSpan.textContent = item.msg || '';\n"
"  row.appendChild(timeSpan);\n"
"  row.appendChild(tagSpan);\n"
"  row.appendChild(msgSpan);\n"
"  logsEl.appendChild(row);\n"
"  if (logsEl.children.length > 3000) logsEl.removeChild(logsEl.firstChild);\n"
"  if (autoScroll) logsEl.scrollTop = logsEl.scrollHeight;\n"
"}\n"
"\n"
"function connectSSE() {\n"
"  const es = new EventSource('/events');\n"
"  es.onopen = () => {\n"
"    badgeEl.textContent = 'Live Connected';\n"
"    badgeEl.className = 'badge live';\n"
"  };\n"
"  es.onerror = () => {\n"
"    badgeEl.textContent = 'Reconnecting...';\n"
"    badgeEl.className = 'badge reconn';\n"
"  };\n"
"  es.onmessage = (e) => {\n"
"    try {\n"
"      const data = JSON.parse(e.data);\n"
"      appendLog(data);\n"
"    } catch(err) {}\n"
"  };\n"
"}\n"
"\n"
"async function pollStatus() {\n"
"  try {\n"
"    const res = await fetch('/api/status');\n"
"    if (res.ok) {\n"
"      const s = await res.json();\n"
"      if (s.fps !== undefined) statFps.textContent = Number(s.fps).toFixed(1);\n"
"      if (s.retired !== undefined) statRetired.textContent = s.retired;\n"
"      if (s.ttbr0 !== undefined) statSpace.textContent = s.ttbr0;\n"
"    }\n"
"  } catch (e) {}\n"
"}\n"
"\n"
"connectSSE();\n"
"setInterval(pollStatus, 1000);\n"
"</script>\n"
"</body>\n"
"</html>\n";

static pthread_t s_thread;
static bool s_running = false;
static uint16_t s_port = 0;
static int s_listen_fd = -1;
static int s_stop_pipe[2] = { -1, -1 };

static pthread_mutex_t s_lock = PTHREAD_MUTEX_INITIALIZER;
static log_entry_t s_ring[RING_BUFFER_CAP];
static uint32_t s_head = 0u;
static uint32_t s_count = 0u;
static uint32_t s_seq = 0u;

static client_t s_clients[MAX_CLIENTS];

/* Live status */
static uint64_t s_status_retired = 0u;
static double s_status_fps = 0.0;
static double s_status_mips = 0.0;
static uint32_t s_status_ttbr0 = 0u;
static char s_status_state[32] = "idle";

static void format_now(char *out, size_t cap)
{
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    struct tm tm;
    localtime_r(&ts.tv_sec, &tm);
    snprintf(out, cap, "%02d:%02d:%02d.%03ld",
             tm.tm_hour, tm.tm_min, tm.tm_sec, ts.tv_nsec / 1000000L);
}

static void escape_json_string(const char *in, char *out, size_t cap)
{
    size_t o = 0;
    for (size_t i = 0; in[i] && o + 6 < cap; i++) {
        unsigned char c = (unsigned char)in[i];
        if (c == '"') {
            out[o++] = '\\'; out[o++] = '"';
        } else if (c == '\\') {
            out[o++] = '\\'; out[o++] = '\\';
        } else if (c == '\n') {
            out[o++] = '\\'; out[o++] = 'n';
        } else if (c == '\r') {
            out[o++] = '\\'; out[o++] = 'r';
        } else if (c == '\t') {
            out[o++] = '\\'; out[o++] = 't';
        } else if (c < 0x20) {
            snprintf(out + o, cap - o, "\\u%04x", c);
            o += 6;
        } else {
            out[o++] = (char)c;
        }
    }
    out[o] = '\0';
}

void web_log_post(const char *category, const char *msg)
{
    if (!msg || !msg[0]) return;
    char time_buf[16];
    format_now(time_buf, sizeof time_buf);

    pthread_mutex_lock(&s_lock);
    uint32_t idx = (s_head + s_count) % RING_BUFFER_CAP;
    if (s_count < RING_BUFFER_CAP) {
        s_count++;
    } else {
        s_head = (s_head + 1u) % RING_BUFFER_CAP;
    }
    log_entry_t *entry = &s_ring[idx];
    entry->id = ++s_seq;
    snprintf(entry->time_str, sizeof entry->time_str, "%s", time_buf);
    snprintf(entry->category, sizeof entry->category, "%s", category ? category : "info");
    snprintf(entry->text, sizeof entry->text, "%s", msg);

    /* Broadcast to active SSE clients */
    char esc_msg[MAX_LINE_LEN * 2];
    escape_json_string(msg, esc_msg, sizeof esc_msg);
    char packet[MAX_LINE_LEN * 3];
    int len = snprintf(packet, sizeof packet,
                       "data: {\"id\":%u,\"time\":\"%s\",\"cat\":\"%s\",\"msg\":\"%s\"}\n\n",
                       entry->id, entry->time_str, entry->category, esc_msg);

    if (len > 0) {
        for (unsigned i = 0; i < MAX_CLIENTS; i++) {
            if (s_clients[i].fd >= 0 && s_clients[i].is_sse) {
                ssize_t ret = send(s_clients[i].fd, packet, (size_t)len, MSG_DONTWAIT | MSG_NOSIGNAL);
                if (ret < 0 && (errno == EPIPE || errno == ECONNRESET)) {
                    close(s_clients[i].fd);
                    s_clients[i].fd = -1;
                    s_clients[i].is_sse = false;
                }
            }
        }
    }
    pthread_mutex_unlock(&s_lock);
}

static char s_raw_pending[MAX_LINE_LEN];
static size_t s_raw_pending_len = 0;
static char s_raw_pending_cat[16] = "kernel";

void web_log_flush_raw(void)
{
    pthread_mutex_lock(&s_lock);
    if (s_raw_pending_len > 0) {
        s_raw_pending[s_raw_pending_len] = '\0';
        s_raw_pending_len = 0;
        char cat[16];
        snprintf(cat, sizeof cat, "%s", s_raw_pending_cat);
        pthread_mutex_unlock(&s_lock);
        web_log_post(cat, s_raw_pending);
        return;
    }
    pthread_mutex_unlock(&s_lock);
}

void web_log_post_raw(const char *category, const char *data, size_t len)
{
    if (!data || len == 0) return;
    const char *cat = category ? category : "kernel";

    pthread_mutex_lock(&s_lock);
    if (s_raw_pending_len > 0 && strcmp(s_raw_pending_cat, cat) != 0) {
        s_raw_pending[s_raw_pending_len] = '\0';
        char prev_cat[16];
        snprintf(prev_cat, sizeof prev_cat, "%s", s_raw_pending_cat);
        s_raw_pending_len = 0;
        pthread_mutex_unlock(&s_lock);
        web_log_post(prev_cat, s_raw_pending);
        pthread_mutex_lock(&s_lock);
    }
    snprintf(s_raw_pending_cat, sizeof s_raw_pending_cat, "%s", cat);

    for (size_t i = 0; i < len; i++) {
        char c = data[i];
        if (c == '\r') continue;
        if (c == '\n') {
            s_raw_pending[s_raw_pending_len] = '\0';
            if (s_raw_pending_len > 0) {
                pthread_mutex_unlock(&s_lock);
                web_log_post(cat, s_raw_pending);
                pthread_mutex_lock(&s_lock);
            }
            s_raw_pending_len = 0;
        } else {
            if (s_raw_pending_len + 1 < sizeof s_raw_pending) {
                s_raw_pending[s_raw_pending_len++] = c;
            } else {
                s_raw_pending[s_raw_pending_len] = '\0';
                pthread_mutex_unlock(&s_lock);
                web_log_post(cat, s_raw_pending);
                pthread_mutex_lock(&s_lock);
                s_raw_pending_len = 0;
                s_raw_pending[s_raw_pending_len++] = c;
            }
        }
    }
    pthread_mutex_unlock(&s_lock);
}

void web_log_printf(const char *category, const char *fmt, ...)
{
    char buf[MAX_LINE_LEN];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    web_log_post(category, buf);
}

void web_log_set_status(uint64_t retired, double fps, double mips,
                        uint32_t ttbr0, const char *state)
{
    pthread_mutex_lock(&s_lock);
    s_status_retired = retired;
    s_status_fps = fps;
    s_status_mips = mips;
    s_status_ttbr0 = ttbr0;
    if (state && state[0]) {
        snprintf(s_status_state, sizeof s_status_state, "%s", state);
    }
    pthread_mutex_unlock(&s_lock);
}

size_t web_log_get_entries_json(uint32_t last_id, char *out, size_t capacity)
{
    if (!out || capacity < 3) return 0;
    pthread_mutex_lock(&s_lock);
    size_t o = 0;
    out[o++] = '[';

    bool first = true;
    for (uint32_t i = 0; i < s_count; i++) {
        uint32_t idx = (s_head + i) % RING_BUFFER_CAP;
        log_entry_t *entry = &s_ring[idx];
        if (entry->id <= last_id) continue;

        char esc_msg[MAX_LINE_LEN * 2];
        escape_json_string(entry->text, esc_msg, sizeof esc_msg);

        char item[MAX_LINE_LEN * 3];
        int item_len = snprintf(item, sizeof item,
            "%s{\"id\":%u,\"time\":\"%s\",\"cat\":\"%s\",\"msg\":\"%s\"}",
            first ? "" : ",", entry->id, entry->time_str, entry->category, esc_msg);

        if (item_len > 0) {
            if (o + (size_t)item_len + 2 >= capacity) {
                break;
            }
            memcpy(out + o, item, (size_t)item_len);
            o += (size_t)item_len;
            first = false;
        }
    }
    out[o++] = ']';
    out[o] = '\0';
    pthread_mutex_unlock(&s_lock);
    return o;
}

static void send_sse_backlog(int fd)
{
    pthread_mutex_lock(&s_lock);
    for (uint32_t i = 0; i < s_count; i++) {
        uint32_t idx = (s_head + i) % RING_BUFFER_CAP;
        log_entry_t *entry = &s_ring[idx];
        char esc_msg[MAX_LINE_LEN * 2];
        escape_json_string(entry->text, esc_msg, sizeof esc_msg);
        char packet[MAX_LINE_LEN * 3];
        int len = snprintf(packet, sizeof packet,
                           "data: {\"id\":%u,\"time\":\"%s\",\"cat\":\"%s\",\"msg\":\"%s\"}\n\n",
                           entry->id, entry->time_str, entry->category, esc_msg);
        if (len > 0) {
            (void)send(fd, packet, (size_t)len, MSG_DONTWAIT | MSG_NOSIGNAL);
        }
    }
    pthread_mutex_unlock(&s_lock);
}

static void handle_client_request(int fd, int slot)
{
    char req[2048];
    ssize_t n = recv(fd, req, sizeof(req) - 1, 0);
    if (n <= 0) {
        close(fd);
        s_clients[slot].fd = -1;
        s_clients[slot].is_sse = false;
        return;
    }
    req[n] = '\0';

    if (strncmp(req, "GET /events", 11) == 0) {
        const char header[] =
            "HTTP/1.1 200 OK\r\n"
            "Content-Type: text/event-stream\r\n"
            "Cache-Control: no-cache\r\n"
            "Connection: keep-alive\r\n"
            "Access-Control-Allow-Origin: *\r\n\r\n";
        (void)send(fd, header, sizeof(header) - 1, MSG_NOSIGNAL);
        s_clients[slot].is_sse = true;
        send_sse_backlog(fd);
    } else if (strncmp(req, "GET /log.txt", 12) == 0) {
        const char header[] =
            "HTTP/1.1 200 OK\r\n"
            "Content-Type: text/plain; charset=utf-8\r\n"
            "Content-Disposition: attachment; filename=\"s5landbox_log.txt\"\r\n"
            "Connection: close\r\n\r\n";
        (void)send(fd, header, sizeof(header) - 1, MSG_NOSIGNAL);
        pthread_mutex_lock(&s_lock);
        for (uint32_t i = 0; i < s_count; i++) {
            uint32_t idx = (s_head + i) % RING_BUFFER_CAP;
            log_entry_t *entry = &s_ring[idx];
            char line[MAX_LINE_LEN + 64];
            int len = snprintf(line, sizeof line, "[%s] [%-6s] %s\n",
                               entry->time_str, entry->category, entry->text);
            if (len > 0)
                (void)send(fd, line, (size_t)len, MSG_NOSIGNAL);
        }
        pthread_mutex_unlock(&s_lock);
        close(fd);
        s_clients[slot].fd = -1;
    } else if (strncmp(req, "GET /api/status", 15) == 0) {
        char body[256];
        pthread_mutex_lock(&s_lock);
        int body_len = snprintf(body, sizeof body,
            "{\"retired\":%llu,\"fps\":%.2f,\"mips\":%.2f,\"ttbr0\":\"0x%08x\",\"state\":\"%s\"}",
            (unsigned long long)s_status_retired, s_status_fps, s_status_mips,
            s_status_ttbr0, s_status_state);
        pthread_mutex_unlock(&s_lock);

        char header[256];
        int hlen = snprintf(header, sizeof header,
            "HTTP/1.1 200 OK\r\n"
            "Content-Type: application/json\r\n"
            "Content-Length: %d\r\n"
            "Access-Control-Allow-Origin: *\r\n"
            "Connection: close\r\n\r\n", body_len);
        (void)send(fd, header, (size_t)hlen, MSG_NOSIGNAL);
        (void)send(fd, body, (size_t)body_len, MSG_NOSIGNAL);
        close(fd);
        s_clients[slot].fd = -1;
    } else {
        /* Default: serve HTML UI */
        char header[256];
        int hlen = snprintf(header, sizeof header,
            "HTTP/1.1 200 OK\r\n"
            "Content-Type: text/html; charset=utf-8\r\n"
            "Content-Length: %zu\r\n"
            "Connection: close\r\n\r\n", sizeof(HTML_PAGE) - 1);
        (void)send(fd, header, (size_t)hlen, MSG_NOSIGNAL);
        (void)send(fd, HTML_PAGE, sizeof(HTML_PAGE) - 1, MSG_NOSIGNAL);
        close(fd);
        s_clients[slot].fd = -1;
    }
}

static void *server_thread_func(void *arg)
{
    (void)arg;
    struct pollfd pfds[MAX_CLIENTS + 2];
    time_t last_ping = time(NULL);

    while (s_running) {
        int nfds = 0;

        /* pfds[0] = s_listen_fd */
        pfds[nfds].fd = s_listen_fd;
        pfds[nfds].events = POLLIN;
        pfds[nfds].revents = 0;
        int listen_idx = nfds++;

        /* pfds[1] = stop pipe */
        pfds[nfds].fd = s_stop_pipe[0];
        pfds[nfds].events = POLLIN;
        pfds[nfds].revents = 0;
        int stop_idx = nfds++;

        int client_map[MAX_CLIENTS];
        for (unsigned i = 0; i < MAX_CLIENTS; i++) {
            if (s_clients[i].fd >= 0) {
                client_map[i] = nfds;
                pfds[nfds].fd = s_clients[i].fd;
                pfds[nfds].events = POLLIN;
                pfds[nfds].revents = 0;
                nfds++;
            } else {
                client_map[i] = -1;
            }
        }

        int ret = poll(pfds, (nfds_t)nfds, 1000);
        if (ret < 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (pfds[stop_idx].revents & POLLIN) {
            break; /* shutdown requested */
        }

        /* New connection */
        if (pfds[listen_idx].revents & POLLIN) {
            struct sockaddr_in client_addr;
            socklen_t client_len = sizeof client_addr;
            int client_fd = accept(s_listen_fd, (struct sockaddr *)&client_addr, &client_len);
            if (client_fd >= 0) {
                int flags = fcntl(client_fd, F_GETFL, 0);
                if (flags >= 0) (void)fcntl(client_fd, F_SETFL, flags | O_NONBLOCK);
                int slot = -1;
                for (unsigned i = 0; i < MAX_CLIENTS; i++) {
                    if (s_clients[i].fd < 0) { slot = (int)i; break; }
                }
                if (slot >= 0) {
                    s_clients[slot].fd = client_fd;
                    s_clients[slot].is_sse = false;
                } else {
                    /* Server full */
                    close(client_fd);
                }
            }
        }

        /* Check client events */
        for (unsigned i = 0; i < MAX_CLIENTS; i++) {
            int p_idx = client_map[i];
            if (p_idx >= 0 && (pfds[p_idx].revents & (POLLIN | POLLHUP | POLLERR))) {
                if (pfds[p_idx].revents & POLLIN) {
                    handle_client_request(s_clients[i].fd, (int)i);
                } else {
                    close(s_clients[i].fd);
                    s_clients[i].fd = -1;
                    s_clients[i].is_sse = false;
                }
            }
        }

        /* Periodic SSE heartbeat ping (every 15s) */
        time_t now = time(NULL);
        if (now - last_ping >= 15) {
            last_ping = now;
            pthread_mutex_lock(&s_lock);
            const char ping_msg[] = ": ping\n\n";
            for (unsigned i = 0; i < MAX_CLIENTS; i++) {
                if (s_clients[i].fd >= 0 && s_clients[i].is_sse) {
                    ssize_t s = send(s_clients[i].fd, ping_msg, sizeof(ping_msg) - 1, MSG_DONTWAIT | MSG_NOSIGNAL);
                    if (s < 0 && (errno == EPIPE || errno == ECONNRESET)) {
                        close(s_clients[i].fd);
                        s_clients[i].fd = -1;
                        s_clients[i].is_sse = false;
                    }
                }
            }
            pthread_mutex_unlock(&s_lock);
        }
    }

    return NULL;
}

uint16_t web_log_server_start(uint16_t preferred_port)
{
    if (s_running) return s_port;

    for (unsigned i = 0; i < MAX_CLIENTS; i++) {
        s_clients[i].fd = -1;
        s_clients[i].is_sse = false;
    }

    if (pipe(s_stop_pipe) != 0) {
        return 0;
    }

    uint16_t try_port = preferred_port ? preferred_port : 8080u;
    int fd = -1;

    for (uint16_t p = try_port; p <= try_port + 10u; p++) {
        fd = socket(AF_INET, SOCK_STREAM, 0);
        if (fd < 0) continue;

        int opt = 1;
        (void)setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof opt);

        struct sockaddr_in addr;
        memset(&addr, 0, sizeof addr);
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = INADDR_ANY;
        addr.sin_port = htons(p);

        if (bind(fd, (struct sockaddr *)&addr, sizeof addr) == 0) {
            if (listen(fd, 8) == 0) {
                s_port = p;
                s_listen_fd = fd;
                break;
            }
        }
        close(fd);
        fd = -1;
    }

    if (fd < 0) {
        close(s_stop_pipe[0]);
        close(s_stop_pipe[1]);
        s_stop_pipe[0] = s_stop_pipe[1] = -1;
        return 0;
    }

    s_running = true;
    if (pthread_create(&s_thread, NULL, server_thread_func, NULL) != 0) {
        s_running = false;
        close(s_listen_fd);
        close(s_stop_pipe[0]);
        close(s_stop_pipe[1]);
        s_listen_fd = s_stop_pipe[0] = s_stop_pipe[1] = -1;
        s_port = 0;
        return 0;
    }

    web_log_printf("info", "Web log inspector started on port %u (http://<ip>:%u)", s_port, s_port);
    return s_port;
}

void web_log_server_stop(void)
{
    if (!s_running) return;
    web_log_flush_raw();
    s_running = false;

    if (s_stop_pipe[1] >= 0) {
        char b = 1;
        (void)write(s_stop_pipe[1], &b, 1);
    }
    pthread_join(s_thread, NULL);

    if (s_listen_fd >= 0) {
        close(s_listen_fd);
        s_listen_fd = -1;
    }
    if (s_stop_pipe[0] >= 0) close(s_stop_pipe[0]);
    if (s_stop_pipe[1] >= 0) close(s_stop_pipe[1]);
    s_stop_pipe[0] = s_stop_pipe[1] = -1;

    for (unsigned i = 0; i < MAX_CLIENTS; i++) {
        if (s_clients[i].fd >= 0) {
            close(s_clients[i].fd);
            s_clients[i].fd = -1;
            s_clients[i].is_sse = false;
        }
    }
    s_port = 0;
}

uint16_t web_log_server_get_port(void)
{
    return s_port;
}
