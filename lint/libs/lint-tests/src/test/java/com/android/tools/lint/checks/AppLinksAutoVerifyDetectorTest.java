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

package com.android.tools.lint.checks;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.Variant;
import com.android.builder.model.VectorDrawablesOptions;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Project;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.File;
import java.util.Map;

@SuppressWarnings("javadoc")
public class AppLinksAutoVerifyDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new AppLinksAutoVerifyDetector();
    }

    public void testOk() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            JsonArray statementList = new JsonArray();
            JsonObject statement = new JsonObject();
            JsonArray relation = new JsonArray();
            relation.add(new JsonPrimitive("delegate_permission/common.handle_all_urls"));
            statement.add("relation", relation);
            JsonObject target = new JsonObject();
            target.addProperty("namespace", "android_app");
            target.addProperty("package_name", "com.example.helloworld");
            statement.add("target", target);
            statementList.add(statement);
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_HTTP_OK, statementList));

            assertEquals("No warnings.",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testInvalidPackage() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            JsonArray statementList = new JsonArray();
            JsonObject statement = new JsonObject();
            JsonArray relation = new JsonArray();
            relation.add(new JsonPrimitive("delegate_permission/common.handle_all_urls"));
            statement.add("relation", relation);
            JsonObject target = new JsonObject();
            target.addProperty("namespace", "android_app");
            target.addProperty("package_name", "com.example");
            statement.add("target", target);
            statementList.add(statement);
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_HTTP_OK, statementList));

            assertEquals(
                    "AndroidManifest.xml:12: Error: This host does not support app links to your app. Checks the Digital Asset Links JSON file: http://example.com/.well-known/assetlinks.json [AppLinksAutoVerifyError]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "1 errors, 0 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testNotAppTarget() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            JsonArray statementList = new JsonArray();
            JsonObject statement = new JsonObject();
            JsonArray relation = new JsonArray();
            relation.add(new JsonPrimitive("delegate_permission/common.handle_all_urls"));
            statement.add("relation", relation);
            JsonObject target = new JsonObject();
            target.addProperty("namespace", "web");
            target.addProperty("package_name", "com.example.helloworld");
            statement.add("target", target);
            statementList.add(statement);
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_HTTP_OK, statementList));

            assertEquals(
                    "AndroidManifest.xml:12: Error: This host does not support app links to your app. Checks the Digital Asset Links JSON file: http://example.com/.well-known/assetlinks.json [AppLinksAutoVerifyError]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "1 errors, 0 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testHttpResponseError() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(404, null));

            assertEquals(
                    "AndroidManifest.xml:12: Warning: HTTP request for Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails. HTTP response code: 404 [AppLinksAutoVerifyWarning]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "0 errors, 1 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testFailedHttpConnection() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_HTTP_CONNECT_FAIL, null));

            assertEquals(
                    "AndroidManifest.xml:12: Warning: Connection to Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerifyWarning]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "0 errors, 1 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testMalformedUrl() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_MALFORMED_URL, null));

            assertEquals(
                    "AndroidManifest.xml:12: Error: Malformed URL of Digital Asset Links JSON file: http://example.com/.well-known/assetlinks.json. An unknown protocol is specified [AppLinksAutoVerifyError]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "1 errors, 0 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testUnknownHost() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST, null));

            assertEquals(
                    "AndroidManifest.xml:12: Warning: Unknown host: http://example.com. Check if the host exists, and check your network connection [AppLinksAutoVerifyWarning]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "0 errors, 1 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testNotFound() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_NOT_FOUND, null));

            assertEquals(
                    "AndroidManifest.xml:12: Error: Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json is not found on the host [AppLinksAutoVerifyError]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "1 errors, 0 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testWrongJsonSyntax() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_WRONG_JSON_SYNTAX, null));

            assertEquals(
                    "AndroidManifest.xml:12: Error: http://example.com/.well-known/assetlinks.json has incorrect JSON syntax [AppLinksAutoVerifyError]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "1 errors, 0 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testFailedJsonParsing() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_JSON_PARSE_FAIL, null));

            assertEquals(
                    "AndroidManifest.xml:12: Error: Parsing JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerifyError]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "1 errors, 0 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testNoAutoVerify() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                        + "        <activity android:name=\".MainActivity\" >\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testNotAppLinkInIntents() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                        + "        <activity android:name=\".MainActivity\" >\n"
                        + "            <intent-filter android:autoVerify=\"true\">\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "            <intent-filter android:autoVerify=\"true\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "            </intent-filter>\n"
                        + "            <intent-filter android:autoVerify=\"true\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testMultipleLinks() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_HTTP_CONNECT_FAIL, null));
            data.put("https://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_NOT_FOUND, null));
            data.put("http://www.example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST, null));
            data.put("https://www.example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_WRONG_JSON_SYNTAX, null));

            assertEquals(
                    "AndroidManifest.xml:12: Error: Digital Asset Links JSON file https://example.com/.well-known/assetlinks.json is not found on the host [AppLinksAutoVerifyError]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "AndroidManifest.xml:15: Error: https://www.example.com/.well-known/assetlinks.json has incorrect JSON syntax [AppLinksAutoVerifyError]\n"
                            + "                <data android:host=\"www.example.com\" />\n"
                            + "                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "AndroidManifest.xml:12: Warning: Connection to Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerifyWarning]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "AndroidManifest.xml:15: Warning: Unknown host: http://www.example.com. Check if the host exists, and check your network connection [AppLinksAutoVerifyWarning]\n"
                            + "                <data android:host=\"www.example.com\" />\n"
                            + "                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "2 errors, 2 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <data android:scheme=\"https\" />\n"
                            + "                <data android:host=\"www.example.com\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testMultipleIntents() throws Exception {
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_HTTP_CONNECT_FAIL, null));
            data.put("http://www.example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST, null));

            assertEquals(
                    "AndroidManifest.xml:12: Warning: Unknown host: http://www.example.com. Check if the host exists, and check your network connection [AppLinksAutoVerifyWarning]\n"
                            + "                    android:host=\"www.example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "AndroidManifest.xml:20: Warning: Connection to Digital Asset Links JSON file http://example.com/.well-known/assetlinks.json fails [AppLinksAutoVerifyWarning]\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "0 errors, 2 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter>\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"www.example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data android:scheme=\"http\"\n"
                            + "                    android:host=\"example.com\"\n"
                            + "                    android:pathPrefix=\"/gizmos\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testUnknownHostWithManifestPlaceholders() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=205990
        // Skip hosts that use manifest placeholders
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST, null));

            assertEquals(
                    "No warnings.",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data\n"
                            + "                    android:host=\"${intentFilterHost}\"\n"
                            + "                    android:pathPrefix=\"/path/\"\n"
                            + "                    android:scheme=\"https\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    public void testUnknownHostWithResolvedManifestPlaceholders() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=205990
        // Skip hosts that use manifest placeholders
        try {
            Map<String, AppLinksAutoVerifyDetector.HttpResult> data = Maps.newHashMap();
            AppLinksAutoVerifyDetector.sMockData = data;
            data.put("http://example.com", new AppLinksAutoVerifyDetector.HttpResult(
                    AppLinksAutoVerifyDetector.STATUS_UNKNOWN_HOST, null));

            assertEquals(""
                    + "AndroidManifest.xml:12: Warning: Unknown host: http://example.com. Check if the host exists, and check your network connection [AppLinksAutoVerifyWarning]\n"
                    + "                    android:host=\"${intentFilterHost}\"\n"
                    + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                    + "0 errors, 1 warnings\n",
                    lintProject(xml("AndroidManifest.xml", ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    package=\"com.example.helloworld\" >\n"
                            + "\n"
                            + "    <application\n"
                            + "        android:allowBackup=\"true\"\n"
                            + "        android:icon=\"@mipmap/ic_launcher\" >\n"
                            + "        <activity android:name=\".MainActivity\" >\n"
                            + "            <intent-filter android:autoVerify=\"true\">\n"
                            + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                            + "                <data\n"

                            + "                    android:host=\"${intentFilterHost}\"\n"
                            + "                    android:pathPrefix=\"/gizmos/\"\n"
                            + "                    android:scheme=\"http\" />\n"
                            + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                            + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                            + "            </intent-filter>\n"
                            + "        </activity>\n"
                            + "    </application>\n"
                            + "\n"
                            + "</manifest>\n")));
        } finally {
            AppLinksAutoVerifyDetector.sMockData = null;
        }
    }

    @Override
    protected TestLintClient createClient() {
        // Provide a model for the place holder test
        if (!"testUnknownHostWithResolvedManifestPlaceholders".equals(getName())) {
            return super.createClient();
        }
        return new TestLintClient() {
            @NonNull
            @Override
            protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
                return new Project(this, dir, referenceDir) {
                    @Override
                    public boolean isGradleProject() {
                        return true;
                    }

                    @Nullable
                    @Override
                    public Variant getCurrentVariant() {
                        Variant onlyVariant = mock(Variant.class);
                        ProductFlavor productFlavor = mock(ProductFlavor.class);
                        when(onlyVariant.getMergedFlavor()).thenReturn(productFlavor);
                        Map<String,Object> placeHolders = Maps.newHashMap();
                        placeHolders.put("intentFilterHost", "example.com");
                        when(productFlavor.getManifestPlaceholders()).thenReturn(placeHolders);

                        return onlyVariant;
                    }

                    @Nullable
                    @Override
                    public AndroidProject getGradleProjectModel() {
                        AndroidProject project = mock(AndroidProject.class);
                        when(project.getModelVersion()).thenReturn("2.0.0");
                        return project;
                    }
                };
            }
        };
    }
}
