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
import com.android.build.gradle.integration.common.utils.ImageHelper
import com.android.builder.model.AndroidProject
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Assemble tests for overlay3.
 */
class Overlay3Test {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("overlay3")
            .create()

    @BeforeClass
    static void setUp() {
        project.execute("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check image color"() {
        int GREEN = ImageHelper.GREEN
        int RED = ImageHelper.RED

        File drawableOutput = project.file(
                "build/" + AndroidProject.FD_INTERMEDIATES + "/res/merged/freebeta/debug/drawable")

        ImageHelper.checkImageColor(drawableOutput, "no_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "debug_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "beta_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "free_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "free_beta_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "free_beta_debug_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "free_normal_overlay.png", RED)

        drawableOutput = project.file(
                "build/" + AndroidProject.FD_INTERMEDIATES + "/res/merged/freenormal/debug/drawable")

        ImageHelper.checkImageColor(drawableOutput, "no_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "debug_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "beta_overlay.png", RED)
        ImageHelper.checkImageColor(drawableOutput, "free_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "free_beta_overlay.png", RED)
        ImageHelper.checkImageColor(drawableOutput, "free_beta_debug_overlay.png", RED)
        ImageHelper.checkImageColor(drawableOutput, "free_normal_overlay.png", GREEN)

        drawableOutput = project.file(
                "build/" + AndroidProject.FD_INTERMEDIATES + "/res/merged/paidbeta/debug/drawable")

        ImageHelper.checkImageColor(drawableOutput, "no_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "debug_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "beta_overlay.png", GREEN)
        ImageHelper.checkImageColor(drawableOutput, "free_overlay.png", RED)
        ImageHelper.checkImageColor(drawableOutput, "free_beta_overlay.png", RED)
        ImageHelper.checkImageColor(drawableOutput, "free_beta_debug_overlay.png", RED)
        ImageHelper.checkImageColor(drawableOutput, "free_normal_overlay.png", RED)
    }

    @Test
    void lint() {
        project.execute("lint")
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck()
    }
}
