/* NetHack 3.7	recover.c	$NHDT-Date: 1687547437 2023/06/23 19:10:37 $  $NHDT-Branch: NetHack-3.7 $:$NHDT-Revision: 1.33 $ */
/* Copyright (c) Janet Walz, 1992.                                */
/* NetHack may be freely redistributed.  See license for details. */

/*
 *  Utility for reconstructing NetHack save file from a set of individual
 *  level files.  Requires that the `checkpoint' option be enabled at the
 *  time NetHack creates those level files.
 */
#ifdef WIN32
#include <errno.h>
#include "win32api.h"
#endif

#define RECOVER_C

#include "config.h"
#include "hacklib.h"

#include "artifact.h"
#include "rect.h"
#include "dungeon.h"
#include "hack.h"
#include "savefile.h"

#if !defined(O_WRONLY) && !defined(LSC) && !defined(AZTEC_C)
#include <fcntl.h>
#endif

#ifdef VMS
extern int vms_creat(const char *, unsigned);
extern int vms_open(const char *, int, unsigned);
#endif /* VMS */

#ifndef nhUse
#define nhUse(arg) (void)(arg)
#endif

/* copy_bytes() has been moved to hacklib */
#include "androidrecover.h"

extern int get_critical_size_count(void);
extern uchar cscbuf[];

#ifndef WIN_CE
#define Fprintf (void) fprintf
#else
#define Fprintf (void) nhce_message
static void nhce_message(FILE *, const char *, ...);
#endif

#define Close (void) close

#if defined(EXEPATH)
char *exepath(char *);
#endif

#if defined(__BORLANDC__) && !defined(_WIN32)
extern unsigned _stklen = STKSIZ;
#endif

/* SAVESIZE is defined in "fnamesiz.h" */
char savename[SAVESIZE]; /* holds relative path of save file from playground */

DISABLE_WARNING_UNREACHABLE_CODE

int
main(int argc, char *argv[])
{
    int argno;
    const char *dir = (char *) 0, *progname = (char *) 0;
#ifdef AMIGA
    char *startdir = (char *) 0;
#endif

    if (!dir)
        dir = getenv("NETHACKDIR");
    if (!dir)
        dir = getenv("HACKDIR");
#if defined(EXEPATH)
    if (!dir)
        dir = exepath(argv[0]);
#endif
    if (argc > 0)
        progname = argv[0];
    if (!progname || !*progname)
        progname = "recover";
#ifdef VMS
    /*progname = vms_basebame(progname, FALSE);*/ /* needs vmsfiles.obj */
#endif

    if (argc < 2 || (argc == 2 && !strcmp(argv[1], "-"))) {
        Fprintf(stderr, "Usage: %s [ -d directory ] base1 [ base2 ... ]\n",
                progname);
#if defined(WIN32) || defined(MSDOS)
        if (dir) {
            Fprintf(stderr,
                   "\t(Unless you override it with -d, recover will look \n");
            Fprintf(stderr, "\t in the %s directory on your system)\n", dir);
        }
#endif
        exit(EXIT_FAILURE);
    }

    argno = 1;
    if (!strncmp(argv[argno], "-d", 2)) {
        dir = argv[argno] + 2;
        if (*dir == '=' || *dir == ':')
            dir++;
        if (!*dir && argc > argno) {
            argno++;
            dir = argv[argno];
        }
        if (!*dir) {
            Fprintf(stderr,
                    "%s: flag -d must be followed by a directory name.\n",
                    argv[0]);
            exit(EXIT_FAILURE);
        }
        argno++;
    }
#if defined(SECURE) && !defined(VMS)
    if (dir
#ifdef HACKDIR
        && strcmp(dir, HACKDIR)
#endif
            ) {
        (void) setgid(getgid());
        (void) setuid(getuid());
    }
#endif /* SECURE && !VMS */

#ifdef HACKDIR
    if (!dir)
        dir = HACKDIR;
#endif

#ifdef AMIGA
    startdir = getcwd(0, 255);
#endif
    if (dir && chdir((char *) dir) < 0) {
        Fprintf(stderr, "%s: cannot chdir to %s.\n", argv[0], dir);
        exit(EXIT_FAILURE);
    }

    while (argc > argno) {
        if (and_restore_savefile(argv[argno]) == 0)
            Fprintf(stderr, "recovered \"%s\" to %s\n", argv[argno],
                    savename);
        argno++;
    }
#ifdef AMIGA
    if (startdir)
        (void) chdir(startdir);
#endif
    exit(EXIT_SUCCESS);
    /*NOTREACHED*/
    return 0;
}

RESTORE_WARNINGS

static char lock[256];

void
and_set_levelfile_name(int lev)
{
    char *tf;

    tf = strrchr(lock, '.');
    if (!tf)
        tf = lock + strlen(lock);
    (void) sprintf(tf, ".%d", lev);
#ifdef VMS
    (void) strcat(tf, ";1");
#endif
}

