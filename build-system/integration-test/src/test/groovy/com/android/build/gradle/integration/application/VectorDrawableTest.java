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

package com.android.build.gradle.integration.application;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertWithMessage;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.searchAndReplace;
import static com.google.common.base.Charsets.UTF_8;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

/**
 * Tests for the PNG generation feature.
 *
 * The "v4" is added by resource merger to all dpi qualifiers, to make it clear dpi qualifiers are
 * supported since API 4.
 */
public class VectorDrawableTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("vectorDrawables")
            .create();

    @BeforeClass
    public static void checkBuildTools() {
        GradleTestProject.assumeBuildToolsAtLeast(21);
    }

    @Test
    public void vectorFileIsMovedAndPngsAreGenerated() throws Exception {
        project.execute("clean", "assembleDebug");
        File apk = project.getApk("debug");
        assertThatApk(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable-v22/no_need.xml");
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable/icon.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThatApk(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable/heart.xml");

        // Check HDPI. Test project contains the hdpi png, it should be used instead of the
        // generated one.
        File originalPng = new File(
                project.getTestDir(),
                "src/main/res/drawable-hdpi/special_heart.png");
        File generatedPng = new File(
                project.getTestDir(),
                "build/generated/res/pngs/debug/drawable-hdpi/special_heart.png");
        File pngToUse = new File(
                project.getTestDir(),
                "build/intermediates/res/merged/debug/drawable-hdpi-v4/special_heart.png");

        assertThat(generatedPng).doesNotExist();
        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isEqualTo(FileUtils.sha1(originalPng));

        // Check XHDPI.
        generatedPng = new File(
                project.getTestDir(),
                "build/generated/res/pngs/debug/drawable-xhdpi/special_heart.png");
        pngToUse = new File(
                project.getTestDir(),
                "build/intermediates/res/merged/debug/drawable-xhdpi-v4/special_heart.png");

        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isEqualTo(FileUtils.sha1(generatedPng));

        // Check interactions with other qualifiers.
        assertThatApk(apk).containsResource("drawable-anydpi-v21/modern_heart.xml");
        assertThatApk(apk).containsResource("drawable-fr-anydpi-v21/french_heart.xml");
        assertThatApk(apk).containsResource("drawable-fr-anydpi-v21/french_heart.xml");
        assertThatApk(apk).containsResource("drawable-fr-hdpi-v4/french_heart.png");
        assertThatApk(apk).containsResource("drawable-hdpi-v16/modern_heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-fr-hdpi-v21/french_heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-fr-xhdpi-v21/french_heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-fr/french_heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-fr/french_heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v16/modern_heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/french_heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/modern_heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/modern_heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/french_heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/modern_heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi/french_heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi/modern_heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-v16/modern_heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-v16/modern_heart.xml");
    }

    @Test
    public void incrementalBuildAddXml() throws Exception {
        project.execute("assembleDebug");
        File apk = project.getApk("debug");

        // Sanity check:
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/heart_copy.png");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/heart_copy.png");

        File intermediatesXml =
                project.file("build/intermediates/res/merged/debug/drawable-anydpi-v21/heart.xml");
        File intermediatesHdpiPng =
                project.file("build/intermediates/res/merged/debug/drawable-hdpi-v4/heart.png");
        long xmlTimestamp = intermediatesXml.lastModified();
        long pngTimestamp = intermediatesHdpiPng.lastModified();

        File heartXml = new File(project.getTestDir(), "src/main/res/drawable/heart.xml");
        File heartXmlCopy = new File(project.getTestDir(), "src/main/res/drawable/heart_copy.xml");
        Files.copy(heartXml, heartXmlCopy);

        project.execute("assembleDebug");
        assertThat(intermediatesXml).wasModifiedAt(xmlTimestamp);
        assertThat(intermediatesHdpiPng).wasModifiedAt(pngTimestamp);

        assertThatApk(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable-anydpi-v21/heart_copy.xml");
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart_copy.png");
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/heart_copy.png");
        assertThatApk(apk).containsResource("drawable/icon.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/heart_copy.xml");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v21/heart_copy.xml");
        assertThatApk(apk).doesNotContainResource("drawable/heart_copy.xml");
    }

    @Test
    public void incrementalBuildDeleteXml() throws Exception {
        project.execute("assembleDebug");
        File intermediatesIconPng =
                project.file("build/intermediates/res/merged/debug/drawable/icon.png");
        long timestamp = intermediatesIconPng.lastModified();

        FileUtils.delete(new File(project.getTestDir(), "src/main/res/drawable/heart.xml"));

        project.execute("assembleDebug");

        File apk = project.getApk("debug");
        assertThatApk(apk).containsResource("drawable/icon.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable/heart.xml");

        assertThat(intermediatesIconPng).wasModifiedAt(timestamp);
    }

    @Test
    public void incrementalBuildDeletePng() throws Exception {
        project.execute("assembleDebug");
        File intermediatesXml =
                project.file("build/intermediates/res/merged/debug/drawable-anydpi-v21/heart.xml");
        long xmlTimestamp = intermediatesXml.lastModified();


        File generatedPng = new File(
                project.getTestDir(),
                "build/generated/res/pngs/debug/drawable-hdpi/special_heart.png");
        File originalPng = new File(
                project.getTestDir(),
                "src/main/res/drawable-hdpi/special_heart.png");
        File pngToUse = new File(
                project.getTestDir(),
                "build/intermediates/res/merged/debug/drawable-hdpi-v4/special_heart.png");

        assertThat(generatedPng).doesNotExist();
        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isEqualTo(FileUtils.sha1(originalPng));

        FileUtils.delete(originalPng);

        project.execute("assembleDebug");

        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isEqualTo(FileUtils.sha1(generatedPng));

        assertThat(intermediatesXml).wasModifiedAt(xmlTimestamp);
    }

    @Test
    public void incrementalBuildAddPng() throws Exception {
        project.execute("assembleDebug");
        File intermediatesXml =
                project.file("build/intermediates/res/merged/debug/drawable-anydpi-v21/heart.xml");
        long xmlTimestamp = intermediatesXml.lastModified();

        File generatedPng = new File(
                project.getTestDir(),
                "build/generated/res/pngs/debug/drawable-xhdpi/special_heart.png");
        File pngToUse = new File(
                project.getTestDir(),
                "build/intermediates/res/merged/debug/drawable-xhdpi-v4/special_heart.png");

        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isEqualTo(FileUtils.sha1(generatedPng));

        // Create a PNG file for XHDPI. It should be used instead of the generated one.
        File hdpiPng = new File(project.getTestDir(), "src/main/res/drawable-hdpi/special_heart.png");
        File xhdpiPng = new File(project.getTestDir(), "src/main/res/drawable-xhdpi/special_heart.png");
        Files.createParentDirs(xhdpiPng);
        Files.copy(hdpiPng, xhdpiPng);

        project.execute("assembleDebug");

        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isNotEqualTo(FileUtils.sha1(generatedPng));

        assertWithMessage("Wrong file used.")
                .that(FileUtils.sha1(pngToUse))
                .isEqualTo(FileUtils.sha1(xhdpiPng));

        assertThat(intermediatesXml).wasModifiedAt(xmlTimestamp);
    }

    @Test
    public void incrementalBuildModifyXml() throws Exception {
        project.execute("assembleDebug");
        File intermediatesIconPng =
                project.file("build/intermediates/res/merged/debug/drawable/icon.png");
        long timestamp = intermediatesIconPng.lastModified();

        File heartPngToUse = new File(
                project.getTestDir(),
                "build/intermediates/res/merged/debug/drawable-hdpi-v4/heart.png");
        File iconPngToUse = new File(
                project.getTestDir(),
                "build/intermediates/res/merged/debug/drawable/icon.png");

        String oldHashCode = FileUtils.sha1(heartPngToUse);
        long heartPngModified = heartPngToUse.lastModified();
        long iconPngModified = iconPngToUse.lastModified();

        File heartXml = new File(project.getTestDir(), "src/main/res/drawable/heart.xml");
        String content = Files.toString(heartXml, UTF_8);
        // Change the heart to blue.
        Files.write(content.replace("ff0000", "0000ff"), heartXml, UTF_8);

        project.execute("assembleDebug");

        assertThat(iconPngToUse.lastModified()).isEqualTo(iconPngModified);
        assertThat(heartPngToUse.lastModified()).isNotEqualTo(heartPngModified);
        assertWithMessage("XML file change not reflected in PNG.")
                .that(FileUtils.sha1(heartPngToUse))
                .isNotEqualTo(oldHashCode);

        assertThat(intermediatesIconPng).wasModifiedAt(timestamp);
    }

    @Test
    public void incrementalBuildReplaceVectorDrawableWithBitmapAlias() throws Exception {
        project.execute("assembleDebug");
        File intermediatesIconPng =
                project.file("build/intermediates/res/merged/debug/drawable/icon.png");
        long timestamp = intermediatesIconPng.lastModified();

        File heartXml = new File(project.getTestDir(), "src/main/res/drawable/heart.xml");
        Files.write(
                "<bitmap xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                        "android:src=\"@drawable/icon\" />",
                heartXml,
                UTF_8);

        project.execute("assembleDebug");

        File apk = project.getApk("debug");
        assertThatApk(apk).containsResource("drawable/heart.xml");
        assertThatApk(apk).containsResource("drawable/icon.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi/heart.png");

        File heartXmlToUse = new File(
                project.getTestDir(),
                "build/intermediates/res/merged/debug/drawable/heart.xml");

        // They won't be equal, because of the source marker added in the XML.
        assertThat(Files.toString(heartXmlToUse, UTF_8)).contains(Files.toString(heartXml, UTF_8));

        assertThat(intermediatesIconPng).wasModifiedAt(timestamp);
    }

    @Test
    public void incrementalBuildReplaceBitmapAliasWithVectorDrawable() throws Exception {
        File heartXml = new File(project.getTestDir(), "src/main/res/drawable/heart.xml");

        String vectorDrawable = Files.toString(heartXml, UTF_8);

        Files.write(
                "<bitmap xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                "android:src=\"@drawable/icon\" />",
                heartXml,
                UTF_8);

        project.execute("clean", "assembleDebug");
        File intermediatesIconPng =
                project.file("build/intermediates/res/merged/debug/drawable/icon.png");
        long timestamp = intermediatesIconPng.lastModified();

        File apk = project.getApk("debug");
        assertThatApk(apk).containsResource("drawable/heart.xml");
        assertThatApk(apk).containsResource("drawable/icon.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi/heart.png");

        File heartXmlToUse = new File(
                project.getTestDir(),
                "build/intermediates/res/merged/debug/drawable/heart.xml");

        // They won't be equal, because of the source marker added in the XML.
        assertThat(Files.toString(heartXmlToUse, UTF_8)).contains(Files.toString(heartXml, UTF_8));

        Files.write(vectorDrawable, heartXml, UTF_8);
        project.execute("assembleDebug");

        assertThatApk(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable/heart.xml");

        assertThat(intermediatesIconPng).wasModifiedAt(timestamp);
    }

    @Test
    public void defaultDensitiesWork() throws Exception {
        project.execute(ImmutableList.of("-PcheckDefaultDensities=true"), "clean", "assembleDebug");
        File apk = project.getApk("debug");
        assertThatApk(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThatApk(apk).containsResource("drawable-xxxhdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable-xxhdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable-mdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable-ldpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable/icon.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-ldpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable/heart.xml");
    }

    @Test
    public void nothingIsDoneWhenMinSdk21AndAbove() throws Exception {
        searchAndReplace(project.getBuildFile(), "minSdkVersion \\d+", "minSdkVersion 21");
        project.execute("clean", "assembleDebug");
        File apk = project.getApk("debug");

        assertThatApk(apk).containsResource("drawable-hdpi-v4/special_heart.png");
        assertThatApk(apk).containsResource("drawable-v16/modern_heart.xml");
        assertThatApk(apk).containsResource("drawable-v22/no_need.xml");
        assertThatApk(apk).containsResource("drawable/heart.xml");
        assertThatApk(apk).containsResource("drawable/icon.png");
        assertThatApk(apk).containsResource("drawable/special_heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-anydpi-v16/modern_heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-anydpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-anydpi-v22/no_need.xml");
        assertThatApk(apk).doesNotContainResource("drawable-anydpi/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
    }

    @Test
    public void disablingPngGeneration() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "android.defaultConfig.vectorDrawables.generatedDensities = []");

        project.execute("clean", "assembleDebug");
        File apk = project.getApk("debug");
        assertPngGenerationDisabled(apk);
    }

    @Test
    public void disablingPngGeneration_oldDsl() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "android.defaultConfig.generatedDensities = []");

        project.execute("clean", "assembleDebug");
        File apk = project.getApk("debug");
        assertPngGenerationDisabled(apk);
    }

    @Test
    public void incrementalBuildDisablingPngGeneration() throws Exception {
        File apk = project.getApk("debug");

        project.execute("clean", "assembleDebug");
        assertThatApk(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable-v22/no_need.xml");
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable/icon.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThatApk(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable/heart.xml");

        TestFileUtils.appendToFile(project.getBuildFile(),
                "android.defaultConfig.vectorDrawables.useSupportLibrary = true");

        project.execute("assembleDebug");
        assertPngGenerationDisabled(apk);
    }

    @Test
    public void incrementalBuildChangingDensities() throws Exception {
        File apk = project.getApk("debug");

        project.execute("clean", "assembleDebug");
        assertThatApk(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable-v22/no_need.xml");
        assertThatApk(apk).containsResource("drawable-xhdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable/icon.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThatApk(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable/heart.xml");

        TestFileUtils.appendToFile(project.getBuildFile(),
                "android.defaultConfig.vectorDrawables.generatedDensities = ['hdpi']");

        project.execute("assembleDebug");
        assertThatApk(apk).containsResource("drawable-anydpi-v21/heart.xml");
        assertThatApk(apk).containsResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable-v22/no_need.xml");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
        assertThatApk(apk).containsResource("drawable/icon.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThatApk(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable/heart.xml");
    }

    private static void assertPngGenerationDisabled(File apk) throws Exception {
        assertThatApk(apk).containsResource("drawable/heart.xml");
        assertThatApk(apk).containsResource("drawable/icon.png");
        assertThatApk(apk).containsResource("drawable-v22/no_need.xml");
        assertThatApk(apk).doesNotContainResource("drawable-anydpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v4/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v4/heart.png");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v21/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-hdpi-v22/no_need.png");
        assertThatApk(apk).doesNotContainResource("drawable-nodpi-v4/heart.xml");
        assertThatApk(apk).doesNotContainResource("drawable-xhdpi-v21/heart.xml");
    }

}
