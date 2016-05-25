/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.integration.common.category.OnlineTests;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.Charset;

/**
 * Tests for automatic SDK download from Gradle.
 */
@Category(OnlineTests.class)
public class SdkDownloadGradleTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .addGradleProperties(AndroidGradleOptions.PROPERTY_USE_SDK_DOWNLOAD + "=true")
                    .create();

    @Rule public TemporaryFolder mTemporarySdkFolder = new TemporaryFolder(project.getTestDir());

    @Before
    public void setUp() throws Exception {
        File licensesFolder = mTemporarySdkFolder.newFolder("licenses");
        File licenseFile = new File(licensesFolder, "android-sdk-license");

        String licensesHash =
                "e6b7c2ab7fa2298c15165e9583d0acf0b04a2232"
                        + System.lineSeparator()
                        + "8933bad161af4178b1185d1a37fbf41ea5269c55";

        Files.write(licensesHash, licenseFile, Charset.defaultCharset());
        TestFileUtils.appendToFile(
                project.getLocalProp(),
                System.lineSeparator()
                        + SdkConstants.SDK_DIR_PROPERTY
                        + " = "
                        + mTemporarySdkFolder.getRoot().getAbsolutePath());
    }

    /**
     * Tests that the compile SDK target was automatically downloaded in the case that the target
     * was a platform target and it wasn't already there.
     */
    @Test
    public void checkCompileSdkPlatformDownloading() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion 23"
                        + System.lineSeparator()
                        + "android.buildToolsVersion \"19.1.0\"");

        project.executor().run("assembleDebug");

        File platformTarget = getPlatformFolder();
        assertThat(platformTarget).isDirectory();

        File androidJarFile =
                FileUtils.join(
                        mTemporarySdkFolder.getRoot(),
                        SdkConstants.FD_PLATFORMS,
                        "android-23",
                        "android.jar");
        assertThat(androidJarFile).exists();
    }

    /**
     * Tests that the build tools were automatically downloaded, when they weren't already installed
     */
    @Test
    public void checkBuildToolsDownloading() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion 23"
                        + System.lineSeparator()
                        + "android.buildToolsVersion \"19.1.0\"");

        project.executor().run("assembleDebug");

        File buildTools =
                FileUtils.join(
                        mTemporarySdkFolder.getRoot(), SdkConstants.FD_BUILD_TOOLS, "19.1.0");
        assertThat(buildTools).isDirectory();

        File dxFile =
                FileUtils.join(
                        mTemporarySdkFolder.getRoot(), SdkConstants.FD_BUILD_TOOLS, "19.1.0", "dx");
        assertThat(dxFile).exists();
    }

    /**
     * Tests that the compile SDK target was automatically downloaded in the case that the target
     * was an addon target. It also checks that the platform that the addon is dependent on was
     * downloaded.
     */
    @Test
    public void checkCompileSdkAddonDownloading() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:23\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \"19.1.0\"");

        project.executor().run("assembleDebug");

        File platformBase = getPlatformFolder();
        assertThat(platformBase).isDirectory();

        File addonTarget =
                FileUtils.join(
                        mTemporarySdkFolder.getRoot(),
                        SdkConstants.FD_ADDONS,
                        "addon-google_apis-google-23");
        assertThat(addonTarget).isDirectory();
    }

    @Test
    public void checkDependencies_androidRepository() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.defaultConfig.minSdkVersion = 21"
                        + System.lineSeparator()
                        + "dependencies { compile 'com.android.support:support-v4:23.0.0' }");

        project.executor().run("assembleDebug");

        checkForLibrary(
                "android",
                "com",
                "android",
                "support",
                "support-v4",
                "23.0.0",
                "support-v4-23.0.0.aar");
    }

    @Test
    public void checkDependencies_googleRepository() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.defaultConfig.minSdkVersion = 21"
                        + System.lineSeparator()
                        + "dependencies { compile 'com.google.android.support:wearable:1.4.0' }");

        project.executor().run("assembleDebug");

        checkForLibrary(
                "google",
                "com",
                "google",
                "android",
                "support",
                "wearable",
                "1.4.0",
                "wearable-1.4.0.aar");
    }

    private void checkForLibrary(@NonNull String repoName, @NonNull String... segments) {
        File newRepo =
                FileUtils.join(
                        mTemporarySdkFolder.getRoot(),
                        SdkConstants.FD_EXTRAS,
                        SdkConstants.FD_M2_REPOSITORY);

        File oldRepo =
                FileUtils.join(
                        mTemporarySdkFolder.getRoot(),
                        SdkConstants.FD_EXTRAS,
                        repoName,
                        SdkConstants.FD_M2_REPOSITORY);

        for (File repo : ImmutableList.of(newRepo, oldRepo)) {
            File artifact = FileUtils.join(repo, segments);
            if (artifact.isFile()) {
                return;
            }
        }

        Assert.fail(
                String.format(
                        "File %s not found in known locations.", Joiner.on("/").join(segments)));
    }

    @Test
    @SuppressWarnings({"ConstantConditions", "ThrowableResultOfMethodCallIgnored"})
    public void checkDependencies_invalidDependency() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator() + "dependencies { compile 'foo:bar:baz' }");

        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");

        // Make sure the standard gradle error message is what the user sees.
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .startsWith("Could not find foo:bar:baz.");
    }

    private File getPlatformFolder() {
        return FileUtils.join(
                mTemporarySdkFolder.getRoot(), SdkConstants.FD_PLATFORMS, "android-23");
    }
}
