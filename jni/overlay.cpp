/**
 * overlay.cpp - liboverlay.so v1.0
 * Android system overlay via DEX bridge (JNI + WindowManager)
 * Compatible: any AML game (com.sampmobilerp.game, dll)
 *
 * Pola: manual __GetModInfo + OnModPreLoad + OnModLoad (tanpa MYMOD macro)
 */

#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>
#include <dlfcn.h>
#include <pthread.h>
#include <jni.h>
#include <android/log.h>

#define LOG_TAG  "liboverlay"
#define LOGFILE  "/storage/emulated/0/overlay_log.txt"
#define EXPORT   __attribute__((visibility("default")))

// ── Logger ───────────────────────────────────────────────────
static void logf_impl(const char* fmt, ...)
{
    char buf[512];
    va_list ap; va_start(ap, fmt); vsnprintf(buf, sizeof(buf), fmt, ap); va_end(ap);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", buf);
    FILE* f = fopen(LOGFILE, "a");
    if (f) { fprintf(f, "%s\n", buf); fclose(f); }
}
#define LOGI(...)  logf_impl(__VA_ARGS__)

// ── JVM ──────────────────────────────────────────────────────
static JavaVM* g_jvm = nullptr;

static JNIEnv* GetEnv()
{
    if (!g_jvm) return nullptr;
    JNIEnv* env = nullptr;
    int st = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (st == JNI_EDETACHED)
        g_jvm->AttachCurrentThread(&env, nullptr);
    return env;
}

// ── Native callbacks dipanggil dari Java DEX ─────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_brruham_overlay_OverlayBridge_nativeOnButton(JNIEnv* env, jclass, jstring id)
{
    const char* s = env->GetStringUTFChars(id, nullptr);
    LOGI("[Overlay] Button: %s", s);
    env->ReleaseStringUTFChars(id, s);
}

extern "C" JNIEXPORT void JNICALL
Java_com_brruham_overlay_OverlayBridge_nativeOnToggle(JNIEnv* env, jclass,
    jstring id, jboolean val)
{
    const char* s = env->GetStringUTFChars(id, nullptr);
    LOGI("[Overlay] Toggle %s = %d", s, (int)val);
    env->ReleaseStringUTFChars(id, s);
}

extern "C" JNIEXPORT void JNICALL
Java_com_brruham_overlay_OverlayBridge_nativeOnSlider(JNIEnv* env, jclass,
    jstring id, jfloat val)
{
    const char* s = env->GetStringUTFChars(id, nullptr);
    LOGI("[Overlay] Slider %s = %.2f", s, (float)val);
    env->ReleaseStringUTFChars(id, s);
}

extern "C" JNIEXPORT void JNICALL
Java_com_brruham_overlay_OverlayBridge_nativeOnText(JNIEnv* env, jclass,
    jstring id, jstring val)
{
    const char* s  = env->GetStringUTFChars(id,  nullptr);
    const char* sv = env->GetStringUTFChars(val, nullptr);
    LOGI("[Overlay] Text %s = %s", s, sv);
    env->ReleaseStringUTFChars(id,  s);
    env->ReleaseStringUTFChars(val, sv);
}

extern "C" JNIEXPORT void JNICALL
Java_com_brruham_overlay_OverlayBridge_nativeOnCheckbox(JNIEnv* env, jclass,
    jstring id, jboolean val)
{
    const char* s = env->GetStringUTFChars(id, nullptr);
    LOGI("[Overlay] Checkbox %s = %d", s, (int)val);
    env->ReleaseStringUTFChars(id, s);
}

// ── DEX Loader Thread ────────────────────────────────────────
struct LoadArgs {
    char dexPath[256];
    char pkg[128];
};

