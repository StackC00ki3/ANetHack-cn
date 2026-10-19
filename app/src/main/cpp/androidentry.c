#include <jni.h>
#include <string.h>
#include <errno.h>
#include <ctype.h>

#include "hack.h"
#include "func_tab.h"   /* for extended commands */
#include "dlb.h"

#include "androidentry.h"

JNIEnv* jEnv;
jclass jApp;
jobject jAppInstance;
// window
jmethodID jRequireKeyCommand;
jmethodID jRequirePosKeyCommand;
jmethodID jCreateWindow;
jmethodID jDisplayWindow;
jmethodID jClearWindow;
jmethodID jDestroyWindow;
jmethodID jPutString;
jmethodID jRawPrint;
jmethodID jCurs;
jmethodID jPrintTile;
jmethodID jYNFunction;
jmethodID jGetLine;
jmethodID jStartMenu;
jmethodID jAddMenu;
jmethodID jEndMenu;
jmethodID jSelectMenu;
jmethodID jClipAround;
jmethodID jDelayOutput;
jmethodID jSetNumPadOption;
jmethodID jAskName;
jmethodID jRenderStatus;
jmethodID jShowExtCmdMenu;
jmethodID jGetMessageHistory;
jmethodID jPutMessageHistory;
// sound
jmethodID jInitNHSound;
jmethodID jExitNHSound;
jmethodID jSoundAchievement;
jmethodID jSoundEffect;
jmethodID jHeroPlayNotes;
jmethodID jPlayUserSound;
jmethodID jSoundAmbience;
jmethodID jSoundVerbal;

int NetHackMain(int argc, char** argv);
void initNetHackSound();
void initNetHackWin();

JNIEXPORT void JNICALL
Java_com_yywspace_anethack_NetHack_runNetHack(JNIEnv *env, jobject thiz, jstring path) {
    char* params[10];
    params[0] = "NetHack";
    params[1] = jstring2Char(env, path);
    jEnv = env;
    jAppInstance = thiz;
    jApp = (*jEnv)->GetObjectClass(jEnv, jAppInstance);
    initNetHackWin();
    initNetHackSound();
    NetHackMain(2, params);
}

JNIEXPORT void JNICALL
Java_com_yywspace_anethack_NetHack_stopNetHack(JNIEnv *env, jobject thiz) {
    nh_terminate(EXIT_SUCCESS);
}

void initNetHackWin() {
    jCreateWindow = (*jEnv)->GetMethodID(jEnv,jApp, "createWindow", "(I)I");
    jStartMenu = (*jEnv)->GetMethodID(jEnv, jApp, "startMenu", "(IJ)V");
    jAddMenu = (*jEnv)->GetMethodID(jEnv, jApp, "addMenu", "(IIJCCIILjava/lang/String;Z)V");
    jEndMenu = (*jEnv)->GetMethodID(jEnv, jApp, "endMenu", "(ILjava/lang/String;)V");
    jSelectMenu = (*jEnv)->GetMethodID(jEnv, jApp, "selectMenu", "(II)[J");
    jDestroyWindow = (*jEnv)->GetMethodID(jEnv, jApp, "destroyWindow", "(I)V");
    jClearWindow = (*jEnv)->GetMethodID(jEnv, jApp, "clearWindow", "(II)V");
    jRawPrint = (*jEnv)->GetMethodID(jEnv, jApp, "rawPrint", "(ILjava/lang/String;)V");
    jDisplayWindow = (*jEnv)->GetMethodID(jEnv, jApp, "displayWindow", "(IZ)V");
    jCurs = (*jEnv)->GetMethodID(jEnv, jApp, "curs", "(III)V");
    jPutString = (*jEnv)->GetMethodID(jEnv, jApp, "putString", "(IILjava/lang/String;I)V");
    jPrintTile = (*jEnv)->GetMethodID(jEnv, jApp, "printTile", "(IIIIIII)V");
    jRequireKeyCommand = (*jEnv)->GetMethodID(jEnv, jApp, "requireKeyCommand", "()I");
    jRequirePosKeyCommand = (*jEnv)->GetMethodID(jEnv, jApp, "requirePosKeyCommand", "([I)C");
    jRenderStatus = (*jEnv)->GetMethodID(jEnv, jApp, "renderStatus",
                                         "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;III)V");
    jDelayOutput = (*jEnv)->GetMethodID(jEnv, jApp, "delayOutput", "()V");
    jClipAround = (*jEnv)->GetMethodID(jEnv, jApp, "clipAround", "(IIII)V");
    jYNFunction = (*jEnv)->GetMethodID(jEnv, jApp, "ynFunction",
                                       "(Ljava/lang/String;Ljava/lang/String;[JC)C");
    jGetLine = (*jEnv)->GetMethodID(jEnv, jApp, "getLine", "(Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;");
    jAskName = (*jEnv)->GetMethodID(jEnv, jApp, "askName","(I[Ljava/lang/String;)[Ljava/lang/String;");
    jShowExtCmdMenu = (*jEnv)->GetMethodID(jEnv, jApp, "showExtCmdMenu", "([Ljava/lang/String;)I");
    jGetMessageHistory = (*jEnv)->GetMethodID(jEnv, jApp, "getMessageHistory","(I)Ljava/lang/String;");
    jPutMessageHistory = (*jEnv)->GetMethodID(jEnv, jApp, "putMessageHistory", "(Ljava/lang/String;Z)V");
}

