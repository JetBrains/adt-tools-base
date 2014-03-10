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

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for the {@link com.android.manifmerger.PostValidator} class.
 */
public class PostValidatorTest extends TestCase {

    public void testIncorrectRemove()
            throws ParserConfigurationException, SAXException, IOException {

        MockLog mockLog = new MockLog();
        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "        <activity android:name=\"activityOne\" tools:remove=\"exported\"/>\n"
                + "\n"
                + "</manifest>";

        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "        <activity android:name=\"activityOne\"/>"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testIncorrectRemoveMain"), main);

        XmlDocument libraryDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testIncorrectRemoveLib"), library);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mockLog);
        mainDocument.merge(libraryDocument, mergingReportBuilder);

        PostValidator.validate(
                mainDocument, mergingReportBuilder.getActionRecorder().build(), mockLog);
        for (String message : mockLog.getMessages()) {
            if (message.charAt(0) == 'W'
                    && message.contains("PostValidatorTest#testIncorrectRemoveMain:8")) {
                return;
            }
        }
        fail("No reference to faulty PostValidatorTest#testIncorrectRemoveMain:8 found");
    }

    public void testIncorrectReplace()
            throws ParserConfigurationException, SAXException, IOException {

        MockLog mockLog = new MockLog();
        String main = ""
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

        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "        <activity android:name=\"activityOne\"/>"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testIncorrectReplaceMain"), main);

        XmlDocument libraryDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testIncorrectReplaceLib"), library);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mockLog);
        mainDocument.merge(libraryDocument, mergingReportBuilder);

        PostValidator.validate(
                mainDocument, mergingReportBuilder.getActionRecorder().build(), mockLog);
        for (String message : mockLog.getMessages()) {
            if (message.charAt(0) == 'W'
                    && message.contains("PostValidatorTest#testIncorrectReplaceMain:8")) {
                return;
            }
        }
        fail("No reference to faulty PostValidatorTest#testIncorrectRemoveMain:8 found");
    }
}
