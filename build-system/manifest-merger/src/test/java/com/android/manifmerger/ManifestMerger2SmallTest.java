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

package com.android.manifmerger;

import com.android.sdklib.mock.MockLog;

import junit.framework.TestCase;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for the {@link com.android.manifmerger.ManifestMergerTest} class
 */
public class ManifestMerger2SmallTest extends TestCase {

    public void testValidationFailure()
            throws ParserConfigurationException, SAXException, IOException,
            ManifestMerger2.MergeFailureException {

        MockLog mockLog = new MockLog();
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "        <activity android:name=\"activityOne\" "
                + "             tools:replace=\"exported\"/>\n"
                + "\n"
                + "</manifest>";

        File tmpFile = inputAsFile("ManifestMerger2Test_testValidationFailure", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport = ManifestMerger2.newInvoker(tmpFile, mockLog).merge();
            assertEquals(MergingReport.Result.ERROR, mergingReport.getResult());
            // check the log complains about the incorrect "tools:replace"
            assertTrue(mockLog.toString().contains("tools:replace"));
            assertFalse(mergingReport.getMergedDocument().isPresent());
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    /**
     * Utility method to save a {@link String} XML into a file.
     */
    private File inputAsFile(String testName, String input) throws IOException {
        File tmpFile = File.createTempFile(testName, ".xml");
        FileWriter fw = null;
        try {
            fw = new FileWriter(tmpFile);
            fw.append(input);
        } finally {
            if (fw != null) fw.close();
        }
        return tmpFile;
    }
}
