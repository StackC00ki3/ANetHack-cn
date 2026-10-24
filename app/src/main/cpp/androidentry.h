
#ifndef ANETHACK_ANDROIDENTRY_H
#define ANETHACK_ANDROIDENTRY_H

#include <jni.h>

// jni env
extern JNIEnv* jEnv;
extern jclass jApp;
extern jobject jAppInstance;

// NHPosCommand.PosMod ordinal carried through requirePosKeyCommand()
#define AND_POS_NONE   0
#define AND_POS_TRAVEL 1
#define AND_POS_LOOK   2
#define AND_POS_SELF   3
#define AND_POS_TRAVEL_TO 4
// window
extern jmethodID jRequireKeyCommand;
extern jmethodID jRequirePosKeyCommand;
extern jmethodID jCreateWindow;
extern jmethodID jDisplayWindow;
extern jmethodID jClearWindow;
extern jmethodID jDestroyWindow;
extern jmethodID jPutString;
extern jmethodID jRawPrint;
extern jmethodID jCurs;
extern jmethodID jPrintTile;
extern jmethodID jYNFunction;
extern jmethodID jSetDirectionPrompt;
extern jmethodID jGetLine;
extern jmethodID jStartMenu;
extern jmethodID jAddMenu;
extern jmethodID jEndMenu;
extern jmethodID jSelectMenu;
extern jmethodID jClipAround;
extern jmethodID jDelayOutput;
extern jmethodID jSetNumPadOption;
extern jmethodID jAskName;
extern jmethodID jRenderStatus;
extern jmethodID jShowExtCmdMenu;
extern jmethodID jGetMessageHistory;
extern jmethodID jPutMessageHistory;
extern jmethodID jIsAutoSave;
// sound
extern jmethodID jInitNHSound;
extern jmethodID jExitNHSound;
extern jmethodID jSoundAchievement;
extern jmethodID jSoundEffect;
extern jmethodID jHeroPlayNotes;
extern jmethodID jPlayUserSound;
extern jmethodID jSoundAmbience;
extern jmethodID jSoundVerbal;
// string
char * jstring2Char(JNIEnv *env, jstring jstr);
jstring char2Jstring(JNIEnv *env, const char *c_str);
// aborts with a crash report if the last callback threw a Java exception
void and_check_jni_exception(const char *method);

#define JNICallV(func, ...) ({ (*jEnv)->CallVoidMethod(jEnv, jAppInstance, func, ## __VA_ARGS__); \
        and_check_jni_exception(#func); });
#define JNICallI(func, ...) ({ jint jni_ret_ = (*jEnv)->CallIntMethod(jEnv, jAppInstance, func, ## __VA_ARGS__); \
        and_check_jni_exception(#func); jni_ret_; });
#define JNICallO(func, ...) ({ jobject jni_ret_ = (*jEnv)->CallObjectMethod(jEnv, jAppInstance, func, ## __VA_ARGS__); \
        and_check_jni_exception(#func); jni_ret_; });
#define JNICallC(func, ...) ({ jchar jni_ret_ = (*jEnv)->CallCharMethod(jEnv, jAppInstance, func, ## __VA_ARGS__); \
        and_check_jni_exception(#func); jni_ret_; });
#define JNICallZ(func, ...) ({ jboolean jni_ret_ = (*jEnv)->CallBooleanMethod(jEnv, jAppInstance, func, ## __VA_ARGS__); \
        and_check_jni_exception(#func); jni_ret_; });

#endif //ANETHACK_ANDROIDENTRY_H
