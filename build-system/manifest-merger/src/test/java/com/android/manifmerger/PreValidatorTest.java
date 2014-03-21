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

import com.android.SdkConstants;
import com.android.sdklib.mock.MockLog;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for the {@link com.android.manifmerger.PreValidator} class.
 */
public class PreValidatorTest extends TestCase {

    public void testCorrectInstructions()
            throws ParserConfigurationException, SAXException, IOException {

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
                + "             android:exported=\"false\""
                + "             tools:replace=\"exported\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testIncorrectRemove"), input);

        MergingReport.Builder mergingReport = new MergingReport.Builder(mockLog);
        MergingReport.Result validated = PreValidator.validate(mergingReport, xmlDocument);
        assertEquals(MergingReport.Result.SUCCESS, validated);
        assertTrue(mockLog.toString().isEmpty());
    }

    public void testIncorrectReplace()
            throws ParserConfigurationException, SAXException, IOException {

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

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testIncorrectRemove"), input);

        MergingReport.Builder mergingReport = new MergingReport.Builder(mockLog);
        MergingReport.Result validated = PreValidator.validate(mergingReport, xmlDocument);
        assertEquals(MergingReport.Result.ERROR, validated);
        // assert the error message complains about the bad instruction usage.
        assertStringPresenceInLogRecords(mergingReport, "tools:replace");
    }

    public void testIncorrectRemove()
            throws ParserConfigurationException, SAXException, IOException {

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
                + "             android:exported=\"true\""
                + "             tools:remove=\"exported\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testIncorrectRemove"), input);

        MergingReport.Builder mergingReport = new MergingReport.Builder(mockLog);
        MergingReport.Result validated = PreValidator.validate(mergingReport, xmlDocument);
        assertEquals(MergingReport.Result.ERROR, validated);
        // assert the error message complains about the bad instruction usage.
        assertStringPresenceInLogRecords(mergingReport, "tools:remove");
    }

    private void assertStringPresenceInLogRecords(MergingReport.Builder mergingReport, String s) {
        for (MergingReport.Record record : mergingReport.build().getLoggingRecords()) {
            if (record.toString().contains(s)) {
                return;
            }
        }
        // failed, dump the records
        for (MergingReport.Record record : mergingReport.build().getLoggingRecords()) {
            Logger.getAnonymousLogger().info(record.toString());
        }
        fail("could not find " + s + " in logging records");
    }
}
