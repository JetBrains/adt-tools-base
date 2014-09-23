/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.util.Collections;

/**
 */
public class ProjectTest extends TestCase {

    private static final String GRADLE_VERSION = "2.1";

    protected static final String TEST_FOLDER = "com.android.test.folder";
    protected static final String TEST_SDK = "com.android.test.sdk";
    protected static final String TEST_NDK = "com.android.test.ndk";

    public static Test suite() {

        // search for the env var that contains the folder, sdk, and ndk.
        File folder = getFolderFromEnvVar(TEST_FOLDER);
        File sdkFolder = getFolderFromEnvVar(TEST_SDK);
        File ndkFolder = getFolderFromEnvVar(TEST_NDK);

        TestSuite suite = new TestSuite();
        suite.setName("ProjectTests");

        File[] children = folder.listFiles();

        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    searchForProject(suite, child, sdkFolder, ndkFolder);
                }
            }
        }

        return suite;
    }

    private static File getFolderFromEnvVar(String varName) {
        String path = System.getenv().get(varName);
        if (path == null) {
            path = System.getProperty(varName);
        }
        if (path == null) {
            throw new RuntimeException("Missing folder through env var " + varName);
        }
        File folder = new File(path);
        if (!folder.isDirectory()) {
            throw new RuntimeException("Folder does not exist: " + folder.getPath());
        }

        return folder;
    }

    private static void searchForProject(TestSuite suite, File folder, File sdkFolder, File ndkFolder) {
        // first check if this is a project.
        File buildGradle = new File(folder, "build.gradle");
        if (buildGradle.isFile()) {
            suite.addTest(createTest(folder, sdkFolder, ndkFolder));
        }
    }

    private static ProjectTest createTest(File projectFolder, File sdkFolder, File ndkFolder) {
        String testName = "build_" + projectFolder.getName();
        ProjectTest test = (ProjectTest) TestSuite.createTest(
                ProjectTest.class, testName);
        test.setProjectInfo(projectFolder, sdkFolder, ndkFolder);

        return test;
    }

    private File mProjectFolder;
    private File mSdkFolder;
    private File mNdkFolder;

    private void setProjectInfo(File projectFolder, File sdkFolder, File ndkFolder ) {
        this.mProjectFolder = projectFolder;
        this.mSdkFolder = sdkFolder;
        this.mNdkFolder = ndkFolder;
    }

    @Override
    protected void runTest() throws Throwable {
        AndroidProjectConnector connector = new AndroidProjectConnector(mSdkFolder, mNdkFolder);
        connector.runGradleTasks(mProjectFolder, GRADLE_VERSION, Collections.<String>emptyList(), "clean", "assembleDebug");
    }
}
