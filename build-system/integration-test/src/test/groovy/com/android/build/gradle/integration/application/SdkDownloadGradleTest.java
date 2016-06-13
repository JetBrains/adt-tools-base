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
import static org.junit.Assert.assertNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.integration.common.category.OnlineTests;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.MavenRepositories;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang.SystemUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Tests for automatic SDK download from Gradle.
 */
@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
@Category(OnlineTests.class)
public class SdkDownloadGradleTest {
    private static final String OLD_BUILD_TOOLS = "19.1.0";
    private static final String NEW_BUILD_TOOLS = "23.0.1";
    private static final String OLD_PLATFORM = "22";
    private static final String NEW_PLATFORM = "23";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .addGradleProperties(AndroidGradleOptions.PROPERTY_USE_SDK_DOWNLOAD + "=true")
                    .create();

    private File mSdkHome;

    private File licenseFile;

    @Before
    public void setUp() throws Exception {
        // TODO: Set System property {@code AndroidSdkHandler.SDK_TEST_BASE_URL_PROPERTY}.
        mSdkHome = project.file("local-sdk-for-test");
        FileUtils.mkdirs(mSdkHome);

        File licensesFolder = new File(mSdkHome, "licenses");
        FileUtils.mkdirs(licensesFolder);
        licenseFile = new File(licensesFolder, "android-sdk-license");

        String licensesHash =
                "e6b7c2ab7fa2298c15165e9583d0acf0b04a2232"
                        + System.lineSeparator()
                        + "8933bad161af4178b1185d1a37fbf41ea5269c55";

        Files.write(licenseFile.toPath(), licensesHash.getBytes(StandardCharsets.UTF_8));
        TestFileUtils.appendToFile(
                project.getLocalProp(),
                System.lineSeparator()
                        + SdkConstants.SDK_DIR_PROPERTY
                        + " = "
                        + mSdkHome.getAbsolutePath());

        // Copy one version of build tools and one platform from the real SDK, so we have something
        // to start with.
        File realAndroidHome = new File(System.getenv(SdkConstants.ANDROID_HOME_ENV));

        FileUtils.copyDirectoryToDirectory(
                FileUtils.join(
                        realAndroidHome, SdkConstants.FD_PLATFORMS, "android-" + OLD_PLATFORM),
                FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORMS));

