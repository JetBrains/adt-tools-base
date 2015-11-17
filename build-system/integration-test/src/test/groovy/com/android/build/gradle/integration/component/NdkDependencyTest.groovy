/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeLibrary
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Test for dependencies on NDK projects.
 */
@CompileStatic
class NdkDependencyTest {
    static MultiModuleTestProject base = new MultiModuleTestProject(
            app: new HelloWorldJniApp(),
            lib1: new EmptyAndroidTestApp(),
            lib2: new EmptyAndroidTestApp())

    static {
        AndroidTestApp app = (HelloWorldJniApp) base.getSubproject("app")

        app.removeFile(app.getFile("hello-jni.c"))
        app.addFile(new TestSourceFile("src/main/jni", "hello-jni.cpp",
                """
#include <string.h>
#include <jni.h>
#include "lib1.h"

jstring
Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, getLib1String());
}
"""));

        app.addFile(new TestSourceFile("", "build.gradle", """
apply plugin: "com.android.model.application"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.sources {
        main {
            jniLibs {
                dependencies {
                    project ":lib1"
                }
            }
        }
    }
}
"""))

        AndroidTestApp lib1 = (AndroidTestApp) base.getSubproject("lib1")
        lib1.addFile(new TestSourceFile("src/main/headers/", "lib1.h", """
#ifndef INCLUDED_LIB1_H
#define INCLUDED_LIB1_H

char* getLib1String();

#endif
"""))
        lib1.addFile(new TestSourceFile("src/main/jni/", "lib1.cpp", """
#include "lib1.h"
#include "lib2.h"

char* getLib1String() {
    return getLib2String();
}
"""))
        lib1.addFile(new TestSourceFile("", "build.gradle", """
apply plugin: "com.android.model.native"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    }
    android.ndk {
        moduleName = "hello-jni"
    }
    android.sources {
        main {
            jni {
                dependencies {
                    project ":lib2"
                }
                exportedHeaders {
                    srcDir "src/main/headers"
                }
            }
        }
    }
}
"""))

        AndroidTestApp lib2 = (AndroidTestApp) base.getSubproject("lib2")
        lib2.addFile(new TestSourceFile("src/main/headers/", "lib2.h", """
#ifndef INCLUDED_LIB2_H
#define INCLUDED_LIB2_H

char* getLib2String();

#endif
"""))
        // Add c++ file that uses function from the STL.
        lib2.addFile(new TestSourceFile("src/main/jni/", "lib2.cpp", """
#include "lib2.h"
#include <algorithm>
#include <cstring>
#include <cctype>

char* getLib2String() {
    char* greeting = new char[32];
    std::strcpy(greeting, "HELLO WORLD!");
    std::transform(greeting, greeting + strlen(greeting), greeting, std::tolower);
    return greeting;  // memory leak if greeting is not deallocated, but doesn't matter.
}
"""))
        lib2.addFile(new TestSourceFile("", "build.gradle", """
apply plugin: "com.android.model.native"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    }
    android.ndk {
        moduleName = "get-string"
        stl = "stlport_shared"
    }
    android.sources {
        main {
            jni {
                exportedHeaders {
                    srcDir "src/main/headers"
                }
            }
        }
    }
}
"""))
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(base)
            .forExperimentalPlugin(true)
            .create()

    @AfterClass
    static void cleanUp() {
        base = null
    }

    @Test
    void "check app contains compiled .so"() {
        Map<String, AndroidProject> models =
                project.executeAndReturnMultiModel("clean", ":app:assembleDebug");
        GradleTestProject app = project.getSubproject("app");
        GradleTestProject lib1 = project.getSubproject("lib1");
        GradleTestProject lib2 = project.getSubproject("lib2");

        assertThat(models).containsKey(":app")

        AndroidProject model = models.get(":app")

        File apk = project.getSubproject("app").getApk("debug")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so")
        assertThatZip(apk).contains("lib/x86/libstlport_shared.so")
        assertThatZip(apk).contains("lib/x86/libget-string.so")

        NativeLibrary libModel = findNativeLibraryByAbi(model, "debug", "x86");
        assertThat(libModel.getDebuggableLibraryFolders()).containsAllOf(
                app.file("build/intermediates/binaries/debug/obj/x86"),
                lib1.file("build/intermediates/binaries/debug/obj/x86"),
                lib2.file("build/intermediates/binaries/debug/obj/x86"),
        )
    }

    @Test
    void "check static linkage"() {
        GradleTestProject lib1 = project.getSubproject("lib1")
        lib1.buildFile.delete()
        lib1.buildFile << """
apply plugin: "com.android.model.native"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    }
    android.ndk {
        moduleName = "hello-jni"
    }
    android.sources {
        main {
            jni {
                dependencies {
                    project ":lib2" linkage "static"
                }
                exportedHeaders {
                    srcDir "src/main/headers"
                }
            }
        }
    }
}
"""
        project.execute("clean", ":app:assembleDebug")
        File apk = project.getSubproject("app").getApk("debug")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so")
        assertThatZip(apk).contains("lib/x86/libstlport_shared.so")
        assertThatZip(apk).doesNotContain("lib/x86/libget-string.so")

        // Check that the static library is compiled, but not the shared library.
        GradleTestProject lib2 = project.getSubproject("lib2")
        assertThat(lib2.file("build/intermediates/binaries/debug/obj/x86/libget-string.a")).exists()
        assertThat(lib2.file("build/intermediates/binaries/debug/obj/x86/libget-string.so")).doesNotExist()
    }

    private static NativeLibrary findNativeLibraryByAbi(
            AndroidProject model,
            String variantName,
            String abi) {
        AndroidArtifact artifact =
                ModelHelper.getVariant(model.getVariants(), variantName).getMainArtifact()
        return artifact.getNativeLibraries().find { it.abi == abi }
    }
}
