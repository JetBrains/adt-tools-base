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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.builder.model.AndroidProject
import com.google.common.base.Joiner
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static org.junit.Assert.assertTrue

/**
 * Check resources in androidTest are available in the generated R.java.
 */
@CompileStatic
class AndroidTestResourcesTest {
    private static AndroidTestApp testApp = new HelloWorldApp()
    static {

        testApp.addFile(new TestSourceFile("src/androidTest/res/layout", "test_layout_1.xml", """\
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical" >
                    <TextView android:id="@+id/test_layout_1_textview"
                              android:layout_width="wrap_content"
                              android:layout_height="wrap_content"
                              android:text="Hello, I am a TextView" />
                </LinearLayout>
                """.stripIndent()))

        // This class exists to prevent the resource from being automatically removed,
        // if we start filtering test resources by default.
        testApp.addFile(new TestSourceFile("src/androidTest/java/com/example/helloworld",
                "HelloWorldResourceTest.java", """\
                package com.example.helloworld;
                import android.test.ActivityInstrumentationTestCase2;
                import android.test.suitebuilder.annotation.MediumTest;
                import android.widget.TextView;

                public class HelloWorldResourceTest extends
                        ActivityInstrumentationTestCase2<HelloWorld> {
                    private TextView mTextView;

                    public HelloWorldResourceTest() {
                        super("com.example.helloworld", HelloWorld.class);
                    }

                    @Override
                    protected void setUp() throws Exception {
                        super.setUp();
                        final HelloWorld a = getActivity();
                        mTextView = (TextView) a.findViewById(
                                com.example.helloworld.test.R.id.test_layout_1_textview);
                    }

                    @MediumTest
                    public void testPreconditions() {
                        assertNull("Shouldn't find test_layout_1_textview.", mTextView);
                    }
                }
                """.stripIndent()))
    }

    @ClassRule
    public static GradleTestProject appProject = GradleTestProject.builder()
            .withName("application")
            .fromTestApp(testApp)
            .create()

    @ClassRule
    public static GradleTestProject libProject = GradleTestProject.builder()
            .withName("library")
            .fromTestApp(testApp)
            .create()

    /**
     * Use the test app to create an application and a library project.
     */
    @BeforeClass
    static void setUp() {
        appProject.getBuildFile() << """
                apply plugin: 'com.android.application'
                android {
                    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                }
                """.stripIndent()
        appProject.execute("assembleDebugAndroidTest")

        libProject.getBuildFile() << """
                apply plugin: 'com.android.library'
                android {
                    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                }
                """.stripIndent()
        libProject.execute("assembleDebugAndroidTest")
    }

    @AfterClass
    static void cleanUp() {
        appProject = null
        libProject = null
        testApp = null
    }

    @Test
    public void "check test layout file listed in test R.java when compiled as an application"() {
        checkLayoutInR(appProject)
    }

    @Test
    public void "check test layout file listed in test R.java when compiled as a library"() {
        checkLayoutInR(libProject)
    }

    @Test
    @Category(DeviceTests.class)
    public void "check test layout can be used in device tests"() {
        appProject.executeConnectedCheck()
    }


    private void checkLayoutInR(GradleTestProject fixture) {
        def rFile = fixture.file(Joiner.on(File.separatorChar).join(
                "build", AndroidProject.FD_GENERATED, "source", "r",
                "androidTest", "debug", "com", "example", "helloworld", "test",  "R.java"))
        assertTrue("Should have generated R file", rFile.exists())
        def rFileContents = rFile.getText("UTF-8")

        assertTrue("Test/debug R file [${rFile.absolutePath}] should contain test_layout_1",
                rFileContents.contains('test_layout_1'))
        assertTrue("Test/debug R file [${rFile.absolutePath}] " +
                        "should contain test_layout_1_textview",
                rFileContents.contains('test_layout_1_textview'))
    }
}