        FileUtils.copyDirectoryToDirectory(
                FileUtils.join(realAndroidHome, SdkConstants.FD_BUILD_TOOLS, OLD_BUILD_TOOLS),
                FileUtils.join(mSdkHome, SdkConstants.FD_BUILD_TOOLS));

        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.defaultConfig.minSdkVersion = 19");
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
                        + "android.compileSdkVersion "
                        + NEW_PLATFORM
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + OLD_BUILD_TOOLS
                        + "\"");

        project.executor().run("assembleDebug");

        File platformTarget = getPlatformFolder();
        assertThat(platformTarget).isDirectory();

        File androidJarFile =
                FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORMS, "android-23", "android.jar");
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
                        + "android.compileSdkVersion "
                        + OLD_PLATFORM
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + NEW_BUILD_TOOLS
                        + "\"");

        project.executor().run("assembleDebug");

        File buildTools = FileUtils.join(mSdkHome, SdkConstants.FD_BUILD_TOOLS, OLD_BUILD_TOOLS);
        assertThat(buildTools).isDirectory();

        File dxFile = FileUtils.join(mSdkHome, SdkConstants.FD_BUILD_TOOLS, OLD_BUILD_TOOLS, "dx");
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
                        + "android.buildToolsVersion \""
                        + OLD_BUILD_TOOLS
                        + "\"");

        project.executor().run("assembleDebug");

        File platformBase = getPlatformFolder();
        assertThat(platformBase).isDirectory();

        File addonTarget =
                FileUtils.join(mSdkHome, SdkConstants.FD_ADDONS, "addon-google_apis-google-23");
        assertThat(addonTarget).isDirectory();
    }

    @Test
    public void checkDependencies_androidRepository() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + OLD_PLATFORM
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + OLD_BUILD_TOOLS
                        + "\""
                        + System.lineSeparator()
                        + "dependencies { compile 'com.android.support:support-v4:23.0.0' }");

        project.executor().run("assembleDebug");

        checkForLibrary(SdkMavenRepository.ANDROID, "com.android.support", "support-v4", "23.0.0");

        // Check that the Google repo is not automatically installed if an Android library is
        // missing.
        assertThat(SdkMavenRepository.GOOGLE.isInstalled(mSdkHome, FileOpUtils.create())).isFalse();
    }

    @Test
    public void checkDependencies_googleRepository() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + OLD_PLATFORM
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + OLD_BUILD_TOOLS
                        + "\""
                        + System.lineSeparator()
                        + "dependencies { compile 'com.google.android.gms:play-services:8.1.0' }");

        project.executor().run("assembleDebug");

        checkForLibrary(
                SdkMavenRepository.GOOGLE, "com.google.android.gms", "play-services", "8.1.0");
    }

    @Test
    public void checkDependencies_individualRepository() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + OLD_PLATFORM
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + OLD_BUILD_TOOLS
                        + "\""
                        + System.lineSeparator()
                        + "dependencies { compile 'com.android.support.constraint:constraint-layout-solver:1.0.0-alpha3' }");

        project.executor().run("assembleDebug");

        checkForLibrary(
                SdkMavenRepository.ANDROID,
                "com.android.support.constraint",
                "constraint-layout-solver",
                "1.0.0-alpha3");

        assertThat(SdkMavenRepository.GOOGLE.isInstalled(mSdkHome, FileOpUtils.create())).isFalse();
        assertThat(SdkMavenRepository.ANDROID.isInstalled(mSdkHome, FileOpUtils.create()))
                .isFalse();
    }

    private void checkForLibrary(
            @NonNull SdkMavenRepository oldRepository,
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull String version) {
        FileOp fileOp = FileOpUtils.create();
        GradleCoordinate coordinate =
                new GradleCoordinate(
                        groupId, artifactId, new GradleCoordinate.StringComponent(version));

        // Try the new repository first.
        File repositoryLocation =
                FileUtils.join(mSdkHome, SdkConstants.FD_EXTRAS, SdkConstants.FD_M2_REPOSITORY);

        File artifactDirectory =
                MavenRepositories.getArtifactDirectory(repositoryLocation, coordinate);

        if (!artifactDirectory.exists()) {
            // Try the old repository it's supposed to be in.
            repositoryLocation = oldRepository.getRepositoryLocation(mSdkHome, true, fileOp);
            assertNotNull(repositoryLocation);
            artifactDirectory =
                    MavenRepositories.getArtifactDirectory(repositoryLocation, coordinate);
            assertThat(artifactDirectory).exists();
        }
    }

    @Test
    public void checkDependencies_invalidDependency() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + OLD_PLATFORM
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + OLD_BUILD_TOOLS
                        + "\""
                        + System.lineSeparator()
                        + "dependencies { compile 'foo:bar:baz' }");

        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        // Make sure the standard gradle error message is what the user sees.
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .startsWith("Could not find foo:bar:baz.");
    }

    @Test
    public void checkNoLicenseError_PlatformTarget() throws Exception {
        FileUtils.delete(licenseFile);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + NEW_PLATFORM
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + OLD_BUILD_TOOLS
                        + "\"");

        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Android SDK Platform 23");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
    }

    @Test
    public void checkNoLicenseError_AddonTarget() throws Exception {
        FileUtils.delete(licenseFile);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:23\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + OLD_BUILD_TOOLS
                        + "\"");

        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Google APIs");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
    }

    @Test
    public void checkNoLicenseError_BuildTools() throws Exception {
        FileUtils.delete(licenseFile);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + OLD_PLATFORM
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + NEW_BUILD_TOOLS
                        + "\"");

        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Android SDK Build-Tools 23.0.1");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
    }

    @Test
    public void checkNoLicenseError_MultiplePackages() throws Exception {
        FileUtils.delete(licenseFile);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:23\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + NEW_BUILD_TOOLS
                        + "\"");

        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Android SDK Build-Tools 23.0.1");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Android SDK Platform 23");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Google APIs");
    }

    @Test
    public void checkPermissions_BuildTools() throws Exception {
        Assume.assumeFalse(SystemUtils.IS_OS_WINDOWS);

        // Change the permissions.
        Path sdkHomePath = mSdkHome.toPath();
        Set<PosixFilePermission> readOnlyDir =
                ImmutableSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE);

        Files.walk(sdkHomePath).forEach(path -> {
            try {
                Files.setPosixFilePermissions(path, readOnlyDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try {
            // Request a new version of build tools.
            TestFileUtils.appendToFile(
                    project.getBuildFile(),
                    System.lineSeparator()
                            + "android.compileSdkVersion "
                            + OLD_PLATFORM
                            + System.lineSeparator()
                            + "android.buildToolsVersion \""
                            + NEW_BUILD_TOOLS
                            + "\"");

            GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
            assertNotNull(result.getException());

            assertThat(Throwables.getRootCause(result.getException()).getMessage())
                    .contains("Android SDK Build-Tools 23.0.1");
            assertThat(Throwables.getRootCause(result.getException()).getMessage())
                    .contains("not writeable");
        } finally {
            Set<PosixFilePermission> readWriteDir =
                    ImmutableSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE);

            //noinspection ThrowFromFinallyBlock
            Files.walk(sdkHomePath).forEach(path -> {
                try {
                    Files.setPosixFilePermissions(path, readWriteDir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private File getPlatformFolder() {
        return FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORMS, "android-23");
    }
}