// Register native methods setelah class berhasil di-load
static void RegisterNatives(JNIEnv* env, jclass cls)
{
    JNINativeMethod methods[] = {
        {"nativeOnButton",   "(Ljava/lang/String;)V",
            (void*)Java_com_brruham_overlay_OverlayBridge_nativeOnButton},
        {"nativeOnToggle",   "(Ljava/lang/String;Z)V",
            (void*)Java_com_brruham_overlay_OverlayBridge_nativeOnToggle},
        {"nativeOnSlider",   "(Ljava/lang/String;F)V",
            (void*)Java_com_brruham_overlay_OverlayBridge_nativeOnSlider},
        {"nativeOnText",     "(Ljava/lang/String;Ljava/lang/String;)V",
            (void*)Java_com_brruham_overlay_OverlayBridge_nativeOnText},
        {"nativeOnCheckbox", "(Ljava/lang/String;Z)V",
            (void*)Java_com_brruham_overlay_OverlayBridge_nativeOnCheckbox},
    };
    int ret = env->RegisterNatives(cls, methods, 5);
    LOGI("[Overlay] RegisterNatives = %d", ret);
}

static void* LoadDexThread(void* arg)
{
    LoadArgs* args = (LoadArgs*)arg;
    JNIEnv* env = GetEnv();
    if (!env) { LOGI("[Overlay] JNIEnv null di thread"); delete args; return nullptr; }

    // 1. Dapatkan Context via ActivityThread
    jclass  atCls  = env->FindClass("android/app/ActivityThread");
    jmethodID curAT = env->GetStaticMethodID(atCls,
        "currentActivityThread", "()Landroid/app/ActivityThread;");
    jobject atObj  = env->CallStaticObjectMethod(atCls, curAT);
    jmethodID getApp = env->GetMethodID(atCls,
        "getApplication", "()Landroid/app/Application;");
    jobject appCtx = env->CallObjectMethod(atObj, getApp);

    if (!appCtx) { LOGI("[Overlay] Context null"); delete args; return nullptr; }
    LOGI("[Overlay] Context OK");

    // 2. DexClassLoader
    jclass    clCls = env->FindClass("dalvik/system/DexClassLoader");
    jmethodID clNew = env->GetMethodID(clCls, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
        "Ljava/lang/ClassLoader;)V");

    char optDir[256];
    snprintf(optDir, sizeof(optDir), "/data/data/%s/cache", args->pkg);

    // Parent classloader dari context
    jclass    ctxCls = env->FindClass("android/content/Context");
    jmethodID getCL  = env->GetMethodID(ctxCls,
        "getClassLoader", "()Ljava/lang/ClassLoader;");
    jobject   parentCL = env->CallObjectMethod(appCtx, getCL);

    jstring jDex = env->NewStringUTF(args->dexPath);
    jstring jOpt = env->NewStringUTF(optDir);

    jobject loader = env->NewObject(clCls, clNew,
        jDex, jOpt, (jstring)nullptr, parentCL);

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe(); env->ExceptionClear();
        LOGI("[Overlay] DexClassLoader gagal");
        delete args; return nullptr;
    }
    LOGI("[Overlay] DexClassLoader OK");

    // 3. Load class
    jmethodID loadCls = env->GetMethodID(clCls,
        "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring clsName = env->NewStringUTF("com.brruham.overlay.OverlayBridge");
    jclass  bridgeCls = (jclass)env->CallObjectMethod(loader, loadCls, clsName);

    if (!bridgeCls || env->ExceptionCheck()) {
        env->ExceptionDescribe(); env->ExceptionClear();
        LOGI("[Overlay] OverlayBridge class tidak ditemukan");
        delete args; return nullptr;
    }
    LOGI("[Overlay] OverlayBridge class OK");

    // 4. Register native callbacks
    RegisterNatives(env, bridgeCls);

    // 5. Panggil OverlayBridge.initSimple(Context)
    jmethodID initM = env->GetStaticMethodID(bridgeCls,
        "initSimple", "(Landroid/content/Context;)V");
    if (!initM || env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGI("[Overlay] initSimple method tidak ditemukan");
        delete args; return nullptr;
    }

    env->CallStaticVoidMethod(bridgeCls, initM, appCtx);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe(); env->ExceptionClear();
        LOGI("[Overlay] initSimple() threw exception");
    } else {
        LOGI("[Overlay] initSimple() OK — overlay aktif");
    }

    delete args;
    return nullptr;
}

