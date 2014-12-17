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
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import java.util.regex.Matcher
import java.util.regex.Pattern

import static org.junit.Assert.assertTrue
/**
 * Test for pseudolocalized.
 */
class PseudoLocalizationTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("pseudolocalized")
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
    void lint() {
        project.execute("lint")
    }

    @Test
    public void testPseudolocalization() throws Exception {
        List<String> output = ApkHelper.getApkBadging(project.getApk("debug"))

        Pattern p = Pattern.compile("^locales:.*'en[_-]XA'.*'ar[_-]XB'.*")
        boolean pseudolocalized = false

        for (String line : output) {
            Matcher m = p.matcher(line)
            if (m.matches()) {
                pseudolocalized = true
            }
        }

        assertTrue("Pseudo locales were not added", pseudolocalized)
    }
}
