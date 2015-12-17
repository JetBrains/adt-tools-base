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

package com.android.build.gradle.integration.ndk

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeLibrary
import com.android.builder.model.Variant
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Test the return model of the NDK.
 */
class NdkModelTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .addGradleProperties("android.useDeprecatedNdk=true")
            .create()

    @Before
    void setUp() {
        project.buildFile <<
"""
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    defaultConfig {
        ndk {
            moduleName "hello-jni"
            cFlags = "-DTEST_FLAG"
        }
    }
}
"""
    }

    @Test
    void "check native libraries in model"() {
        checkModel(
                debug : [
                    SdkConstants.ABI_ARMEABI,
                    SdkConstants.ABI_ARMEABI_V7A,
                    SdkConstants.ABI_ARM64_V8A,
                    SdkConstants.ABI_INTEL_ATOM,
                    SdkConstants.ABI_INTEL_ATOM64,
                    SdkConstants.ABI_MIPS,
                    SdkConstants.ABI_MIPS64
                ]);
    }

    @Test
    void "check native libraries with splits"() {
        project.buildFile <<
"""
android {
    splits {
        abi {
            enable true
            reset()
            include 'x86', 'armeabi-v7a', 'mips'
        }
    }
}
"""
        checkModel(
                debug: [SdkConstants.ABI_ARMEABI_V7A, SdkConstants.ABI_INTEL_ATOM, SdkConstants.ABI_MIPS]);
    }

    @Test
    void "check native libraries with splits and universalApk"() {
        project.buildFile <<
                """
android {
    splits {
        abi {
            enable true
            reset()
            include 'x86', 'armeabi-v7a', 'mips'
            universalApk true
        }
    }
}
"""
        checkModel(
                debug : [
                        SdkConstants.ABI_ARMEABI,
                        SdkConstants.ABI_ARMEABI_V7A,
                        SdkConstants.ABI_ARM64_V8A,
                        SdkConstants.ABI_INTEL_ATOM,
                        SdkConstants.ABI_INTEL_ATOM64,
                        SdkConstants.ABI_MIPS,
                        SdkConstants.ABI_MIPS64
                ]);
    }

    @Test
    void "check native libraries with abiFilters"() {
        project.buildFile <<
                """
android {
    productFlavors {
        x86 {
            ndk {
                abiFilter "x86"
            }
        }
        arm {
            ndk {
                abiFilters "armeabi-v7a"
            }
        }
        mips {
            ndk {
                abiFilter "mips"
            }
        }
    }
}
"""
        checkModel(
                x86Debug : [SdkConstants.ABI_INTEL_ATOM],
                armDebug : [SdkConstants.ABI_ARMEABI_V7A],
                mipsDebug : [SdkConstants.ABI_MIPS]);
    }

    @Test
    void "check using add on string for compileSdkVersion"() {
        project.buildFile <<
"""
android {
    compileSdkVersion "Google Inc.:Google APIs:$GradleTestProject.DEFAULT_COMPILE_SDK_VERSION"
}
"""
        AndroidProject model = project.executeAndReturnModel("assembleDebug")
        NativeLibrary lib = ModelHelper.getVariant(model.getVariants(), "debug").getMainArtifact()
                .getNativeLibraries().first()
        for (String flag : lib.getCCompilerFlags()) {
            if (flag.contains("sysroot")) {
                assertThat(flag).contains("android-${GradleTestProject.LATEST_NDK_VERSION}")
            }
        }
    }

    /**
     * Verify resulting model is as expected.
     *
     * @param variantToolchains map of variant name to array of expected toolchains.
     */
    private void checkModel(Map variantToolchains) {

        AndroidProject model = project.executeAndReturnModel("assembleDebug")

        Collection<Variant> variants = model.getVariants()
        for (Map.Entry entry : variantToolchains) {
            Variant variant = ModelHelper.getVariant(variants, (String) entry.getKey())
            AndroidArtifact mainArtifact = variant.getMainArtifact()

            assertThat(mainArtifact.getNativeLibraries()).hasSize(((Collection)entry.getValue()).size())
            for (NativeLibrary nativeLibrary : mainArtifact.getNativeLibraries()) {
                assertThat(nativeLibrary.getName()).isEqualTo("hello-jni")
                assertThat(nativeLibrary.getCCompilerFlags()).contains("-DTEST_FLAG");
                assertThat(nativeLibrary.getCppCompilerFlags()).contains("-DTEST_FLAG");
                assertThat(nativeLibrary.getCSystemIncludeDirs()).isEmpty();
                assertThat(nativeLibrary.getCppSystemIncludeDirs()).isNotEmpty();
                File solibSearchPath = nativeLibrary.getDebuggableLibraryFolders().first()
                assertThat(new File(solibSearchPath, "libhello-jni.so")).exists()
            }

            Collection<String> expectedToolchainNames = entry.getValue().collect { "gcc-" + it }
            Collection<String> toolchainNames = model.getNativeToolchains().collect { it.getName() }
            assertThat(toolchainNames).containsAllIn(expectedToolchainNames)
            Collection<String> nativeLibToolchains = mainArtifact.getNativeLibraries().
                    collect { it.getToolchainName() }
            assertThat(nativeLibToolchains).containsExactlyElementsIn(expectedToolchainNames)
        }

    }
}
