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

import com.google.common.collect.ImmutableList;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.Collections;
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

    private String projectName;
    private String gradleVersion;

    private static final String[] sBuiltProjects = new String[] {
            "api",
            "assets",
            "applibtest",
            "attrOrder",
            "basic",
            "dependencies",
            "flavored",
            "flavorlib",
            "flavoredlib",
            "flavors",
            "libProguardJarDep",
            "libProguardLibDep",
            "libTestDep",
            "libsTest",
            "migrated",
            "multires",
            "ndkStandaloneSo",
            "ndkJniLib",
            "ndkJniLib2",
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

    private static final List<String> ndkPluginTests = ImmutableList.of(
            "ndkJniLib2"
    );

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.setName("DeviceTest");

        for (String gradleVersion : BasePlugin.GRADLE_SUPPORTED_VERSIONS) {
            if (isIgnoredGradleVersion(gradleVersion)) {
                continue;
            }
            // first the project we build on all available versions of Gradle
            for (String projectName : sBuiltProjects) {
                // Disable NDK plugin tests on non-Linux platforms due to Gradle incorrectly
                // setting arguments based on current OS instead of target OS.
                if (!System.getProperty("os.name").equals("Linux") &&
                        ndkPluginTests.contains(projectName)) {
                    // TODO: Remove this when Gradle is fix.
                    continue;
                }

                String testName = "check_" + projectName + "_" + gradleVersion;

                DeviceTest test = (DeviceTest) TestSuite.createTest(DeviceTest.class, testName);
                test.setProjectInfo(projectName, gradleVersion);
                suite.addTest(test);
            }
        }

        return suite;
    }

    private void setProjectInfo(String projectName, String gradleVersion) {
        this.projectName = projectName;
        this.gradleVersion = gradleVersion;
    }

    @Override
    protected void runTest() throws Throwable {
        try {
            runTasksOnProject(projectName, gradleVersion,
                    Collections.<String>emptyList(),
                    "clean", "connectedCheck");
        } finally {
            // because runTasksOnProject will throw an exception if the gradle side fails, do this
            // in the finally block.

            // TODO: Get the test output and copy it in here.
        }
    }
}