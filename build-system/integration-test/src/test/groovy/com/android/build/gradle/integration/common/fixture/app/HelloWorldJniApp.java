/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.integration.common.fixture.app;

import com.android.annotations.NonNull;

/**
 * Simple test application that uses JNI to print a "hello world!".
 *
 * NOTE: Android project must create an NDK module named "hello-jni".
 */
public class HelloWorldJniApp extends AbstractAndroidTestApp implements AndroidTestApp {

    private static final TestSourceFile javaSource = new TestSourceFile(
            "src/main/java/com/example/hellojni", "HelloJni.java",
"package com.example.hellojni;\n" +
"\n" +
"import android.app.Activity;\n" +
"import android.widget.TextView;\n" +
"import android.os.Bundle;\n" +
"\n" +
"public class HelloJni extends Activity {\n" +
"    /** Called when the activity is first created. */\n" +
"    @Override\n" +
"    public void onCreate(Bundle savedInstanceState) {\n" +
"        super.onCreate(savedInstanceState);\n" +
"\n" +
"        // Create a TextView and set its content from a native function.\n" +
"        TextView  tv = new TextView(this);\n" +
"        tv.setText( stringFromJNI() );\n" +
"        setContentView(tv);\n" +
"    }\n" +
"\n" +
"    // A native method that is implemented by the 'hello-jni' native library.\n" +
"    public native String  stringFromJNI();\n" +
"\n" +
"    static {\n" +
"        System.loadLibrary(\"hello-jni\");\n" +
"    }\n" +
"}\n");

    // JNI Implementation in C.
    private static final TestSourceFile cSource = new TestSourceFile(
            "src/main/jni", "hello-jni.c",
"#include <string.h>\n" +
"#include <jni.h>\n" +
"\n" +
"// This is a trivial JNI example where we use a native method\n" +
"// to return a new VM String.\n" +
"jstring\n" +
"Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz)\n" +
"{\n" +
"    return (*env)->NewStringUTF(env, \"hello world!\");\n" +
"}\n");

    // JNI Implementation in C++.\n" +
    private static final TestSourceFile cppSource = new TestSourceFile(
            "src/main/jni", "hello-jni.cpp",
"#include <string.h>\n" +
"#include <jni.h>\n" +
"#include <cctype>\n" +
"\n" +
"// This is a trivial JNI example where we use a native method\n" +
"// to return a new VM String.\n" +
"extern \"C\"\n" +
"jstring\n" +
"Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz)\n" +
"{\n" +
"    char greeting[] = \"HELLO WORLD!\";\n" +
"    char* ptr = greeting;\n" +
"    while (*ptr) {\n" +
"        *ptr = std::tolower(*ptr);\n" +
"        ++ptr;\n" +
"    }\n" +
"    return env->NewStringUTF(greeting);\n" +
"}\n");


    private static final TestSourceFile resSource =
            new TestSourceFile("src/main/res/values", "strings.xml",
"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
"<resources>\n" +
"    <string name=\"app_name\">HelloJni</string>\n" +
"</resources>\n");

    private static final TestSourceFile manifest = new TestSourceFile(
            "src/main", "AndroidManifest.xml",
"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
"<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
"      package=\"com.example.hellojni\"\n" +
"      android:versionCode=\"1\"\n" +
"      android:versionName=\"1.0\">\n" +
"\n" +
"    <uses-sdk android:minSdkVersion=\"3\" />\n" +
"    <application android:label=\"@string/app_name\">\n" +
"        <activity android:name=\".HelloJni\"\n" +
"                  android:label=\"@string/app_name\">\n" +
"            <intent-filter>\n" +
"                <action android:name=\"android.intent.action.MAIN\" />\n" +
"                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
"            </intent-filter>\n" +
"        </activity>\n" +
"    </application>\n" +
"</manifest>\n");

