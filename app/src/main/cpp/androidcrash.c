/* androidcrash.c
 *
 * Native crash reporter. On a fatal signal it writes a plain-text report
 * (signal, registers, backtrace, game state, recent window-port events) to
 * <files>/crash/native_<time>.log, so it survives the process and can be
 * exported after the next launch, then passes the signal on to the previous
 * handler so the system still produces its own tombstone.
 *
 * Everything that runs inside the signal handler only uses static buffers
 * and plain system calls; nothing there allocates or formats with stdio.
 */

#ifndef _GNU_SOURCE
#define _GNU_SOURCE /* REG_* register indices on x86 */
#endif
#include <jni.h>
#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <link.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <unwind.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <sys/ucontext.h>
#include <sys/uio.h>
#include <android/log.h>

/* <elf.h> may define LL_NONE, which NetHack reuses for livelog flags */
#undef LL_NONE
#include "hack.h"
#include "androidcrash.h"

#define TAG "NativeNetHackCrash"

#define BREADCRUMB_COUNT 64
#define BREADCRUMB_SIZE 200
#define MAX_FRAMES 64

static const int crash_signals[] = {
    SIGSEGV, SIGABRT, SIGBUS, SIGFPE, SIGILL, SIGTRAP, SIGSYS
};
#define NUM_CRASH_SIGNALS ((int) (sizeof crash_signals / sizeof crash_signals[0]))

static struct sigaction old_actions[NUM_CRASH_SIGNALS];
static int handlers_installed;
static volatile sig_atomic_t handling_crash;

static char report_dir[512];
static char report_header[1024];
static char lib_info[256];
static char report_path[600];

static char breadcrumbs[BREADCRUMB_COUNT][BREADCRUMB_SIZE];
static volatile unsigned breadcrumb_next;

static uintptr_t frames[MAX_FRAMES];
static int frame_count;

/* ---- breadcrumbs (NetHack thread, normal context) ---- */

void crash_breadcrumb(const char *fmt, ...)
{
    char *slot = breadcrumbs[breadcrumb_next % BREADCRUMB_COUNT];
    int len = snprintf(slot, BREADCRUMB_SIZE, "[T%ld] ", svm.moves);
    va_list ap;

    if (len < 0 || len >= BREADCRUMB_SIZE)
        len = 0;
    va_start(ap, fmt);
    vsnprintf(slot + len, BREADCRUMB_SIZE - len, fmt, ap);
    va_end(ap);
    breadcrumb_next++;
}

/* ---- async-signal-safe output ---- */

static int out_fd = -1;
static char out_buf[2048];
static size_t out_len;

static void out_flush(void)
{
    size_t off = 0;

    while (out_fd >= 0 && off < out_len) {
        ssize_t n = write(out_fd, out_buf + off, out_len - off);
        if (n <= 0)
            break;
        off += (size_t) n;
    }
    out_len = 0;
}

static void out_strn(const char *s, size_t max)
{
    size_t i;

    for (i = 0; s && i < max && s[i]; i++) {
        if (out_len == sizeof out_buf)
            out_flush();
        out_buf[out_len++] = s[i];
    }
}

static void out_str(const char *s)
{
    out_strn(s, (size_t) -1);
}

static void out_hex(uintptr_t v)
{
    char tmp[2 + 2 * sizeof v + 1];
    int i = (int) sizeof tmp - 1;

    tmp[i] = '\0';
    do {
        tmp[--i] = "0123456789abcdef"[v & 0xf];
        v >>= 4;
    } while (v && i > 2);
    tmp[--i] = 'x';
    tmp[--i] = '0';
    out_str(&tmp[i]);
}

/* formats v at the end of tmp[24] and returns where the digits start */
static const char *dec_str(long v, char *tmp)
{
    int i = 23;
    unsigned long m = v < 0 ? 0UL - (unsigned long) v : (unsigned long) v;

    tmp[i] = '\0';
    do {
        tmp[--i] = (char) ('0' + m % 10);
        m /= 10;
    } while (m && i > 1);
    if (v < 0)
        tmp[--i] = '-';
    return &tmp[i];
}

static void out_dec(long v)
{
    char tmp[24];

    out_str(dec_str(v, tmp));
}

/* ---- report sections ---- */

static const char *signal_name(int sig)
{
    switch (sig) {
    case SIGSEGV: return "SIGSEGV";
    case SIGABRT: return "SIGABRT";
    case SIGBUS:  return "SIGBUS";
    case SIGFPE:  return "SIGFPE";
    case SIGILL:  return "SIGILL";
    case SIGTRAP: return "SIGTRAP";
    case SIGSYS:  return "SIGSYS";
    default:      return "?";
    }
}