// ── Cari JVM ────────────────────────────────────────────────
static void AcquireJVM()
{
    // Cara 1: alcGetJavaVM dari OpenAL embedded di libsamp.so
    typedef JavaVM* (*alcGetJavaVM_t)();

    void* samp = dlopen("libsamp.so", RTLD_NOLOAD | RTLD_NOW);
    if (samp) {
        auto fn = (alcGetJavaVM_t)dlsym(samp, "alcGetJavaVM");
        if (fn) { g_jvm = fn(); LOGI("[Overlay] JVM dari libsamp.so"); return; }
    }

    // Cara 2: libopenal.so terpisah
    void* al = dlopen("libopenal.so", RTLD_NOLOAD | RTLD_NOW);
    if (!al) al = dlopen("libopenal.so", RTLD_NOW);
    if (al) {
        auto fn = (alcGetJavaVM_t)dlsym(al, "alcGetJavaVM");
        if (fn) { g_jvm = fn(); LOGI("[Overlay] JVM dari libopenal.so"); return; }
    }

    // Cara 3: JNI_GetCreatedJavaVMs dari libart.so
    typedef jint (*GetVMs_t)(JavaVM**, jsize, jsize*);
    void* art = dlopen("libart.so", RTLD_NOLOAD | RTLD_NOW);
    if (art) {
        auto fn = (GetVMs_t)dlsym(art, "JNI_GetCreatedJavaVMs");
        if (fn) {
            jsize cnt = 0;
            fn(&g_jvm, 1, &cnt);
            if (g_jvm) { LOGI("[Overlay] JVM dari libart.so"); return; }
        }
    }

    LOGI("[Overlay] JVM tidak ditemukan!");
}

// ── AML Entry Points ─────────────────────────────────────────
extern "C" {

EXPORT void* __GetModInfo()
{
    static const char* info = "overlay|1.0|Android Overlay DEX Bridge|brruham";
    return (void*)info;
}

EXPORT void OnModPreLoad()
{
    remove(LOGFILE);
    LOGI("[Overlay] OnModPreLoad v1.0");
    AcquireJVM();
}

EXPORT void OnModLoad()
{
    LOGI("[Overlay] OnModLoad");

    if (!g_jvm) {
        LOGI("[Overlay] JVM null, abort");
        return;
    }

    // Cari DEX di beberapa path
    const char* paths[] = {
        "/storage/emulated/0/Android/data/com.sampmobilerp.game/files/overlay_bridge.dex",
        "/storage/emulated/0/Android/data/com.arizona.game.gtasa/files/overlay_bridge.dex",
        "/storage/emulated/0/overlay_bridge.dex",
        nullptr
    };

    const char* found = nullptr;
    for (int i = 0; paths[i]; i++) {
        FILE* f = fopen(paths[i], "rb");
        if (f) { fclose(f); found = paths[i]; break; }
    }

    if (!found) {
        LOGI("[Overlay] overlay_bridge.dex tidak ditemukan!");
        LOGI("[Overlay] Taruh di: /sdcard/Android/data/com.sampmobilerp.game/files/overlay_bridge.dex");
        return;
    }
    LOGI("[Overlay] DEX ditemukan: %s", found);

    LoadArgs* args = new LoadArgs();
    strncpy(args->dexPath, found, 255);

    if (strstr(found, "com.sampmobilerp.game"))
        strncpy(args->pkg, "com.sampmobilerp.game", 127);
    else if (strstr(found, "com.arizona.game.gtasa"))
        strncpy(args->pkg, "com.arizona.game.gtasa", 127);
    else
        strncpy(args->pkg, "com.sampmobilerp.game", 127);

    pthread_t tid;
    pthread_create(&tid, nullptr, LoadDexThread, args);
    pthread_detach(tid);

    LOGI("[Overlay] DEX loader thread spawned");
}

EXPORT void OnModUnload()
{
    LOGI("[Overlay] OnModUnload");
}

} // extern "C"
