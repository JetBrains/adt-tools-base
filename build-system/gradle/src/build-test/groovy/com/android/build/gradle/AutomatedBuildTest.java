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

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Automated tests building a set of projects using a set of gradle versions.
 *
 * This requires an SDK, found through the ANDROID_HOME environment variable or present in the
 * Android Source tree under out/host/<platform>/sdk/... (result of 'make sdk')
 */
public class AutomatedBuildTest extends BuildTest {

    private String projectName;
    private String gradleVersion;
    private TestType testType;

    private static enum TestType { BUILD, REPORT }

    private static final String[] sBuiltProjects = new String[] {
            "aidl",
            "api",
            "applibtest",
            "assets",
            "attrOrder",
            "basic",
            "dependencies",
            "dependencyChecker",
            "flavored",
            "flavorlib",
            "flavors",
            "genFolderApi",
            "libProguardJarDep",
            "libProguardLibDep",
            "libTestDep",
            "libsTest",
            "localJars",
            "migrated",
            "multiproject",
            "multires",
            "ndkSanAngeles",
            "ndkJniLib",
            "ndkPrebuilts",
            "ndkLibPrebuilts",
            "overlay1",
            "overlay2",
            "pkgOverride",
            "proguard",
            "proguardLib",
            "renderscript",
            "renderscriptInLib",
            "renderscriptMultiSrc",
            "rsSupportMode",
            "sameNamedLibs",
            "tictactoe",
            /*"autorepo"*/
    };

    private static final String[] sReportProjects = new String[] {
            "basic", "flavorlib"
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
                test.setProjectInfo(projectName, gradleVersion, TestType.BUILD);
                suite.addTest(test);
            }

            // then the project to run reports on
            for (String projectName : sReportProjects) {
                String testName = "report_" + projectName + "_" + gradleVersion;

                AutomatedBuildTest test = (AutomatedBuildTest) TestSuite.createTest(
                        AutomatedBuildTest.class, testName);
                test.setProjectInfo(projectName, gradleVersion, TestType.REPORT);
                suite.addTest(test);
            }
        }

        return suite;
    }

    private void setProjectInfo(String projectName, String gradleVersion, TestType testType) {
        this.projectName = projectName;
        this.gradleVersion = gradleVersion;
        this.testType = testType;
    }

    @Override
    protected void runTest() throws Throwable {
        if (testType == TestType.BUILD) {
            buildProject(projectName, gradleVersion);
        } else if (testType == TestType.REPORT) {
            runTasksOn(projectName, gradleVersion, "androidDependencies", "signingReport");
        }
    }
}