    private static final TestSourceFile androidTestSource = new TestSourceFile(
            "src/androidTest/java/com/example/hellojni", "HelloJniTest.java",
"package com.example.hellojni;\n" +
"\n" +
"import android.test.ActivityInstrumentationTestCase;\n" +
"\n" +
"/**\n" +
" * This is a simple framework for a test of an Application.  See\n" +
" * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on\n" +
" * how to write and extend Application tests.\n" +
" * <p/>\n" +
" * To run this test, you can type:\n" +
" * adb shell am instrument -w \\n" +
" * -e class com.example.hellojni.HelloJniTest \\n" +
" * com.example.hellojni.tests/android.test.InstrumentationTestRunner\n" +
" */\n" +
"public class HelloJniTest extends ActivityInstrumentationTestCase<HelloJni> {\n" +
"\n" +
"    public HelloJniTest() {\n" +
"        super(\"com.example.hellojni\", HelloJni.class);\n" +
"    }\n" +
"\n" +
"\n" +
"    public void testJniName() {\n" +
"        final HelloJni a = getActivity();\n" +
"        // ensure a valid handle to the activity has been returned\n" +
"        assertNotNull(a);\n" +
"\n" +
"        assertTrue(\"hello world!\".equals(a.stringFromJNI()));\n" +
"    }\n" +
"}\n");

    public static final TestSourceFile androidMkC(String folder) {
        return new TestSourceFile(
                folder, "Android.mk",
                "LOCAL_PATH := $(call my-dir)\n"
                        + "\n"
                        + "include $(CLEAR_VARS)\n"
                        + "\n"
                        + "LOCAL_MODULE    := hello-jni\n"
                        + "LOCAL_SRC_FILES := hello-jni.c\n"
                        + "\n"
                        + "include $(BUILD_SHARED_LIBRARY)");
    }

    public static final TestSourceFile androidMkCpp(String folder) {
        return new TestSourceFile(
                folder, "Android.mk",
                "LOCAL_PATH := $(call my-dir)\n"
                        + "\n"
                        + "include $(CLEAR_VARS)\n"
                        + "\n"
                        + "LOCAL_MODULE    := hello-jni\n"
                        + "LOCAL_SRC_FILES := hello-jni.cpp\n"
                        + "\n"
                        + "include $(BUILD_SHARED_LIBRARY)");
    }

    public static final TestSourceFile applicationMk(String folder) {
        return new TestSourceFile(
                folder, "Application.mk",
                "NDK_TOOLCHAIN_VERSION:=clang\n");
    }

    public static final TestSourceFile cmakeLists(String folder) {
        return new TestSourceFile(
                folder, "CMakeLists.txt",
                "cmake_minimum_required(VERSION 3.4.1)\n" +
                        "\n" +
                        "# Compile all source files under this tree into a single shared library\n" +
                        "file(GLOB_RECURSE SRC src/*.c src/*.cpp src/*.cc src/*.cxx src/*.c++ src/*.C)\n" +
                        "message(\"${SRC}\")\n" +
                        "set(CMAKE_VERBOSE_MAKEFILE ON)\n" +
                        "add_library(hello-jni SHARED ${SRC})\n" +
                        "\n" +
                        "# Include a nice standard set of libraries to link against by default\n" +
                        "target_link_libraries(hello-jni log)");
    }

    public HelloWorldJniApp() {
        this("jni", false);
    }

    HelloWorldJniApp(String jniDir, boolean useCppSource) {
        TestSourceFile jniSource = useCppSource ? cppSource : cSource;
        addFiles(
                javaSource,
                new TestSourceFile("src/main/" + jniDir, jniSource.getName(), jniSource.getContent()),
                resSource,
                manifest,
                androidTestSource);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String jniDir = "jni";
        private boolean useCppSource = false;

        public Builder withNativeDir(@NonNull String jniDir) {
            this.jniDir = jniDir;
            return this;
        }

        public Builder useCppSource() {
            this.useCppSource = true;
            return this;
        }

        public Builder useCppSource(boolean useCppSource) {
            this.useCppSource = useCppSource;
            return this;
        }

        public HelloWorldJniApp build() {
            return new HelloWorldJniApp(jniDir, useCppSource);
        }
    }
}