int
and_open_levelfile(int lev)
{
    int fd;

    and_set_levelfile_name(lev);
#if defined(MICRO) || defined(WIN32) || defined(MSDOS)
    fd = open(lock, O_RDONLY | O_BINARY);
#else
    fd = open(lock, O_RDONLY, 0);
#endif
    return fd;
}

int
and_create_savefile(void)
{
    int fd;

#if defined(MICRO) || defined(WIN32) || defined(MSDOS)
    fd = open(savename, O_WRONLY | O_BINARY | O_CREAT | O_TRUNC, FCMASK);
#else
    fd = creat(savename, FCMASK);
#endif
    return fd;
}

int
and_restore_savefile(char *basename)
{
    int gfd, lfd, sfd;
    int res = 0, lev, savelev, hpid, pltmpsiz,
        cscount = get_critical_size_count();
    xint8 levc;
    struct version_info version_data;
    NHFILE snhfp;
    char plbuf[PL_NSIZ_PLUS], indicator, file_cscount;
    off_t filesz = 0;

    /* level 0 file contains:
     *  pid of creating process (ignored here)
     *  level number for current level of save file
     *  name of save file nethack would have created
     *  player name
     *  and game state
     */
    (void) strcpy(lock, basename);
    gfd = and_open_levelfile(0);
    if (gfd < 0) {
#if defined(WIN32) && !defined(WIN_CE)
        if (errno == EACCES) {
            Fprintf(
                stderr,
                "\nThere are files from a game in progress under your name.");
            Fprintf(stderr, "\nThe files are locked or inaccessible.");
            Fprintf(stderr, "\nPerhaps the other game is still running?\n");
        } else
            Fprintf(stderr, "\nTrouble accessing level 0 (errno = %d).\n",
                    errno);
#endif
        Fprintf(stderr, "Cannot open level 0 for %s.\n", basename);
        return -1;
    }
    filesz = lseek(gfd, 0L, SEEK_END);
    (void) lseek(gfd, 0L, SEEK_SET);
    if (filesz >= 0
        && (size_t) filesz < (sizeof hpid
                              + sizeof savelev
                              + sizeof savename
                              + sizeof indicator
                              + sizeof file_cscount
                              + sizeof version_data
                              + sizeof pltmpsiz)) {
        Fprintf(stderr, "Checkpoint data in %s is too short -- can't recover.\n",
                basename);
        Close(gfd);
        (void) unlink(lock);
        return -1;
    }
    if (read(gfd, (genericptr_t) &hpid, sizeof hpid) != sizeof hpid) {
        Fprintf(
                stderr, "%s\n%s%s%s\n",
                "Checkpoint data incompletely written or subsequently clobbered;",
                "recovery for \"", basename, "\" impossible.");
        Close(gfd);
        return -1;
    }
    if (read(gfd, (genericptr_t) &savelev, sizeof(savelev))
        != sizeof(savelev)) {
        Fprintf(stderr, "Checkpointing was not in effect for %s -- recovery "
                        "impossible.\n",
                basename);
        Close(gfd);
        return -1;
    }
    if ((read(gfd, (genericptr_t) savename, sizeof savename)
         != sizeof savename)
        || (read(gfd, (genericptr_t) &indicator, sizeof indicator)
            != sizeof indicator)
        || (read(gfd, (genericptr_t) &file_cscount, sizeof file_cscount)
            != sizeof file_cscount)
        || (file_cscount <= 0 || file_cscount > cscount)
        || (read(gfd, (genericptr_t) cscbuf, file_cscount)
            != file_cscount)
        || (read(gfd, (genericptr_t) &version_data, sizeof version_data)
            != sizeof version_data)
        || (read(gfd, (genericptr_t) &pltmpsiz, sizeof pltmpsiz)
            != sizeof pltmpsiz) || (pltmpsiz > PL_NSIZ_PLUS)
        || (read(gfd, (genericptr_t) plbuf, pltmpsiz) != pltmpsiz)) {
        Fprintf(stderr, "Error reading %s -- can't recover.\n", lock);
        Close(gfd);
        return -1;
    }

    /* save file should contain:
     *  format indicator (1 byte)
     *  n = count of critical size list (1 byte)
     *  n bytes of critical sizes (n bytes)
     *  version info
     *  plnametmp = player name size (int, 2 bytes)
     *  player name (PL_NSIZ_PLUS)
     *  current level (including pets)
     *  (non-level-based) game state
     *  other levels
     */
    sfd = and_create_savefile();
    if (sfd < 0) {
        Fprintf(stderr, "Cannot create savefile %s.\n", savename);
        Close(gfd);
        return -1;
    }

    lfd = and_open_levelfile(savelev);
    if (lfd < 0) {
        Fprintf(stderr, "Cannot open level of save for %s.\n", basename);
        Close(gfd);
        Close(sfd);
        (void) unlink(savename);
        return -1;
    }

    memset((genericptr_t) &snhfp, 0, sizeof snhfp);
    init_nhfile(&snhfp);
    snhfp.ftype = NHF_SAVEFILE;
    snhfp.mode = WRITING;
    snhfp.structlevel = TRUE;
    snhfp.fieldlevel = FALSE;
    snhfp.addinfo = FALSE;
    snhfp.style.deflt = FALSE;
    snhfp.style.binary = TRUE;
    snhfp.fnidx = historical;
    snhfp.fd = sfd;
    store_version(&snhfp);
    if (snhfp.structlevel)
        bufoff(snhfp.fd);

    sfo_int(&snhfp, &pltmpsiz, "plname-size");

    assert((size_t) pltmpsiz <= sizeof plbuf);
    sfo_char(&snhfp, plbuf, "plname", pltmpsiz);

    if (!copy_bytes(lfd, sfd)) {
        Fprintf(stderr, "file copy failed!\n");
        Close(gfd);
        Close(lfd);
        bclose(sfd);
        (void) unlink(savename);
        return -1;
    }
    Close(lfd);
    (void) unlink(lock);

    if (!copy_bytes(gfd, sfd)) {
        Fprintf(stderr, "file copy failed!\n");
        bclose(sfd);
        (void) unlink(savename);
        return -1;
    }
    Close(gfd);
    and_set_levelfile_name(0);
    (void) unlink(lock);

    for (lev = 1; lev < 256 && res == 0; lev++) {
        /* level numbers are kept in 'xint8's in save.c, so the
         * maximum level number (for the endlevel) must be < 256
         */
        if (lev != savelev) {
            lfd = and_open_levelfile(lev);
            if (lfd >= 0) {
                /* any or all of these may not exist */
                levc = (xint8) lev;
                if (write(sfd, (genericptr_t) &levc, sizeof levc)
                    != sizeof levc) {
                    res = -1;
                } else {
                    if (!copy_bytes(lfd, sfd)) {
                        Fprintf(stderr, "file copy failed!\n");
                        Close(lfd);
                        bclose(sfd);
                        (void) unlink(savename);
                        return -1;
                    }
                }
                Close(lfd);
                (void) unlink(lock);
            }
        }
    }

    if (snhfp.structlevel)
        bufon(snhfp.fd);
    bclose(sfd);

#if 0 /* OBSOLETE, HackWB is no longer in use */
    #ifdef AMIGA
    if (res == 0) {
        /* we need to create an icon for the saved game
         * or HackWB won't notice the file.
         */
        char iconfile[FILENAME];
        int in, out;

        (void) sprintf(iconfile, "%s.info", savename);
        in = open("NetHack:default.icon", O_RDONLY);
        out = open(iconfile, O_WRONLY | O_TRUNC | O_CREAT);
        if (in > -1 && out > -1) {
            if (!copy_bytes(in, out)) {
                Fprintf(stderr, "file copy failed!\n");
                exit(EXIT_FAILURE);
            }
        }
        if (in > -1)
            close(in);
        if (out > -1)
            close(out);
    }
#endif /*AMIGA*/
#endif
    return res;
}

