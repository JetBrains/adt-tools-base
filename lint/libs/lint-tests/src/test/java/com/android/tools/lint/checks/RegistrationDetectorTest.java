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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
public class RegistrationDetectorTest extends AbstractCheckTest {

    public void testRegistered() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        xml("AndroidManifest.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"test.pkg\">\n"
                                + "    <application\n"
                                + "        android:name=\".MyApplication\">\n"
                                + "        <activity android:name=\".TestActivity\" />\n"
                                + "        <service android:name=\".TestService\" />\n"
                                + "        <provider android:name=\".TestProvider\" />\n"
                                + "        <provider android:name=\".TestProvider2\" />\n"
                                + "        <receiver android:name=\".TestReceiver\" />\n"
                                + "    </application>\n"
                                + "</manifest>\n"),
                        mApplication,
                        mTestActivity,
                        mTestService,
                        mTestProvider,
                        mTestProvider2,
                        mTestReceiver));
    }

    public void testNotRegistered() throws Exception {
        assertEquals(""
                + "src/test/pkg/MyApplication.java:5: Warning: The <application> test.pkg.MyApplication is not registered in the manifest [Registered]\n"
                + "public class MyApplication extends Application {\n"
                + "             ~~~~~~~~~~~~~\n"
                + "src/test/pkg/TestActivity.java:3: Warning: The <activity> test.pkg.TestActivity is not registered in the manifest [Registered]\n"
                + "public class TestActivity extends Activity {\n"
                + "             ~~~~~~~~~~~~\n"
                + "src/test/pkg/TestProvider.java:8: Warning: The <provider> test.pkg.TestProvider is not registered in the manifest [Registered]\n"
                + "public class TestProvider extends ContentProvider {\n"
                + "             ~~~~~~~~~~~~\n"
                + "src/test/pkg/TestProvider2.java:3: Warning: The <provider> test.pkg.TestProvider2 is not registered in the manifest [Registered]\n"
                + "public class TestProvider2 extends TestProvider {\n"
                + "             ~~~~~~~~~~~~~\n"
                + "src/test/pkg/TestService.java:7: Warning: The <service> test.pkg.TestService is not registered in the manifest [Registered]\n"
                + "public class TestService extends Service {\n"
                + "             ~~~~~~~~~~~\n"
                + "0 errors, 5 warnings\n",

                lintProject(
                        // no manifest
                        mApplication,
                        mTestActivity,
                        mTestService,
                        mTestProvider,
                        mTestProvider2,
                        mTestReceiver,
                        mSuppressedApplication));
    }

    public void testNoDot() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        xml("AndroidManifest.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"test.pkg\">\n"
                                + "    <application>\n"
                                + "        <activity android:name=\"TestActivity\" />\n"
                                + "    </application>\n"
                                + "</manifest>\n"),
                        mTestActivity));
    }

    public void testWrongRegistrations() throws Exception {
        assertEquals(""
                + "src/test/pkg/MyApplication.java:5: Warning: test.pkg.MyApplication is an <application> but is registered in the manifest as a <service> [Registered]\n"
                + "public class MyApplication extends Application {\n"
                + "             ~~~~~~~~~~~~~\n"
                + "src/test/pkg/TestActivity.java:3: Warning: test.pkg.TestActivity is an <activity> but is registered in the manifest as a <receiver> [Registered]\n"
                + "public class TestActivity extends Activity {\n"
                + "             ~~~~~~~~~~~~\n"
                + "src/test/pkg/TestProvider.java:8: Warning: test.pkg.TestProvider is a <provider> but is registered in the manifest as an <activity> [Registered]\n"
                + "public class TestProvider extends ContentProvider {\n"
                + "             ~~~~~~~~~~~~\n"
                + "src/test/pkg/TestProvider2.java:3: Warning: test.pkg.TestProvider2 is a <provider> but is registered in the manifest as a <service> [Registered]\n"
                + "public class TestProvider2 extends TestProvider {\n"
                + "             ~~~~~~~~~~~~~\n"
                + "src/test/pkg/TestReceiver.java:7: Warning: test.pkg.TestReceiver is a <receiver> but is registered in the manifest as a <service> [Registered]\n"
                + "public class TestReceiver extends BroadcastReceiver {\n"
                + "             ~~~~~~~~~~~~\n"
                + "src/test/pkg/TestService.java:7: Warning: test.pkg.TestService is a <service> but is registered in the manifest as a <provider> [Registered]\n"
                + "public class TestService extends Service {\n"
                + "             ~~~~~~~~~~~\n"
                + "0 errors, 6 warnings\n",

                lintProject(
                        xml("AndroidManifest.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"test.pkg\">\n"
                                + "    <application\n"
                                + "        android:name=\".TestActivity\">\n"
                                + "        <!-- These registrations are bogus (wrong type) -->\n"
                                + "        <activity android:name=\".TestProvider\" />\n"
                                + "        <service android:name=\"test.pkg.TestProvider2\" />\n"
                                + "        <provider android:name=\".TestService\" />\n"
                                + "        <receiver android:name=\".TestActivity\" />\n"
                                + "        <service android:name=\".TestReceiver\" />\n"
                                + "        <service android:name=\".MyApplication\" />\n"
                                + "    </application>\n"
                                + "</manifest>\n"),
                        mApplication,
                        mTestActivity,
                        mTestService,
                        mTestProvider,
                        mTestProvider2,
                        mTestReceiver));
    }

    public void testLibraryProjects() throws Exception {
        // If a library project provides additional activities, it is not an error to
        // not register all of those here
        assertEquals(
                "No warnings.",
                lintProject(
                        // Master project
                        source("project.properties", "android.library.reference.1=../LibraryProject2"),
                        // Library project
                        source("../LibraryProject2/project.properties", "android.library=true"),

                        java("../LibraryProject2/src/test/pkg/TestActivity.java", ""
                                + "package test.pkg;\n"
                                + "import android.app.Activity;\n"
                                + "public class TestActivity extends Activity {\n"
                                + "}\n")
                ));
    }

    public void testSkipReceivers() throws Exception {
        assertEquals("No warnings.",
                lintProject(java("src/test/pkg/MyReceiver.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.app.Activity;\n"
                        + "import android.content.BroadcastReceiver;\n"
                        + "import android.content.Context;\n"
                        + "import android.content.Intent;\n"
                        + "\n"
                        + "public class MyReceiver extends BroadcastReceiver {\n"
                        + "    @Override\n"
                        + "    public void onReceive(Context context, Intent intent) {\n"
                        + "    }\n"
                        + "\n"
                        + "    private static class MyActivity extends Activity {\n"
                        + "    }\n"
                        + "}\n")));
    }

    @Override
    protected Detector getDetector() {
        return new RegistrationDetector();
    }

    private TestFile mTestActivity = java("src/test/pkg/TestActivity.java", ""
            + "package test.pkg;\n"
            + "import android.app.Activity;\n"
            + "public class TestActivity extends Activity {\n"
            + "}\n");

    private TestFile mTestService = java("src/test/pkg/TestService.java", ""
            + "package test.pkg;\n"
            + "\n"
            + "import android.app.Service;\n"
            + "import android.content.Intent;\n"
            + "import android.os.IBinder;\n"
            + "\n"
            + "public class TestService extends Service {\n"
            + "\n"
            + "    @Override\n"
            + "    public IBinder onBind(Intent intent) {\n"
            + "        return null;\n"
            + "    }\n"
            + "\n"
            + "}\n");

    private TestFile mTestProvider = java("src/test/pkg/TestProvider.java", "package test.pkg;\n"
            + "\n"
            + "import android.content.ContentProvider;\n"
            + "import android.content.ContentValues;\n"
            + "import android.database.Cursor;\n"
            + "import android.net.Uri;\n"
            + "\n"
            + "public class TestProvider extends ContentProvider {\n"
            + "    @Override\n"
            + "    public int delete(Uri uri, String selection, String[] selectionArgs) {\n"
            + "        return 0;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public String getType(Uri uri) {\n"
            + "        return null;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public Uri insert(Uri uri, ContentValues values) {\n"
            + "        return null;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public boolean onCreate() {\n"
            + "        return false;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public Cursor query(Uri uri, String[] projection, String selection,\n"
            + "            String[] selectionArgs, String sortOrder) {\n"
            + "        return null;\n"
            + "    }\n"
            + "\n"
            + "    @Override\n"
            + "    public int update(Uri uri, ContentValues values, String selection,\n"
            + "            String[] selectionArgs) {\n"
            + "        return 0;\n"
            + "    }\n"
            + "}\n");

    private TestFile mTestProvider2 = java("src/test/pkg/TestProvider2.java", ""
            + "package test.pkg;\n"
            + "\n"
            + "public class TestProvider2 extends TestProvider {\n"
            + "}\n");

    private TestFile mTestReceiver = java("src/test/pkg/TestReceiver.java", ""
            + "package test.pkg;\n"
            + "\n"
            + "import android.content.BroadcastReceiver;\n"
            + "import android.content.Context;\n"
            + "import android.content.Intent;\n"
            + "\n"
            + "public class TestReceiver extends BroadcastReceiver {\n"
            + "\n"
            + "    @Override\n"
            + "    public void onReceive(Context context, Intent intent) {\n"
            + "    }\n"
            + "\n"
            + "    // Anonymous classes should NOT be counted as a must-register\n"
            + "    private BroadcastReceiver dummy() {\n"
            + "        return new BroadcastReceiver() {\n"
            + "            @Override\n"
            + "            public void onReceive(Context context, Intent intent) {\n"
            + "            }\n"
            + "        };\n"
            + "    }\n"
            + "}\n");

    private TestFile mApplication = java("src/test/pkg/MyApplication.java", ""
            + "package test.pkg;\n"
            + "\n"
            + "import android.app.Application;\n"
            + "\n"
            + "public class MyApplication extends Application {\n"
            + "}\n");

    private TestFile mSuppressedApplication = java("src/test/pkg/MySuppressedApplication.java", ""
            + "package test.pkg;\n"
            + "\n"
            + "import android.app.Application;\n"
            +  "import android.annotation.SuppressLint;\n"
            + "\n"
            + "@SuppressLint(\"Registered\")\n"
            + "public class MySuppressedApplication extends Application {\n"
            + "}\n");
}