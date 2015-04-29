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
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertWithMessage

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

        // Check HDPI. Test project contains the hdpi png, it should be used instead of the
        // generated one.
        File originalPng = new File(
                project.testDir,
                "build/intermediates/res/merged/debug/drawable-hdpi/special_heart.png")
        File generatedPng = new File(
                project.testDir,
                "build/generated/res/pngs/debug/drawable-hdpi/special_heart.png")
        File pngToUse = new File(
                project.testDir,
                "build/intermediates/res/preprocessed/debug/drawable-hdpi/special_heart.png")


        assertWithMessage("Generated file is just a copy.")
                .that(FileUtils.sha1(originalPng))
                .isNotEqualTo(FileUtils.sha1(generatedPng))
        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isEqualTo(FileUtils.sha1(originalPng))

        // Check XHDPI.
        generatedPng = new File(
                project.testDir,
                "build/generated/res/pngs/debug/drawable-xhdpi/special_heart.png")
        pngToUse = new File(
                project.testDir,
                "build/intermediates/res/preprocessed/debug/drawable-xhdpi/special_heart.png")

        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isEqualTo(FileUtils.sha1(generatedPng))

        // Check interactions with other qualifiers.
        assertThatApk(apk).containsResource("drawable-fr-v21/french_heart.xml")
        assertThatApk(apk).doesNotContainResource("drawable-v21/french_heart.xml")
        assertThatApk(apk).doesNotContainResource("drawable-fr/french_heart.xml")
        assertThatApk(apk).containsResource("drawable-fr-hdpi-v4/french_heart.png")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi/french_heart.png")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/french_heart.png")
        assertThatApk(apk).doesNotContainResource("drawable-fr/french_heart.png")

        assertThatApk(apk).containsResource("drawable-v21/modern_heart.xml")
        assertThatApk(apk).doesNotContainResource("drawable-v16/modern_heart.xml")
        assertThatApk(apk).containsResource("drawable-hdpi-v16/modern_heart.png")
        assertThatApk(apk).doesNotContainResource("drawable-v16/modern_heart.png")
        assertThatApk(apk).doesNotContainResource("drawable-v21/modern_heart.png")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi/modern_heart.png")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/modern_heart.png")
    }

    @Test
    public void "incremental build: add xml"() throws Exception {
        project.execute("assembleDebug")

        File heartXml = new File(project.testDir, "src/main/res/drawable/heart.xml")
        File heartXmlCopy = new File(project.testDir, "src/main/res/drawable/heart_copy.xml")
        Files.copy(heartXml, heartXmlCopy)

        project.execute("assembleDebug")
        checkIncrementalBuild()

        File apk = project.getApk("debug")
        assertThatApk(apk).containsResource("drawable/icon.png")
        assertThatApk(apk).doesNotContainResource("drawable/heart_copy.xml")
        assertThatApk(apk).containsResource("drawable-v21/heart_copy.xml")
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart_copy.png")
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/heart_copy.png")
    }

    @Test
    public void "incremental build: delete xml"() throws Exception {
        project.execute("assembleDebug")

        File heartXml = new File(project.testDir, "src/main/res/drawable/heart.xml")
        heartXml.delete()

        project.execute("assembleDebug")
        checkIncrementalBuild()

        File apk = project.getApk("debug")
        assertThatApk(apk).containsResource("drawable/icon.png")
        assertThatApk(apk).doesNotContainResource("drawable/heart.xml")
        assertThatApk(apk).doesNotContainResource("drawable-v21/heart.xml")
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/heart.png")
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png")
    }

    @Test
    public void "incremental build: delete png"() throws Exception {
        project.execute("assembleDebug")

        File generatedPng = new File(
                project.testDir,
                "build/generated/res/pngs/debug/drawable-hdpi/special_heart.png")
        File pngToUse = new File(
                project.testDir,
                "build/intermediates/res/preprocessed/debug/drawable-hdpi/special_heart.png")

        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isNotEqualTo(FileUtils.sha1(generatedPng))

        File pngFile = new File(project.testDir, "src/main/res/drawable-hdpi/special_heart.png")
        pngFile.delete()

        project.execute("assembleDebug")
        checkIncrementalBuild()

        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isEqualTo(FileUtils.sha1(generatedPng))
    }

    @Test
    public void "incremental build: add png"() throws Exception {
        project.execute("assembleDebug")

        File generatedPng = new File(
                project.testDir,
                "build/generated/res/pngs/debug/drawable-xhdpi/special_heart.png")
        File pngToUse = new File(
                project.testDir,
                "build/intermediates/res/preprocessed/debug/drawable-xhdpi/special_heart.png")

        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isEqualTo(FileUtils.sha1(generatedPng))

        // Create a PNG file for XHDPI. It should be used instead of the generated one.
        File hdpiPng = new File(project.testDir, "src/main/res/drawable-hdpi/special_heart.png")
        File xhdpiPng = new File(project.testDir, "src/main/res/drawable-xhdpi/special_heart.png")
        Files.createParentDirs(xhdpiPng)
        Files.copy(hdpiPng, xhdpiPng)

        project.execute("assembleDebug")
        checkIncrementalBuild()

        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isNotEqualTo(FileUtils.sha1(generatedPng))
    }

    @Test
    public void "incremental build: modify xml"() throws Exception {
        project.execute("assembleDebug")

        File preprocessedHeartXml = new File(
                project.testDir,
                "build/intermediates/res/preprocessed/debug/drawable-hdpi/heart.png")
        File preprocessedIconPng = new File(
                project.testDir,
                "build/intermediates/res/preprocessed/debug/drawable/icon.png")

        String oldHashCode = FileUtils.sha1(preprocessedHeartXml)
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
        assertWithMessage("XML file not updated.")
                .that(FileUtils.sha1(preprocessedHeartXml))
                .isNotEqualTo(oldHashCode)
    }

    private void checkIncrementalBuild() {
        File incrementalFolder = new File(
                project.testDir,
                "build/intermediates/incremental/preprocessResourcesTask/debug")
        assertThat(new File(incrementalFolder, "build_was_incremental").exists()).isTrue()
    }
}
