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

package com.android.ide.common.vectordrawable;

import com.android.annotations.NonNull;
import com.android.testutils.TestUtils;

import junit.framework.TestCase;

import java.io.File;

public class VdCommandLineOptionsTest extends TestCase {
    @NonNull
    private String getTestFolderPath() {
        File testFolder = TestUtils.getRoot("vectordrawable");
        return testFolder.getAbsolutePath();
    }

    private String getInvalidFolderPath() {
        File testFolder = TestUtils.getRoot("vectordrawable");
        return testFolder.getAbsolutePath() + File.separator + "_NOT_EXIST_FOLDER";
    }

    public void testCommandlineOptionsFull() {
        // Use a known directory for testing purpose.
        String testFolderPath = getTestFolderPath();

        VdCommandLineOptions options = new VdCommandLineOptions();
        String[] args = { "-c", "-d", "-in", testFolderPath, "-out", testFolderPath};
        String error = options.parse(args);

        TestCase.assertTrue(error == null);
        TestCase.assertTrue(options.getDisplayXml());
        TestCase.assertTrue(options.getConvertSvg());
        TestCase.assertTrue(options.getOutputDir() != null);
        TestCase.assertTrue(options.getInputFiles() != null);
    }

    public void testCommandlineOptionsDisplayOnly() {
        // Use a known directory for testing purpose.
        String testFolderPath = getTestFolderPath();

        VdCommandLineOptions options = new VdCommandLineOptions();
        String[] args = {"-d", "-in", testFolderPath};
        String error = options.parse(args);

        TestCase.assertTrue(error == null);
        TestCase.assertTrue(options.getDisplayXml());
        TestCase.assertFalse(options.getConvertSvg());
        TestCase.assertTrue(options.getOutputDir() == null);
        TestCase.assertTrue(options.getInputFiles() != null);
    }

    public void testCommandlineOptionsConvertOnly() {
        // Use a known directory for testing purpose.
        String testFolderPath = getTestFolderPath();

        VdCommandLineOptions options = new VdCommandLineOptions();
        String[] args = {"-c", "-in", testFolderPath};
        String error = options.parse(args);

        TestCase.assertTrue(error == null);
        TestCase.assertFalse(options.getDisplayXml());
        TestCase.assertTrue(options.getConvertSvg());
        TestCase.assertTrue(options.getOutputDir() != null);
        TestCase.assertTrue(options.getInputFiles() != null);
    }

    public void testCommandlineOptionsExtras() {
        // Use a known directory for testing purpose.
        String testFolderPath = getTestFolderPath();

        VdCommandLineOptions options = new VdCommandLineOptions();
        String[] args = {"-c", "-in", testFolderPath, "-widthdp", "23", "-heightdp", "10", "-addHeader"};
        String error = options.parse(args);

        TestCase.assertTrue(error == null);
        TestCase.assertFalse(options.getDisplayXml());
        TestCase.assertTrue(options.getConvertSvg());
        TestCase.assertTrue(options.getOutputDir() != null);
        TestCase.assertTrue(options.getInputFiles() != null);
        TestCase.assertTrue(options.getForceWidth() == 23);
        TestCase.assertTrue(options.getForceHeight() == 10);
        TestCase.assertTrue(options.isAddHeader());
    }

    // Below are the negative test cases.
    public void testCommandlineOptionsInvalidOption() {
        VdCommandLineOptions options = new VdCommandLineOptions();
        String[] args = { "-a"};
        String error = options.parse(args);

        TestCase.assertTrue(error != null);
    }

    public void testCommandlineOptionsMissingAction() {
        String testFolderPath = getTestFolderPath();

        VdCommandLineOptions options = new VdCommandLineOptions();
        String[] args = {"-in", testFolderPath, "-out", testFolderPath};
        String error = options.parse(args);

        TestCase.assertTrue(error != null);
    }

    public void testCommandlineOptionsInvalidInputPath() {
        String invalidFolderPath = getInvalidFolderPath();

        VdCommandLineOptions options = new VdCommandLineOptions();
        String[] args = {"-c", "-in", invalidFolderPath};
        String error = options.parse(args);

        TestCase.assertTrue(error != null);
    }

    public void testCommandlineOptionsInvalidOutputPath() {
        String invalidFolderPath = getInvalidFolderPath();

        VdCommandLineOptions options = new VdCommandLineOptions();
        String[] args = {"-c", "-out", invalidFolderPath};
        String error = options.parse(args);

        TestCase.assertTrue(error != null);
    }
}
