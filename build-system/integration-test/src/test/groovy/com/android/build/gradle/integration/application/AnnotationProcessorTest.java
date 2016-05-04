/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.BuildScriptGenerator;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AnnotationProcessorLib;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import org.gradle.api.JavaVersion;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * Tests for annotation processor.
 */
@RunWith(FilterableParameterized.class)
public class AnnotationProcessorTest {
    @Parameterized.Parameters(name = "forJack={0}, forComponentPlugin={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                // forJack       forComponentPlugin
                {true,           false},
                {false,          false},
                {true,           true},
                {false,          true},
        });
    }


    private final boolean forJack;
    private final boolean forComponentPlugin;

    @Rule
    public GradleTestProject project;

    @Rule
    public Adb adb = new Adb();

    public AnnotationProcessorTest(boolean forJack, boolean forComponentPlugin) {
        this.forJack = forJack;
        this.forComponentPlugin = forComponentPlugin;

        project = GradleTestProject.builder()
                .fromTestApp(new MultiModuleTestProject(
                        ImmutableMap.of(
                                ":app", app,
                                ":lib", new AnnotationProcessorLib())))
                .useExperimentalGradleVersion(forComponentPlugin)
                .create();
    }
    public static AndroidTestApp app = HelloWorldApp.noBuildFile();
    static {
        app.removeFile(app.getFile("HelloWorld.java"));
        app.addFile(new TestSourceFile(
        "src/main/java/com/example/helloworld", "HelloWorld.java",
                "package com.example.helloworld;\n"
                        + "\n"
                        + "import android.app.Activity;\n"
                        + "import android.widget.TextView;\n"
                        + "import android.os.Bundle;\n"
                        + "import com.example.annotation.ProvideString;\n"
                        + "\n"
                        + "@ProvideString\n"
                        + "public class HelloWorld extends Activity {\n"
                        + "    /** Called when the activity is first created. */\n"
                        + "    @Override\n"
                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                        + "        super.onCreate(savedInstanceState);\n"
                        + "        TextView tv = new TextView(this);\n"
                        + "        tv.setText(getString());\n"
                        + "        setContentView(tv);\n"
                        + "    }\n"
                        + "\n"
                        + "    public static String getString() {\n"
                        + "        return new com.example.annotation.HelloWorldStringValue().value;\n"
                        + "    }\n"
                        + "\n"
                        + "    public static String getProcessor() {\n"
                        + "        return new com.example.annotation.HelloWorldStringValue().processor;\n"
                        + "    }\n"
                        + "}\n"));


        app.removeFile(app.getFile("HelloWorldTest.java"));
        app.addFile(new TestSourceFile(
                "src/androidTest/java/com/example/hellojni", "HelloWorldTest.java",
                "package com.example.helloworld;\n" +
                        "\n" +
                        "import android.test.ActivityInstrumentationTestCase;\n" +
                        "\n" +
                        "public class HelloWorldTest extends ActivityInstrumentationTestCase<HelloWorld> {\n" +
                        "\n" +
                        "    public HelloWorldTest() {\n" +
                        "        super(\"com.example.helloworld\", HelloWorld.class);\n" +
                        "    }\n" +
                        "\n" +
                        "    public void testStringValue() {\n" +
                        "        assertTrue(\"Hello\".equals(HelloWorld.getString()));\n" +
                        "    }\n" +
                        "    public void testProcessor() {\n" +
                        "        assertTrue(\"Processor\".equals(HelloWorld.getProcessor()));\n" +
                        "    }\n" +
                        "}\n"));
    }

    @Before
    public void setUp() throws IOException {
        String buildScript = new BuildScriptGenerator(
                "\n"
                        + "apply plugin: '${application_plugin}'\n"
                        + "\n"
                        + "${model_start}"
                        + "android {\n"
                        + "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "    buildToolsVersion '" + GradleTestProject.UPCOMING_BUILD_TOOL_VERSION + "'\n"
                        + "    defaultConfig {\n"
                        + "        jackOptions {\n"
                        + "            enabled " + forJack + "\n"
                        + "        }\n"
                        + "        javaCompileOptions {\n"
                        + "            annotationProcessorOptions {\n"
                        + "                ${argument}\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "${model_end}"
                        + "\n"
                        + "dependencies {\n"
                        + "    annotationProcessor project(':lib')\n"
                        + "    compile project(':lib')\n"
                        + "}\n" )
                .addPattern(
                        "argument",
                        "argument \"value\", \"Hello\"",
                        "arguments { create(\"value\") { value \"Hello\" }\n }")
                .build(forComponentPlugin);
        Files.write(buildScript, project.getSubproject(":app").file("build.gradle"), Charsets.UTF_8);
    }

    @Test
    public void normalBuild() throws Exception {
        project.execute("assembleDebug");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        project.executeConnectedCheck();
    }
}
