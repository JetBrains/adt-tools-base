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

package com.android.build.gradle.integration.component

import com.android.SdkConstants
import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeLibrary
import com.android.builder.model.Variant
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Assemble tests for ndkSanAngeles2.
 */
@CompileStatic
class NdkSanAngeles2Test {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .forExperimentalPlugin(true)
            .fromTestProject("ndkSanAngeles2")
            .create()

    private static AndroidProject model;

    @BeforeClass
    static void setUp() {
        model = project.executeAndReturnModel("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void lint() {
        project.execute("lint")
    }

    @Test
    void "check model"() {
        Collection<Variant> variants = model.getVariants()
        assertThat(variants).hasSize(8)

        Variant debugVariant = ModelHelper.getVariant(variants, "x86Debug")
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact()
        assertThat(debugMainArtifact.getNativeLibraries()).hasSize(1)

        NativeLibrary nativeLibrary = debugMainArtifact.getNativeLibraries().first()
        assertThat(nativeLibrary.getName()).isEqualTo("sanangeles")
        assertThat(nativeLibrary.getToolchainName()).isEqualTo("clang-x86")
        assertThat(nativeLibrary.getCCompilerFlags()).contains("-DDISABLE_IMPORTGL");
        assertThat(nativeLibrary.getCSystemIncludeDirs()).isEmpty();
        assertThat(nativeLibrary.getCppSystemIncludeDirs()).isNotEmpty()
        File solibSearchPath = nativeLibrary.getDebuggableLibraryFolders().first()
        assertThat(new File(solibSearchPath, "libsanangeles.so")).exists()

        Collection<String> toolchainNames = model.getNativeToolchains().collect { it.getName() }
        Collection<String> expectedToolchains = [
                SdkConstants.ABI_INTEL_ATOM,
                SdkConstants.ABI_ARMEABI_V7A,
                SdkConstants.ABI_ARMEABI,
                SdkConstants.ABI_MIPS].collect { "clang-" + it }
        assertThat(toolchainNames).containsAllIn(expectedToolchains)
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck();
    }
}
