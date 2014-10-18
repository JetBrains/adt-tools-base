/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.List;

/**
 * DeviceConnector tests.
 *
 * This build relies on the {@link BuildTest} to have been run, so that all that there
 * is left to do is deploy the tested and test apps to the device and run the tests (and gather
 * the result).
 *
 * The dependency on the build tests is ensured by the gradle tasks definition.
 *
 * This does not test every projects under tests, instead it's a selection that actually has
 * tests.
 *
 */
public class DeviceTest extends BuildTest {

    private String testFolder;
    private String projectName;
    private String gradleVersion;
    private TestType testType;


    private static enum TestType { CHECK, CHECK_AND_REPORT, INSTALL, JACK }

    private static final String[] sBuiltProjects = new String[] {
            "api",
            "assets",
            "applibtest",
            "attrOrder",
            "basic",
            "dependencies",
            "densitySplit",
            "flavored",
            "flavorlib",
            "flavoredlib",
            "flavors",
            "libProguardJarDep",
            "libProguardLibDep",
            "libTestDep",
            "libsTest",
            "migrated",
            "multiDex",
            "multires",
            "ndkSanAngeles",
            "ndkJniLib",
            "ndkPrebuilts",
            "ndkLibPrebuilts",
            "overlay1",
            "overlay2",
            "packagingOptions",
            "pkgOverride",
            "proguard",
            "proguardLib",
            "sameNamedLibs"
    };

    private static final List<String> sNdkPluginTests = ImmutableList.of(
            "ndkJniLib2",
            "ndkStandaloneSo",
            "ndkStl"
    );

    private static final String[] sMergeReportProjects = new String[] {
            "multiproject",
    };

    private static final String[] sInstallProjects = new String[] {
            "basic"
    };

    private static final String[] sJackProjects = new String[] {
            "basic"
    };

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.setName("DeviceTest");

        for (String gradleVersion : BasePlugin.GRADLE_SUPPORTED_VERSIONS) {
            if (isIgnoredGradleVersion(gradleVersion)) {
                continue;
            }
            // first the project we build on all available versions of Gradle
            for (String projectName : sBuiltProjects) {
                String testName = "check_" + projectName + "_" + gradleVersion;

                DeviceTest test = (DeviceTest) TestSuite.createTest(DeviceTest.class, testName);
                test.setProjectInfo(FOLDER_TEST_REGULAR, projectName, gradleVersion,
                        TestType.CHECK);
                suite.addTest(test);
            }

            // some native tests that run only on linux for now.
            if (System.getProperty("os.name").equals("Linux")) {
                for (String projectName : sNdkPluginTests) {
                    String testName = "check_" + projectName + "_" + gradleVersion;

                    DeviceTest test = (DeviceTest) TestSuite.createTest(DeviceTest.class, testName);
                    test.setProjectInfo(FOLDER_TEST_NATIVE, projectName, gradleVersion,
                            TestType.CHECK);
                    suite.addTest(test);
                }
            }

            for (String projectName : sMergeReportProjects) {
                String testName = "report_" + projectName + "_" + gradleVersion;

                DeviceTest test = (DeviceTest) TestSuite.createTest(DeviceTest.class, testName);
                test.setProjectInfo(FOLDER_TEST_REGULAR, projectName, gradleVersion,
                        TestType.CHECK_AND_REPORT);
                suite.addTest(test);
            }

            for (String projectName : sInstallProjects) {
                String testName = "install_" + projectName + "_" + gradleVersion;

                DeviceTest test = (DeviceTest) TestSuite.createTest(DeviceTest.class, testName);
                test.setProjectInfo(FOLDER_TEST_REGULAR, projectName, gradleVersion,
                        TestType.INSTALL);
                suite.addTest(test);
            }

            if (System.getenv("TEST_JACK") != null) {
                for (String projectName : sJackProjects) {
                    String testName = "jack_" + projectName + "_" + gradleVersion;

                    DeviceTest test = (DeviceTest) TestSuite.createTest(DeviceTest.class, testName);
                    test.setProjectInfo(FOLDER_TEST_REGULAR, projectName, gradleVersion,
                            TestType.JACK);
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
        //noinspection EmptyFinallyBlock
        try {
            switch (testType) {
                case CHECK:
                    runTasksOn(testFolder, projectName, gradleVersion,
                            "androidDependencies", "signingReport");
                    runTasksOn(testFolder, projectName, gradleVersion,
                            "clean", "connectedCheck");
                    break;
                case CHECK_AND_REPORT:
                    runTasksOn(testFolder, projectName, gradleVersion,
                            "clean", "connectedCheck", "mergeAndroidReports");
                    break;
                case INSTALL:
                    runTasksOn(testFolder, projectName, gradleVersion,
                            "clean", "installDebug", "uninstallAll");
                    break;
                case JACK:
                    runTasksOn(testFolder, projectName, gradleVersion,
                            Lists.newArrayList(
                                    "-PCUSTOM_JACK=1",
                                    "-PCUSTOM_BUILDTOOLS=21.1.0"),
                            "clean", "connectedCheck");
                    break;
            }
        } finally {
            // because runTasksOnProject will throw an exception if the gradle side fails, do this
            // in the finally block.

            // TODO: Get the test output and copy it in here.
        }
    }
}