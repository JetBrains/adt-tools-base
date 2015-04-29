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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip
import static org.junit.Assert.assertEquals

/**
 * MultiAPK test where densities are obtained automatically.
 */
class AutoDensitySplitTest {
    static AndroidProject model

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("densitySplit")
            .create()

    @BeforeClass
    static void setUp() {
        project.getBuildFile() << """android {
          splits {
            density {
              enable true
              auto true
              compatibleScreens 'small', 'normal', 'large', 'xlarge'
            }
          }
        }"""
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
    void testPackaging() {
        for (Variant variant : model.getVariants()) {
            AndroidArtifact mainArtifact = variant.getMainArtifact()
            if (!variant.getBuildType().equalsIgnoreCase("Debug")) {
                continue
            }
            assertEquals(5, mainArtifact.getOutputs().size())

            for (AndroidArtifactOutput output : mainArtifact.getOutputs()) {
                assertThatZip(output.getMainOutputFile().getOutputFile())
                        .contains("res/drawable-mdpi-v4/other.png")
            }
        }
    }

    @Test
    void "check version code in apk"() {
        File universalApk = project.getApk("universal", "debug")
        assertThatApk(universalApk).hasVersionCode(112)
        assertThatApk(universalApk).hasVersionName("version 112")

        File mdpiApk = project.getApk("mdpi", "debug")
        assertThatApk(mdpiApk).hasVersionCode(212)
        assertThatApk(mdpiApk).hasVersionName("version 212")

        File hdpiApk = project.getApk("hdpi", "debug")
        assertThatApk(hdpiApk).hasVersionCode(312)
        assertThatApk(hdpiApk).hasVersionName("version 312")

        File xhdpiApk = project.getApk("xhdpi", "debug")
        assertThatApk(xhdpiApk).hasVersionCode(412)
        assertThatApk(xhdpiApk).hasVersionName("version 412")

        File xxhdiApk = project.getApk("xxhdpi", "debug")
        assertThatApk(xxhdiApk).hasVersionCode(512)
        assertThatApk(xxhdiApk).hasVersionName("version 512")
    }
}