void initNetHackSound() {
    jInitNHSound = (*jEnv)->GetMethodID(jEnv, jApp, "initNHSound", "()V");
    jExitNHSound = (*jEnv)->GetMethodID(jEnv, jApp, "exitNHSound", "(Ljava/lang/String;)V");
    jSoundAchievement = (*jEnv)->GetMethodID(jEnv, jApp, "soundAchievement", "(III)V");
    jSoundEffect = (*jEnv)->GetMethodID(jEnv, jApp, "soundEffect",
                                        "(Ljava/lang/String;Ljava/lang/String;II)V");
    jHeroPlayNotes = (*jEnv)->GetMethodID(jEnv, jApp, "heroPlayNotes", "(ILjava/lang/String;I)V");
    jPlayUserSound = (*jEnv)->GetMethodID(jEnv, jApp, "playUserSound", "(Ljava/lang/String;II)V");
    jSoundAmbience = (*jEnv)->GetMethodID(jEnv, jApp, "soundAmbience", "(III)V");
    jSoundVerbal = (*jEnv)->GetMethodID(jEnv, jApp, "soundVerbal", "(Ljava/lang/String;IIII)V");
}

// The NetHack thread never returns to Java while a game is running, so every
// local reference created here stays alive until the game ends unless it is
// deleted explicitly. String class, methods and charset are cached as global
// references so the conversions below only create the references they return.
static jclass jStringClass;
static jmethodID jStringInit;
static jmethodID jStringGetBytes;
static jstring jUtf8Charset;

static int initStringJni(JNIEnv *env) {
    if (jStringClass)
        return 1;
    jclass str_cls = (*env)->FindClass(env, "java/lang/String");
    jstring charset = (*env)->NewStringUTF(env, "utf-8");
    if (!str_cls || !charset) {
        (*env)->ExceptionClear(env);
        return 0;
    }
    jStringInit = (*env)->GetMethodID(env, str_cls, "<init>", "([BLjava/lang/String;)V");
    jStringGetBytes = (*env)->GetMethodID(env, str_cls, "getBytes", "(Ljava/lang/String;)[B");
    jUtf8Charset = (*env)->NewGlobalRef(env, charset);
    jStringClass = (*env)->NewGlobalRef(env, str_cls);
    (*env)->DeleteLocalRef(env, charset);
    (*env)->DeleteLocalRef(env, str_cls);
    return 1;
}

// Returns a new local reference (NULL for a NULL c_str); the caller must
// DeleteLocalRef it.
jstring char2Jstring(JNIEnv *env, const char *c_str) {
    if (c_str == NULL || !initStringJni(env))
        return NULL;
    int len = (int)strlen(c_str);
    jbyteArray bytes = (*env)->NewByteArray(env, len);
    if (!bytes)
        return NULL;
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (signed char *)c_str);
    jstring result = (*env)->NewObject(env, jStringClass, jStringInit, bytes, jUtf8Charset);
    (*env)->DeleteLocalRef(env, bytes);
    return result;
}

// Returns a malloc'd UTF-8 copy of jstr ("" for NULL or empty strings, never
// NULL); the caller must free() it.
char* jstring2Char(JNIEnv *env, jstring jstr)
{
    char* rtn = NULL;
    if (jstr && initStringJni(env)) {
        jbyteArray barr = (jbyteArray)(*env)->CallObjectMethod(env, jstr, jStringGetBytes, jUtf8Charset);
        if (barr) {
            jsize alen = (*env)->GetArrayLength(env, barr);
            rtn = (char*)malloc(alen + 1);
            (*env)->GetByteArrayRegion(env, barr, 0, alen, (jbyte *)rtn);
            rtn[alen] = 0;
            (*env)->DeleteLocalRef(env, barr);
        }
    }
    return rtn ? rtn : strdup("");
}