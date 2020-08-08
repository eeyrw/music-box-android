#include <jni.h>
#include <string>
#include <vector>
#include "MusicBoxEngine.h"


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_music_1box_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {

#if defined(__aarch64__) || defined(__x86_64__)
    std::string hello = "Hello from Native Code Music Box with 64bit CPU";
#else
    std::string hello = "Hello from Native Code Music Box with 32bit CPU";
#endif
    return env->NewStringUTF(hello.c_str());
}

std::vector<int> convertJavaArrayToVector(JNIEnv *env, jintArray intArray) {
    std::vector<int> v;
    jsize length = env->GetArrayLength(intArray);
    if (length > 0) {
        jint *elements = env->GetIntArrayElements(intArray, nullptr);
        v.insert(v.end(), &elements[0], &elements[length]);
        // Unpin the memory for the array, or free the copy.
        env->ReleaseIntArrayElements(intArray, elements, 0);
    }
    return v;
}

extern "C" {
/**
 * Start the audio engine
 *
 * @param env
 * @param instance
 * @param jCpuIds - CPU core IDs which the audio process should affine to
 * @return a pointer to the audio engine. This should be passed to other methods
 */
JNIEXPORT jlong JNICALL
Java_com_example_music_1box_MainActivity_createEngine(JNIEnv *env, jobject /*unused*/,
                                                                jintArray jCpuIds) {
    std::vector<int> cpuIds = convertJavaArrayToVector(env, jCpuIds);
    LOGD("cpu ids size: %d", static_cast<int>(cpuIds.size()));
    MusicBoxEngine  *engine = new MusicBoxEngine(std::move(cpuIds));
    LOGD("Engine Started");
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_example_music_1box_MainActivity_deleteEngine(JNIEnv *env, jobject instance,
                                                               jlong jEngineHandle) {
    auto engine = reinterpret_cast<MusicBoxEngine*>(jEngineHandle);
    if (engine) {
        delete engine;
    } else {
        LOGD("Engine invalid, call startEngine() to create");
    }
}


JNIEXPORT void JNICALL
Java_com_example_music_1box_MainActivity_tap(JNIEnv *env, jobject instance,
                                                        jlong jEngineHandle, jboolean isDown) {

    auto *engine = reinterpret_cast<MusicBoxEngine*>(jEngineHandle);
    if (engine) {
        engine->tap(isDown);
    } else {
        LOGE("Engine handle is invalid, call createEngine() to create a new one");
    }
}

JNIEXPORT void JNICALL
Java_com_example_music_1box_MainActivity_native_1setDefaultStreamValues(JNIEnv *env,
                                                                                   jclass type,
                                                                                   jint sampleRate,
                                                                                   jint framesPerBurst) {
    oboe::DefaultStreamValues::SampleRate = (int32_t) sampleRate;
    oboe::DefaultStreamValues::FramesPerBurst = (int32_t) framesPerBurst;
}

} // extern "C"

extern "C"
JNIEXPORT void JNICALL
Java_com_example_music_1box_MainActivity_noteOn(JNIEnv *env, jobject thiz, jlong engine_handle,
                                                jint note) {
    auto *engine = reinterpret_cast<MusicBoxEngine *>(engine_handle);
    if (engine) {
        engine->noteOn(static_cast<uint8_t>(note));
    } else {
        LOGE("Engine handle is invalid, call createEngine() to create a new one");
    }
}extern "C"
JNIEXPORT void JNICALL
Java_com_example_music_1box_MainActivity_pause(JNIEnv *env, jobject thiz, jlong engine_handle,
                                               jboolean is_pause) {
    auto *engine = reinterpret_cast<MusicBoxEngine *>(engine_handle);
    if (engine) {
        engine->pause(static_cast<bool>(is_pause));
    } else {
        LOGE("Engine handle is invalid, call createEngine() to create a new one");
    }
}