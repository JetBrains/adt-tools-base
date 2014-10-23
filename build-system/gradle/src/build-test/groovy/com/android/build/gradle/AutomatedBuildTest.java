/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Automated tests building a set of projects using a set of gradle versions.
 *
 * This requires an SDK, found through the ANDROID_HOME environment variable or present in the
 * Android Source tree under out/host/<platform>/sdk/... (result of 'make sdk')
 */
public class AutomatedBuildTest extends BuildTest {

    private String testFolder;
    private String projectName;
    private String gradleVersion;
    private TestType testType;

    private static enum TestType { BUILD, REPORT, JACK }

    private static final String[] sBuiltProjects = new String[] {
            "aidl",
            "api",
            "applibtest",
            "assets",
            "attrOrder",
            "basic",
            "densitySplit",
            "densitySplitWithOldMerger",
            "dependencies",
            "dependencyChecker",
            "emptySplit",
            "filteredOutBuildType",
            "filteredOutVariants",
            "flavored",
            "flavorlib",
            "flavoredlib",
            "flavors",
            "genFolderApi",
            "libMinifyJarDep",
            "libMinifyLibDep",
            "libTestDep",
            "libsTest",
            "localAarTest",
            "localJars",
            "migrated",
            "multiDex",
            "multiDexWithLib",
            "multiproject",
            "multires",
            "ndkJniLib",
            "ndkLibPrebuilts",
            "ndkPrebuilts",
            "ndkSanAngeles",
            "noPreDex",
            "overlay1",
            "overlay2",
            "pkgOverride",
            "minify",
            "minifyLib",
            "renderscript",
            "renderscriptInLib",
            "renderscriptMultiSrc",
            "rsSupportMode",
            "sameNamedLibs",
            "tictactoe"
    };

    private static final String[] sReportProjects = new String[] {
            "basic",
            "flavorlib"
    };

    private static final String[] sJackProjects = new String[] {
            "basic",
            "minify"
    };

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.setName("AutomatedBuildTest");

        for (String gradleVersion : BasePlugin.GRADLE_SUPPORTED_VERSIONS) {
            if (isIgnoredGradleVersion(gradleVersion)) {
                continue;
            }
            // first the project we build on all available versions of Gradle
            for (String projectName : sBuiltProjects) {
                String testName = "build_" + projectName + "_" + gradleVersion;

                AutomatedBuildTest test = (AutomatedBuildTest) TestSuite.createTest(
                        AutomatedBuildTest.class, testName);
                test.setProjectInfo(FOLDER_TEST_REGULAR, projectName, gradleVersion, TestType.BUILD);
                suite.addTest(test);
            }

            // then the project to run reports on
            for (String projectName : sReportProjects) {
                String testName = "report_" + projectName + "_" + gradleVersion;

                AutomatedBuildTest test = (AutomatedBuildTest) TestSuite.createTest(
                        AutomatedBuildTest.class, testName);
                test.setProjectInfo(FOLDER_TEST_REGULAR, projectName, gradleVersion,
                        TestType.REPORT);
                suite.addTest(test);
            }

            if (System.getenv("TEST_JACK") != null) {
                for (String projectName : sJackProjects) {
                    String testName = "jack_" + projectName + "_" + gradleVersion;

                    AutomatedBuildTest test = (AutomatedBuildTest) TestSuite.createTest(
                            AutomatedBuildTest.class, testName);
                    test.setProjectInfo(FOLDER_TEST_REGULAR, projectName, gradleVersion, TestType.JACK);
                    suite.addTest(test);
                }
            }
        }

        return suite;
    }

    private void setProjectInfo(
            @NonNull String testFolder,
            @NonNull String projectName,
            @NonNull String gradleVersion,
            @NonNull TestType testType) {
        this.testFolder = testFolder;
        this.projectName = projectName;
        this.gradleVersion = gradleVersion;
        this.testType = testType;
    }

    @Override
    protected void runTest() throws Throwable {
        switch (testType) {
            case BUILD:
                buildProject(testFolder, projectName, gradleVersion);
                break;
            case JACK:
                buildProject(testFolder, projectName, gradleVersion, Lists.newArrayList(
                        "-PCUSTOM_JACK=1",
                        "-PCUSTOM_BUILDTOOLS=21.1.0"
                ));
                break;
            case REPORT:
                runTasksOn(testFolder, projectName, gradleVersion,
                        "androidDependencies", "signingReport");
                break;
        }
    }
}