static const char *signal_code_name(int sig, int code)
{
    switch (code) {
    case SI_USER:  return "SI_USER";
    case SI_TKILL: return "SI_TKILL";
    case SI_QUEUE: return "SI_QUEUE";
    default: break;
    }
    if (sig == SIGSEGV) {
        if (code == SEGV_MAPERR) return "SEGV_MAPERR";
        if (code == SEGV_ACCERR) return "SEGV_ACCERR";
    } else if (sig == SIGBUS) {
        if (code == BUS_ADRALN) return "BUS_ADRALN";
        if (code == BUS_ADRERR) return "BUS_ADRERR";
        if (code == BUS_OBJERR) return "BUS_OBJERR";
    } else if (sig == SIGFPE) {
        if (code == FPE_INTDIV) return "FPE_INTDIV";
        if (code == FPE_FLTDIV) return "FPE_FLTDIV";
    } else if (sig == SIGILL) {
        if (code == ILL_ILLOPC) return "ILL_ILLOPC";
        if (code == ILL_ILLTRP) return "ILL_ILLTRP";
    }
    return "?";
}

static void out_frame(int idx, uintptr_t pc)
{
    Dl_info info;

    out_str("  #");
    if (idx < 10)
        out_str("0");
    out_dec(idx);
    out_str(" pc ");
    out_hex(pc);
    if (dladdr((void *) pc, &info) && info.dli_fname) {
        out_str("  ");
        out_str(info.dli_fname);
        out_str(" (offset ");
        out_hex(pc - (uintptr_t) info.dli_fbase);
        out_str(")");
        if (info.dli_sname) {
            out_str(" ");
            out_str(info.dli_sname);
            out_str("+");
            out_hex(pc - (uintptr_t) info.dli_saddr);
        }
    }
    out_str("\n");
}

static _Unwind_Reason_Code unwind_frame(struct _Unwind_Context *ctx, void *arg)
{
    uintptr_t pc = _Unwind_GetIP(ctx);

    (void) arg;
    if (frame_count >= MAX_FRAMES)
        return _URC_END_OF_STACK;
    if (pc)
        frames[frame_count++] = pc;
    return _URC_NO_REASON;
}

#if defined(__aarch64__)
/* reads through the kernel so a bad pointer fails instead of faulting again */
static int read_memory(uintptr_t addr, void *dst, size_t len)
{
    struct iovec local = { dst, len };
    struct iovec remote = { (void *) addr, len };

    return process_vm_readv(getpid(), &local, 1, &remote, 1, 0) == (ssize_t) len;
}

/* arm64 Android keeps frame records (x29 chain); a fallback for when the
   unwinder cannot step out of the signal frame */
static void out_frame_pointer_walk(uintptr_t fp, uintptr_t sp)
{
    int n = 0;

    out_str("\nframe pointer chain:\n");
    while (n < MAX_FRAMES && fp && (fp & 0x7) == 0 && fp >= sp) {
        uintptr_t record[2]; /* previous fp, return address */

        if (!read_memory(fp, record, sizeof record) || !record[1])
            break;
        out_frame(++n, record[1] & 0x0000ffffffffffffULL); /* drop PAC/tag bits */
        if (record[0] <= fp)
            break;
        fp = record[0];
    }
}
#endif

static void out_backtrace(const ucontext_t *uc)
{
    uintptr_t pc = 0, lr = 0, sp = 0, fp = 0;
    int i, start = -1;

#if defined(__aarch64__)
    pc = uc->uc_mcontext.pc;
    lr = uc->uc_mcontext.regs[30];
    fp = uc->uc_mcontext.regs[29];
    sp = uc->uc_mcontext.sp;
#elif defined(__arm__)
    pc = uc->uc_mcontext.arm_pc;
    lr = uc->uc_mcontext.arm_lr;
    fp = uc->uc_mcontext.arm_fp;
    sp = uc->uc_mcontext.arm_sp;
#elif defined(__x86_64__)
    pc = uc->uc_mcontext.gregs[REG_RIP];
    fp = uc->uc_mcontext.gregs[REG_RBP];
    sp = uc->uc_mcontext.gregs[REG_RSP];
#elif defined(__i386__)
    pc = uc->uc_mcontext.gregs[REG_EIP];
    fp = uc->uc_mcontext.gregs[REG_EBP];
    sp = uc->uc_mcontext.gregs[REG_ESP];
#endif
    out_str("registers: pc ");
    out_hex(pc);
    out_str("  lr ");
    out_hex(lr);
    out_str("  sp ");
    out_hex(sp);
    out_str("  fp ");
    out_hex(fp);
    out_str("\n\nbacktrace (frames after #00 are return addresses):\n");

    frame_count = 0;
    _Unwind_Backtrace(unwind_frame, NULL);
    /* the unwinder starts inside this handler; skip to the faulting frame */
    for (i = 0; i < frame_count; i++)
        if ((frames[i] & ~(uintptr_t) 1) == (pc & ~(uintptr_t) 1)) {
            start = i;
            break;
        }
    if (start >= 0) {
        for (i = start; i < frame_count; i++)
            out_frame(i - start, frames[i]);
    } else {
        out_frame(0, pc);
        if (lr)
            out_frame(1, lr);
    }
#if defined(__aarch64__)
    if (start < 0 || frame_count - start < 3)
        out_frame_pointer_walk(fp, sp);
#endif
    if (start < 0) {
        out_str("\nunwinder frames (include crash handler frames):\n");
        for (i = 0; i < frame_count; i++)
            out_frame(i, frames[i]);
    }
}

