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
import com.google.common.base.Charsets
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Tests for the PNG generation feature.
 */
@CompileStatic
class VectorDrawableTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("vectorDrawables")
            .create()

    @Test
    public void "vector file is moved and PNGs are generated"() throws Exception {
        project.execute("clean", "assembleDebug")
        File apk = project.getApk("debug")
        assertThatApk(apk).containsResource("drawable/icon.png")
        assertThatApk(apk).doesNotContainResource("drawable/heart.xml")
        assertThatApk(apk).containsResource("drawable-v21/heart.xml")
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart.png")
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/heart.png")
    }

    @Test
    public void "incremental build: add file"() throws Exception {
        project.execute("assembleDebug")

        File heartXml = new File(project.testDir, "src/main/res/drawable/heart.xml")
        File heartXmlCopy = new File(project.testDir, "src/main/res/drawable/heart2.xml")
        Files.copy(heartXml, heartXmlCopy)

        project.execute("assembleDebug")
        checkIncrementalBuild()

        File apk = project.getApk("debug")
        assertThatApk(apk).containsResource("drawable/icon.png")
        assertThatApk(apk).doesNotContainResource("drawable/heart2.xml")
        assertThatApk(apk).containsResource("drawable-v21/heart2.xml")
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart2.png")
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/heart2.png")
    }

    @Test
    public void "incremental build: delete file"() throws Exception {
        project.execute("assembleDebug")

        File heartXml = new File(project.testDir, "src/main/res/drawable/heart.xml")
        heartXml.delete()

        project.execute("assembleDebug")
        checkIncrementalBuild()

        File apk = project.getApk("debug")
        assertThatApk(apk).containsResource("drawable/icon.png")
        assertThatApk(apk).doesNotContainResource("drawable/heart2.xml")
        assertThatApk(apk).doesNotContainResource("drawable-v21/heart2.xml")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/heart2.png")
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/heart2.png")
    }

    @Test
    public void "incremental build: modify file"() throws Exception {
        project.execute("assembleDebug")

        File preprocessedHeartXml = new File(project.testDir, "build/intermediates/res/preprocessed/debug/drawable-hdpi/heart.png")
        File preprocessedIconPng = new File(project.testDir, "build/intermediates/res/preprocessed/debug/drawable/icon.png")
        long heartXmlModified = preprocessedHeartXml.lastModified()
        long iconModified = preprocessedIconPng.lastModified()

        File heartXml = new File(project.testDir, "src/main/res/drawable/heart.xml")
        String content = Files.toString(heartXml, Charsets.UTF_8)
        // Change the heart to blue.
        Files.write(content.replace("ff0000", "0000ff"), heartXml, Charsets.UTF_8)

        project.execute("assembleDebug")
        checkIncrementalBuild()

        assertThat(preprocessedIconPng.lastModified()).isEqualTo(iconModified)
        assertThat(preprocessedHeartXml.lastModified()).isNotEqualTo(heartXmlModified)
    }

    private void checkIncrementalBuild() {
        File incrementalFolder = new File(
                project.testDir,
                "build/intermediates/incremental/preprocessResourcesTask/debug")
        // state.json is always left behind, this is to make sure incrementalFolder is correct.
        assertThat(new File(incrementalFolder, "state.json").exists()).isTrue()
        assertThat(new File(incrementalFolder, "build_was_incremental").exists()).isTrue()
    }
}
