#ifndef ANETHACK_ANDROIDCRASH_H
#define ANETHACK_ANDROIDCRASH_H

// Records a short event (message shown, key read, menu result...) in a ring
// buffer that is written into the native crash report. NetHack thread only.
void crash_breadcrumb(const char *fmt, ...) __attribute__((format(printf, 1, 2)));

#endif //ANETHACK_ANDROIDCRASH_H