static void out_game_state(void)
{
    out_str("\ngame state:\n  moves ");
    out_dec(svm.moves);
    out_str("  dungeon ");
    out_dec(u.uz.dnum);
    out_str(" level ");
    out_dec(u.uz.dlevel);
    out_str("\n  hero ");
    out_dec(u.ux);
    out_str(",");
    out_dec(u.uy);
    out_str("  travel target ");
    out_dec(u.tx);
    out_str(",");
    out_dec(u.ty);
    out_str("  travelcc ");
    out_dec(iflags.travelcc.x);
    out_str(",");
    out_dec(iflags.travelcc.y);
    out_str("\n  run ");
    out_dec(svc.context.run);
    out_str("  travel ");
    out_dec(svc.context.travel);
    out_str("  travel1 ");
    out_dec(svc.context.travel1);
    out_str("  multi ");
    out_dec((long) gm.multi);
    out_str("\n");
}

static void out_breadcrumbs(void)
{
    unsigned end = breadcrumb_next;
    unsigned i = end > BREADCRUMB_COUNT ? end - BREADCRUMB_COUNT : 0;

    out_str("\nrecent events (oldest first):\n");
    for (; i < end; i++) {
        out_str("  ");
        out_strn(breadcrumbs[i % BREADCRUMB_COUNT], BREADCRUMB_SIZE);
        out_str("\n");
    }
}

