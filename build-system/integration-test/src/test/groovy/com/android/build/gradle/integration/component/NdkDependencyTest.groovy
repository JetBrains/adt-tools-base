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
import com.android.build.gradle.ndk.internal.NativeCompilerArgsUtil
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeLibrary
import com.android.builder.model.NativeSettings
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Test for dependencies on NDK projects.
 */
@CompileStatic
class NdkDependencyTest {
    private static final String[] ABIS =
            ["armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64", "mips", "mips64"];

    static AndroidTestApp prebuilt = new EmptyAndroidTestApp();
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

extern "C"
jstring
Java_com_example_hellojni_HelloJni_stringFromJNI(JNIEnv* env, jobject thiz)
{
    return env->NewStringUTF(getLib1String());
}
"""))

        app.addFile(new TestSourceFile("", "build.gradle", """
apply plugin: "com.android.model.application"

model {
    repositories {
        prebuilt(PrebuiltLibraries) {
            prebuilt {
                binaries.withType(SharedLibraryBinary) {
                    sharedLibraryFile = project.file("../../../prebuilt/build/outputs/native/debug/lib/\${targetPlatform.getName()}/libprebuilt.so")
                }
            }
        }
    }
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName = "hello-jni"
    }
    android.sources {
        main {
            jni {
                dependencies {
                    project ":lib1"
                    library "prebuilt"
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
        moduleName = "getstring1"
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
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        ndk {
            moduleName "getstring2"
            toolchain "clang"
            stl "stlport_shared"
        }
        sources {
            main {
                jni {
                    exportedHeaders {
                        srcDir "src/main/headers"
                    }
                }
            }
        }
    }
}
"""))

        // Subproject for creating prebuilt libraries.
        prebuilt.addFile(new TestSourceFile("src/main/jni/", "prebuilt.c", """
char* getPrebuiltString() {
    return "prebuilt";
}
"""))
        prebuilt.addFile(new TestSourceFile("", "build.gradle", """
apply plugin: "com.android.model.native"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    }
    android.ndk {
        moduleName = "prebuilt"
    }
}
"""))
    }

    @ClassRule
    public static GradleTestProject prebuiltProject = GradleTestProject.builder()
            .withName("prebuilt")
            .fromTestApp(prebuilt)
            .useExperimentalGradleVersion(true)
            .create()

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(base)
            .useExperimentalGradleVersion(true)
            .create()

    @BeforeClass
    public static void setUp() {
        // Create prebuilt libraries.
        prebuiltProject.execute("clean", "assembleDebug")
    }

    @AfterClass
    public static void cleanUp() {
        base = null
        prebuilt = null
        prebuiltProject = null
    }

    @Test
    void "check app contains compiled .so"() {

        project.execute("clean", ":app:assembleDebug")
        Map<String, AndroidProject> models = project.model().getMulti()
        GradleTestProject app = project.getSubproject("app")
        GradleTestProject lib1 = project.getSubproject("lib1")
        GradleTestProject lib2 = project.getSubproject("lib2")

        assertThat(models).containsKey(":app")

        AndroidProject model = models.get(":app")

        final File apk = project.getSubproject("app").getApk("debug")
        for (String abi : ABIS) {
            assertThatZip(apk).contains("lib/$abi/libhello-jni.so")
            assertThatZip(apk).contains("lib/$abi/libstlport_shared.so")
            assertThatZip(apk).contains("lib/$abi/libgetstring1.so")
            assertThatZip(apk).contains("lib/$abi/libgetstring2.so")
            assertThatZip(apk).contains("lib/$abi/libprebuilt.so")

            NativeLibrary libModel = findNativeLibraryByAbi(model, "debug", abi)
            assertThat(libModel).isNotNull();
            assertThat(libModel.getDebuggableLibraryFolders()).containsAllOf(
                    app.file("build/intermediates/binaries/debug/obj/$abi"),
                    lib1.file("build/intermediates/binaries/debug/obj/$abi"),
                    lib2.file("build/intermediates/binaries/debug/obj/$abi"),
            )
        }
    }

    @Test
    void "check static linkage"() {
        GradleTestProject lib1 = project.getSubproject("lib1")
        lib1.buildFile.delete()
        lib1.buildFile << """
apply plugin: "com.android.model.native"

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        ndk {
            moduleName "hello-jni"
        }
        sources {
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
}
"""
        project.executor().run("clean", ":app:assembleDebug");
        Map<String, NativeAndroidProject> models =
                project.model().getMulti(NativeAndroidProject.class);
        NativeAndroidProject model = models.get(":app")
        File apk = project.getSubproject("app").getApk("debug")
        for (String abi : ABIS) {
            assertThatZip(apk).contains("lib/$abi/libhello-jni.so")
            assertThatZip(apk).contains("lib/$abi/libstlport_shared.so")
            assertThatZip(apk).doesNotContain("lib/$abi/libget-string.so")

            // Check that the static library is compiled, but not the shared library.
            GradleTestProject lib2 = project.getSubproject("lib2")
            assertThat(lib2.file("build/intermediates/binaries/debug/obj/$abi/libgetstring2.a")).exists()
            assertThat(lib2.file("build/intermediates/binaries/debug/obj/$abi/libgetstring2.so")).doesNotExist()
        }
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings.getCompilerFlags())
                    .contains("-I" + lib1.file("src/main/headers").absolutePath.replace("\\", "\\\\"))
        }
    }

    @Test
    void "check update in lib triggers rebuild"() {
        project.execute("clean", ":app:assembleDebug")
        GradleTestProject app = project.getSubproject("app")
        GradleTestProject lib1 = project.getSubproject("lib1")
        GradleTestProject lib2 = project.getSubproject("lib2")

        File apk = project.getSubproject("app").getApk("debug")
        assertThatZip(apk).contains("lib/x86/libhello-jni.so")
        assertThatZip(apk).contains("lib/x86/libstlport_shared.so")

        lib2.file("src/main/jni/lib2.cpp") << "void foo() {}"

        File appSo = app.file("build/intermediates/binaries/debug/obj/x86/libhello-jni.so")
        File lib1So = lib1.file("build/intermediates/binaries/debug/obj/x86/libgetstring1.so")
        File lib2So = lib2.file("build/intermediates/binaries/debug/obj/x86/libgetstring2.so")

        long appModifiedTime = appSo.lastModified()
        long lib1ModifiedTime = lib1So.lastModified()
        long lib2ModifiedTime = lib2So.lastModified()

        project.execute(":app:assembleDebug")

        assertThat(lib2So).isNewerThan(lib2ModifiedTime)
        assertThat(lib1So).isNewerThan(lib1ModifiedTime)
        assertThat(appSo).isNewerThan(appModifiedTime)
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
