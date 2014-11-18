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

package com.android.build.gradle;

import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

import com.android.annotations.NonNull;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Automated tests building a set of projects using a set of gradle versions, and testing
 * the packaging of the apk.
 *
 * This requires an SDK, found through the ANDROID_HOME environment variable or present in the
 * Android Source tree under out/host/<platform>/sdk/... (result of 'make sdk')
 */
public class PackagingBuildTest extends BuildTest {

    private String projectName;
    private String[] packagedFiles;

    private static final Object[] sBuiltProjects = new Object[] {
            "packagingOptions", new String[] { "first_pick.txt" },
    };

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.setName("AutomatedBuildTest");

        // first the project we build on all available versions of Gradle
        for (int i = 0, count = sBuiltProjects.length; i < count ; i += 2) {
            String projectName = (String) sBuiltProjects[i];
            String testName = "build_" + projectName;

            PackagingBuildTest test = (PackagingBuildTest) TestSuite.createTest(
                    PackagingBuildTest.class, testName);
            test.setProjectInfo(projectName, (String[]) sBuiltProjects[i+1]);
            suite.addTest(test);
        }

        return suite;
    }

    private void setProjectInfo(String projectName, String[] packagedFiles) {
        this.projectName = projectName;
        this.packagedFiles = packagedFiles;
    }

    @Override
    protected void runTest() throws Throwable {
        File projectFolder = buildProject(FOLDER_TEST_SAMPLES, projectName,
                BasePlugin.GRADLE_TEST_VERSION);

        // TODO replace with model access.
        File apkFolder = new File(projectFolder, "build/" + FD_OUTPUTS + "/apk");

        File apk = new File(apkFolder, projectName + "-debug-unaligned.apk");

        int found = findFilesInZip(apk, Arrays.asList(packagedFiles));
        assertEquals(packagedFiles.length, found);
    }

    private static int findFilesInZip(@NonNull File zip, @NonNull Collection<String> files)
            throws IOException {
        int found = 0;

        ZipInputStream zis = new ZipInputStream(new FileInputStream(zip));
        try {
            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // do not take directories or anything inside a potential META-INF folder.
                if (entry.isDirectory()) {
                    continue;
                }

                if (files.contains(name)) {
                    found++;
                }
            }
        } finally {
            zis.close();
        }

        return found;
    }
}
