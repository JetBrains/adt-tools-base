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

package com.android.testutils;

import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

/**
 * Utility methods to deal with loading the test data.
 */
public class TestUtils {

    /**
     * returns a File for the subfolder of the test resource data.
     *
     * <p>This is basically {@code src/test/resources/testData/$name"}.
     *
     * <p>Note that this folder is relative to the root project which is where gradle
     * sets the current working dir when running the tests.
     *
     * <p>If you need a full folder path, use {@link #getCanonicalRoot(String...)}.
     *
     * @param names the names of the subfolders.
     *
     * @return a File
     */
    @NonNull
    public static File getRoot(String... names) {
        File root = new File("src/test/resources/testData/");

        for (String name : names) {
            root = new File(root, name);

            // Hack: The sdk-common tests are not configured properly; running tests
            // works correctly from Gradle but not from within the IDE. The following
            // hack works around this quirk:
            if (!root.isDirectory() && !root.getPath().contains("sdk-common")) {
                File r = new File("sdk-common", root.getPath()).getAbsoluteFile();
                if (r.isDirectory()) {
                    root = r;
                }
            }

            TestCase.assertTrue("Test folder '" + name + "' does not exist! "
                    + "(Tip: Check unit test launch config pwd)",
                    root.isDirectory());

        }

        return root;
    }

    /**
     * returns a File for the subfolder of the test resource data.
     *
     * The full path is canonized.
     * This is basically ".../src/test/resources/testData/$name".
     *
     * @param names the names of the subfolders.
     *
     * @return a File
     */
    public static File getCanonicalRoot(String... names) throws IOException {
        File root = getRoot(names);
        return root.getCanonicalFile();
    }

    public static void deleteFile(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFile(f);
                }
            }
        } else if (dir.isFile()) {
            assertTrue(dir.getPath(), dir.delete());
        }
    }

    public static File createTempDirDeletedOnExit() {
        final File tempDir = Files.createTempDir();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                deleteFile(tempDir);
            }
        });

        return tempDir;
    }

    /**
     * Returns the SDK directory as built from the Android source tree.
     *
     * @return the SDK directory
     */
    @NonNull
    public static File getSdkDir() {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            }
        }

        throw new IllegalStateException("SDK directory not defined with ANDROID_HOME");
    }
}
