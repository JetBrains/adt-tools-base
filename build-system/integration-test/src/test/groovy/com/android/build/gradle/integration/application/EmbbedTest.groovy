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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ApkHelper
import com.android.build.gradle.integration.common.utils.ZipHelper
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.SdkConstants.*
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull

/**
 * Assemble tests for embedded.
 */
class EmbbedTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("embedded")
            .create()

    @BeforeClass
    static void setUp() {
        project.execute("clean", ":main:assemble");
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void lint() {
        project.execute("lint")
    }

    @Test
    void "check embedded"() {
        String embeddedApkPath = FD_RES + '/' + FD_RES_RAW + '/' + ANDROID_WEAR_MICRO_APK +
                DOT_ANDROID_PACKAGE;

        // each micro app has a different version name to distinguish them from one another.
        // here we record what we expect from which.
        def variantData = [
                //Output apk name             Version name
                //---------------             ------------
                [ "flavor1-release-unsigned", "flavor1" ],
                [ "flavor2-release-unsigned", "default" ],
                [ "flavor1-custom-unsigned",  "custom" ],
                [ "flavor2-custom-unsigned",  "custom" ],
                [ "flavor1-debug",            null ],
                [ "flavor2-debug",            null ]
        ];

        for (String[] data : variantData) {
            File fullApk = project.getSubproject("main").getApk(data[0]);
            File embeddedApk = ZipHelper.extractFile(fullApk, embeddedApkPath);

            if (data[1] == null) {
                assertNull("Expected no embedded app for " + data[0], embeddedApk);
                break;
            }

            assertNotNull("Failed to find embedded micro app for " + data[0], embeddedApk);

            // check for the versionName
            ApkHelper.checkVersionName(embeddedApk, data[1])
        }
    }
}
