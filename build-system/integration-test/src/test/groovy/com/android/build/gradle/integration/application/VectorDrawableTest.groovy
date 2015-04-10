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
import com.google.common.io.Files
import org.junit.AfterClass
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Tests for the PNG generation feature.
 */
class VectorDrawableTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("vectorDrawables")
            .create()


    @AfterClass
    public static void freeResources() throws Exception {
        project = null
    }

    @Before
    public void assemble() throws Exception {
        project.execute("clean", "assembleDebug")
    }

    @Test
    public void "vector file is moved and PNGs are generated"() throws Exception {
        File apk = project.getApk("debug")
        assertThatApk(apk).doesNotContainResource("drawable/heart.xml")
        assertThatApk(apk).containsResource("drawable-v21/heart.xml")
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart.png")
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/heart.png")
    }

    @Test
    public void "incremental build: add file"() throws Exception {
        File apk = project.getApk("debug")
        assertThatApk(apk).doesNotContainResource("drawable/heart2.xml")
        assertThatApk(apk).doesNotContainResource("drawable-v21/heart2.xml")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/heart2.png")
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/heart2.png")

        File heartXml = new File(project.testDir, "src/main/res/drawable/heart.xml")
        File heartXmlCopy = new File(project.testDir, "src/main/res/drawable/heart2.xml")
        Files.copy(heartXml, heartXmlCopy)

        project.execute("assembleDebug")

        apk = project.getApk("debug")
        assertThatApk(apk).doesNotContainResource("drawable/heart2.xml")
        assertThatApk(apk).containsResource("drawable-v21/heart2.xml")
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart2.png")
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/heart2.png")

        heartXmlCopy.delete()
        project.execute("assembleDebug")

        apk = project.getApk("debug")
        assertThatApk(apk).doesNotContainResource("drawable/heart2.xml")
        assertThatApk(apk).doesNotContainResource("drawable-v21/heart2.xml")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/heart2.png")
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/heart2.png")
    }
}
