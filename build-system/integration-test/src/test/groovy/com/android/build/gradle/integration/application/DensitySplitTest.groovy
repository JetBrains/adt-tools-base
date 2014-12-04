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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ApkHelper
import com.android.build.gradle.integration.common.utils.ZipHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.google.common.collect.ImmutableSet
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static junit.framework.Assert.assertEquals

/**
 * Assemble tests for densitySplit.
 */
class DensitySplitTest {

    static AndroidProject model;

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("densitySplit")
            .create()

    @BeforeClass
    static void setup() {
        model = project.executeAndReturnModel("clean", "assembleDebug");
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
    void testPackaging() {
        for (Variant variant : model.getVariants()) {
            AndroidArtifact mainArtifact = variant.getMainArtifact();
            if (!variant.getBuildType().equalsIgnoreCase("Debug")) {
                continue
            }
            assertEquals(5, mainArtifact.getOutputs().size())

            Map<String, String> filesToMatch = Collections.singletonMap(
                    "res/drawable-mdpi-v4/other.png", null)
            for (AndroidArtifactOutput output : mainArtifact.getOutputs()) {
                ZipHelper.checkArchive(output.mainOutputFile.getOutputFile(),
                        filesToMatch,
                        ImmutableSet.<String>of())
            }
        }
    }

    @Test
    void "check version code"() {
        ApkHelper.checkVersion(project.getApk("universal", "debug"), 112, "version 112")
        ApkHelper.checkVersion(project.getApk("mdpi", "debug"), 212, "version 212")
        ApkHelper.checkVersion(project.getApk("hdpi", "debug"), 312, "version 312")
        ApkHelper.checkVersion(project.getApk("xhdpi", "debug"), 412, "version 412")
        ApkHelper.checkVersion(project.getApk("xxhdpi", "debug"), 512, "version 512")
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.execute("connectedCheck");
    }
}
