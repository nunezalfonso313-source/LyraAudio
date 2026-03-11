#include <jni.h>
#include <string>
#include "lyra_kernel.cpp"

extern "C" JNIEXPORT jstring JNICALL
Java_com_lyra_audiokernel_MainActivity_stringFromJNI(JNIEnv* env, jobject) {
    LyraPlayer player;
    return env->NewStringUTF(player.getStatus().c_str());
}
