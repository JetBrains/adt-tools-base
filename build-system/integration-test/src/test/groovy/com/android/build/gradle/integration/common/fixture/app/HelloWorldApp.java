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



package com.android.build.gradle.integration.common.fixture.app;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;

/**
 * Simple test application that prints "hello world!".
 *
 * <p>Using this in a test application as a rule is usually done as:
 * <pre>
 * @Rule
 * public GradleTestProject project = GradleTestProject.builder()
 *     .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
 *     .create();
 * </pre>
 */
public class HelloWorldApp extends AbstractAndroidTestApp implements AndroidTestApp {

    private static final TestSourceFile javaSource = new TestSourceFile(
            "src/main/java/com/example/helloworld", "HelloWorld.java",
            "package com.example.helloworld;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "import android.os.Bundle;\n" +
            "\n" +
            "public class HelloWorld extends Activity {\n" +
            "    /** Called when the activity is first created. */\n" +
            "    @Override\n" +
            "    public void onCreate(Bundle savedInstanceState) {\n" +
            "        super.onCreate(savedInstanceState);\n" +
            "        setContentView(R.layout.main);\n" +
            "        // onCreate\n" +
            "    }\n" +
            "}\n");

    private static final TestSourceFile resValuesSource = new TestSourceFile(
            "src/main/res/values",
            "strings.xml",
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<resources>\n" +
            "    <string name=\"app_name\">HelloWorld</string>\n" +
            "</resources>\n");

    private static final TestSourceFile resLayoutSource = new TestSourceFile(
            "src/main/res/layout",
            "main.xml",
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:orientation=\"vertical\"\n" +
            "    android:layout_width=\"fill_parent\"\n" +
            "    android:layout_height=\"fill_parent\"\n" +
            "    >\n" +
            "<TextView\n" +
            "    android:layout_width=\"fill_parent\"\n" +
            "    android:layout_height=\"wrap_content\"\n" +
            "    android:text=\"hello world!\"\n" +
            "    android:id=\"@+id/text\"\n" +
            "    />\n" +
            "</LinearLayout>\n");

    private static final TestSourceFile manifest = new TestSourceFile(
            "src/main",
            "AndroidManifest.xml",
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "      package=\"com.example.helloworld\"\n" +
            "      android:versionCode=\"1\"\n" +
            "      android:versionName=\"1.0\">\n" +
            "\n" +
            "    <uses-sdk android:minSdkVersion=\"3\" />\n" +
            "    <application android:label=\"@string/app_name\">\n" +
            "        <activity android:name=\".HelloWorld\"\n" +
            "                  android:label=\"@string/app_name\">\n" +
            "            <intent-filter>\n" +
            "                <action android:name=\"android.intent.action.MAIN\" />\n" +
            "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
            "            </intent-filter>\n" +
            "        </activity>\n" +
            "    </application>\n" +
            "</manifest>\n");


    private static final TestSourceFile androidTestSource = new TestSourceFile(
            "src/androidTest/java/com/example/helloworld",
            "HelloWorldTest.java",
            "package com.example.helloworld;\n" +
            "\n" +
            "import android.test.ActivityInstrumentationTestCase2;\n" +
            "import android.test.suitebuilder.annotation.MediumTest;\n" +
            "import android.widget.TextView;\n" +
            "\n" +
            "public class HelloWorldTest extends ActivityInstrumentationTestCase2<HelloWorld> {\n" +
            "    private TextView mTextView;\n" +
            "\n" +
            "    public HelloWorldTest() {\n" +
            "        super(\"com.example.helloworld\", HelloWorld.class);\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    protected void setUp() throws Exception {\n" +
            "        super.setUp();\n" +
            "        final HelloWorld a = getActivity();\n" +
            "        // ensure a valid handle to the activity has been returned\n" +
            "        assertNotNull(a);\n" +
            "        mTextView = (TextView) a.findViewById(R.id.text);\n" +
            "\n" +
            "    }\n" +
            "\n" +
            "    @MediumTest\n" +
            "    public void testPreconditions() {\n" +
            "        assertNotNull(mTextView);\n" +
            "    }\n" +
            "}\n");

    private HelloWorldApp() {
        addFiles(javaSource, resValuesSource, resLayoutSource, manifest, androidTestSource);
    }

    private HelloWorldApp(String plugin) {
        this();

        TestSourceFile buildFile = new TestSourceFile("", "build.gradle", "" +
                "apply plugin: '" + plugin + "'\n" +
                "\n" +
                "android {\n" +
                "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n" +
                "}\n");

        addFile(buildFile);
    }

    public static HelloWorldApp noBuildFile() {
        return new HelloWorldApp();
    }

    public static HelloWorldApp forPlugin(String plugin) {
        return new HelloWorldApp(plugin);
    }
}