#ifdef EXEPATH
#ifdef __DJGPP__
#define PATH_SEPARATOR '/'
#else
#define PATH_SEPARATOR '\\'
#endif

#define EXEPATHBUFSZ 256
char exepathbuf[EXEPATHBUFSZ];

char *
exepath(char *str)
{
    char *tmp, *tmp2;
    int bsize;

    if (!str)
        return (char *) 0;
    nhUse(bsize);
    bsize = EXEPATHBUFSZ;
    tmp = exepathbuf;
#if !defined(WIN32)
    strcpy(tmp, str);
#else
#if defined(WIN_CE)
    {
        TCHAR wbuf[EXEPATHBUFSZ];
        GetModuleFileName((HANDLE) 0, wbuf, EXEPATHBUFSZ);
        NH_W2A(wbuf, tmp, bsize);
    }
#else
    *(tmp + GetModuleFileName((HANDLE) 0, tmp, bsize)) = '\0';
#endif
#endif
    tmp2 = strrchr(tmp, PATH_SEPARATOR);
    if (tmp2)
        *tmp2 = '\0';
    return tmp;
}
#endif /* EXEPATH */

#ifdef WIN_CE
void
nhce_message(FILE *f, const char *str, ...)
{
    va_list ap;
    TCHAR wbuf[NHSTR_BUFSIZE];
    char buf[NHSTR_BUFSIZE];

    va_start(ap, str);
    vsprintf(buf, str, ap);
    va_end(ap);

    MessageBox(NULL, NH_A2W(buf, wbuf, NHSTR_BUFSIZE), TEXT("Recover"),
               MB_OK);
}
#endif

/*recover.c*/
