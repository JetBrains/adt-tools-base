/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle

import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.test.BaseTest
import com.android.build.gradle.internal.variant.BaseVariantData
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

import static com.android.build.gradle.DslTestUtil.DEFAULT_VARIANTS
import static com.google.common.truth.Truth.assertThat

/**
 * Test variant dependencies.
 */
public class DependencyTest extends BaseTest {

    @Override
    protected void setUp() throws Exception {
        SdkHandler.setTestSdkFolder(new File(System.getenv("ANDROID_HOME")));
    }

    public void testProvidedDependency() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "${FOLDER_TEST_PROJECTS}/basic")).build()

        File providedJar = File.createTempFile("provided", ".jar")
        providedJar.createNewFile();
        providedJar.deleteOnExit()

        File debugJar = File.createTempFile("providedDebug", ".jar")
        debugJar.createNewFile();
        debugJar.deleteOnExit()


        project.apply plugin: 'com.android.application'

        println providedJar.isFile()
        project.android {
            compileSdkVersion COMPILE_SDK_VERSION
            buildToolsVersion BUILD_TOOL_VERSION
        }
        project.dependencies {
            provided project.files(providedJar)
            debugProvided project.files(debugJar)
        }

        AppPlugin plugin = project.plugins.getPlugin(AppPlugin)

        plugin.createAndroidTasks(false)
        assertEquals(DEFAULT_VARIANTS.size(), plugin.variantManager.variantDataList.size())
        BaseVariantData variantData =
                plugin.variantManager.variantDataList.find {it -> it.name == "release"}
        assertThat(variantData.getVariantConfiguration().getProvidedOnlyJars()).containsExactly(providedJar)

        variantData = plugin.variantManager.variantDataList.find {it -> it.name == "debug"}
        assertThat(variantData.getVariantConfiguration().getProvidedOnlyJars()).containsExactly(providedJar, debugJar)
    }
}