static void write_report(int sig, siginfo_t *info, const ucontext_t *uc)
{
    struct timespec now;
    char thread_name[17] = "";
    char millis_buf[24];
    long millis;

    clock_gettime(CLOCK_REALTIME, &now);
    millis = (long) now.tv_sec * 1000L + now.tv_nsec / 1000000L;

    /* <dir>/native_<millis>.log; report_dir is short enough to fit
       (nativeInstall limits it to 512 bytes) */
    strcpy(report_path, report_dir);
    strcat(report_path, "/native_");
    strcat(report_path, dec_str(millis, millis_buf));
    strcat(report_path, ".log");

    out_len = 0;
    out_fd = open(report_path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (out_fd < 0)
        return;
    prctl(PR_GET_NAME, thread_name, 0, 0, 0);

    out_str("*** ANetHack native crash ***\n");
    out_str(report_header);
    out_str("\n");
    out_str(lib_info);
    out_str("\ntime: ");
    out_dec(millis);
    out_str(" (unix ms)\npid ");
    out_dec(getpid());
    out_str("  tid ");
    out_dec(gettid());
    out_str("  thread \"");
    out_str(thread_name);
    out_str("\"\n\nsignal ");
    out_dec(sig);
    out_str(" (");
    out_str(signal_name(sig));
    out_str("), code ");
    out_dec(info->si_code);
    out_str(" (");
    out_str(signal_code_name(sig, info->si_code));
    out_str("), fault addr ");
    out_hex((uintptr_t) info->si_addr);
    out_str("\n");
    if (sig == SIGABRT)
        out_str("abort message: see logcat_*.txt in this export (lines tagged F/ or libc)\n");
    out_backtrace(uc);
    out_game_state();
    out_breadcrumbs();
    out_flush();
    close(out_fd);
    out_fd = -1;
}

/* ---- signal handling ---- */

static void restore_old_handlers(void)
{
    int i;

    for (i = 0; i < NUM_CRASH_SIGNALS; i++)
        sigaction(crash_signals[i], &old_actions[i], NULL);
}

static void pass_to_old_handler(int sig, siginfo_t *info, void *uc)
{
    struct sigaction *old = NULL;
    int i;

    for (i = 0; i < NUM_CRASH_SIGNALS; i++)
        if (crash_signals[i] == sig)
            old = &old_actions[i];
    restore_old_handlers();
    if (old && (old->sa_flags & SA_SIGINFO)) {
        if ((void *) old->sa_sigaction != (void *) SIG_DFL
            && (void *) old->sa_sigaction != (void *) SIG_IGN) {
            old->sa_sigaction(sig, info, uc);
            return;
        }
    } else if (old && old->sa_handler == SIG_IGN) {
        return;
    } else if (old && old->sa_handler != SIG_DFL) {
        old->sa_handler(sig);
        return;
    }
    /* default action: a hardware fault repeats when we return; anything
       raised explicitly (abort, kill) has to be raised again */
    if (info->si_code <= 0)
        syscall(SYS_tgkill, getpid(), gettid(), sig);
}

static void crash_handler(int sig, siginfo_t *info, void *uc)
{
    if (!handling_crash) {
        handling_crash = 1;
        write_report(sig, info, (const ucontext_t *) uc);
    }
    pass_to_old_handler(sig, info, uc);
}

static int find_build_id(struct dl_phdr_info *phdr_info, size_t size, void *data)
{
    const char *self = (const char *) data;
    int i;

    (void) size;
    if (!phdr_info->dlpi_name || strcmp(phdr_info->dlpi_name, self) != 0)
        return 0;
    for (i = 0; i < phdr_info->dlpi_phnum; i++) {
        const ElfW(Phdr) *ph = &phdr_info->dlpi_phdr[i];
        const char *p, *end;

        if (ph->p_type != PT_NOTE)
            continue;
        p = (const char *) (phdr_info->dlpi_addr + ph->p_vaddr);
        end = p + ph->p_memsz;
        while (p + sizeof(ElfW(Nhdr)) <= end) {
            const ElfW(Nhdr) *note = (const ElfW(Nhdr) *) p;
            const char *name = p + sizeof *note;
            const unsigned char *desc =
                (const unsigned char *) name + ((note->n_namesz + 3) & ~3u);

            if (note->n_type == NT_GNU_BUILD_ID && note->n_namesz == 4
                && memcmp(name, "GNU", 4) == 0) {
                size_t len = strlen(lib_info), k;

                strncat(lib_info, " build-id ", sizeof lib_info - len - 1);
                len = strlen(lib_info);
                for (k = 0; k < note->n_descsz && len + 2 < sizeof lib_info; k++) {
                    lib_info[len++] = "0123456789abcdef"[desc[k] >> 4];
                    lib_info[len++] = "0123456789abcdef"[desc[k] & 0xf];
                }
                lib_info[len] = '\0';
                return 1;
            }
            p = (const char *) desc + ((note->n_descsz + 3) & ~3u);
        }
    }
    return 0;
}

static void describe_library(void)
{
    Dl_info info;

    if (!dladdr((void *) describe_library, &info) || !info.dli_fname) {
        snprintf(lib_info, sizeof lib_info, "native lib: unknown");
        return;
    }
    snprintf(lib_info, sizeof lib_info, "native lib: %s base %p",
             info.dli_fname, info.dli_fbase);
    dl_iterate_phdr(find_build_id, (void *) info.dli_fname);
}

JNIEXPORT void JNICALL
Java_com_yywspace_anethack_CrashReporter_nativeInstall(JNIEnv *env, jobject thiz,
                                                       jstring dir, jstring header)
{
    const char *dir_c = (*env)->GetStringUTFChars(env, dir, NULL);
    const char *header_c = (*env)->GetStringUTFChars(env, header, NULL);
    struct sigaction action;
    int i;

    (void) thiz;
    snprintf(report_dir, sizeof report_dir, "%s", dir_c);
    snprintf(report_header, sizeof report_header, "%s", header_c);
    (*env)->ReleaseStringUTFChars(env, dir, dir_c);
    (*env)->ReleaseStringUTFChars(env, header, header_c);
    if (handlers_installed)
        return;
    describe_library();

    memset(&action, 0, sizeof action);
    sigemptyset(&action.sa_mask);
    action.sa_sigaction = crash_handler;
    /* ONSTACK: bionic gives every thread an alternate stack, so stack
       overflows are reported too; NODEFER: a fault inside the handler
       reaches the old handler instead of killing us silently */
    action.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_NODEFER;
    for (i = 0; i < NUM_CRASH_SIGNALS; i++)
        sigaction(crash_signals[i], &action, &old_actions[i]);
    handlers_installed = 1;
    __android_log_print(ANDROID_LOG_INFO, TAG, "crash handler installed, reports in %s", report_dir);
}

/* debug builds only (see SettingsActivity): proves the handler works */
JNIEXPORT void JNICALL
Java_com_yywspace_anethack_CrashReporter_nativeTestCrash(JNIEnv *env, jobject thiz)
{
    (void) env;
    (void) thiz;
    crash_breadcrumb("test crash requested from settings");
    raise(SIGSEGV);
}
