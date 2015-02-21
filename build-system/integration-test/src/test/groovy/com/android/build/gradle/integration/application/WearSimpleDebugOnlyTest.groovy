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
import com.android.build.gradle.integration.common.utils.ZipHelper
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE
import static com.android.SdkConstants.FD_RES
import static com.android.SdkConstants.FD_RES_RAW
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
/**
 * Assemble tests for embedded wear app with a single app.
 */
@CompileStatic
class WearSimpleDebugOnlyTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("simpleMicroApp")
            .captureStdOut(true)
            .create()

    @BeforeClass
    static void setUp() {
        project.execute("clean", ":main:assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check release not built"() {
        ByteArrayOutputStream out = project.getStdout()

        String log = out.toString()

        assertFalse(log.contains(":wear:packageRelease"))
    }
}
