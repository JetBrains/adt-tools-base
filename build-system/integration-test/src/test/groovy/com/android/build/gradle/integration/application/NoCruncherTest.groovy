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
import org.junit.ClassRule
import org.junit.Test

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES
import static junit.framework.Assert.assertEquals
import static junit.framework.Assert.assertNotSame
import static junit.framework.Assert.assertTrue

/**
 * Integration test for the cruncherEnabled settings.
 */
class NoCruncherTest {

    @ClassRule
    static public GradleTestProject noPngCrunch = GradleTestProject.builder()
            .withName("noPngCrunch")
            .fromSample("noPngCrunch")
            .create()

    @Test
    void "test png files were not crunched"() {
        noPngCrunch.execute("clean", "assembleDebug")

        File srcFile = noPngCrunch.file("src/main/res/drawable/icon.png")
        File destFile = noPngCrunch.file("build/" + FD_INTERMEDIATES + "/res/debug/drawable/icon.png")

        // assert size are unchanged.
        assertTrue(srcFile.exists())
        assertTrue(destFile.exists())
        assertEquals(srcFile.length(), destFile.length())

        // check the png files is changed.
        srcFile = noPngCrunch.file("src/main/res/drawable/lib_bg.9.png")
        destFile = noPngCrunch.file("build/" + FD_INTERMEDIATES + "/res/debug/drawable/lib_bg.9.png")

        // assert size are changed.
        assertTrue(srcFile.exists())
        assertTrue(destFile.exists())
        assertNotSame(srcFile.length(), destFile.length())
    }
}