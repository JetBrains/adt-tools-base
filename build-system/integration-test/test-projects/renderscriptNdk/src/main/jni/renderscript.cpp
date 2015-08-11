#include <android/bitmap.h>
#include <jni.h>
#include <RenderScript.h>

using namespace android::RSC;

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_android_rs_hellocompute_HelloCompute_mono(JNIEnv* env, jobject src, jobject dst) {
    // TODO: Implement this function to process the image.  For now, just make sure the project
    // compiles and the
}

}