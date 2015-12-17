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

import static com.android.build.gradle.integration.common.utils.FileHelper.createFile
import static com.android.build.gradle.integration.common.utils.FileHelper.searchAndReplace
import static com.android.utils.FileUtils.delete
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
/**
 * Check resources in androidTest are available in the generated R.java.
 */
@CompileStatic
class AndroidTestResourcesTest {
    private static AndroidTestApp testApp = HelloWorldApp.noBuildFile()
    static {

        testApp.addFile(new TestSourceFile(
                "src/androidTest/res/layout",
                "test_layout_1.xml",
                testLayout(1)))

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

    private static String testLayout(int i) {
        """\
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:orientation="vertical" >
                    <TextView android:id="@+id/test_layout_${i}_textview"
                              android:layout_width="wrap_content"
                              android:layout_height="wrap_content"
                              android:text="Hello, I am a TextView" />
                </LinearLayout>
                """.stripIndent()
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

        libProject.getBuildFile() << """
                apply plugin: 'com.android.library'
                android {
                    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
                }
                """.stripIndent()
    }

    @AfterClass
    static void cleanUp() {
        appProject = null
        libProject = null
        testApp = null
    }

    @Test
    public void "check test layout file listed in test R.java when compiled as an application"() {
        doTest(appProject)
    }

    @Test
    public void "check test layout file listed in test R.java when compiled as a library"() {
        doTest(libProject)
    }

    private static void doTest(GradleTestProject project) {
        project.execute("assembleDebugAndroidTest")
        assertTrue(checkLayoutInR(project, 1, 1))
        assertFalse(checkLayoutInR(project, 2, 2))

        createFile(
                project.file("src/androidTest/res/layout/test_layout_2.xml"),
                testLayout(2))

        project.execute("assembleDebugAndroidTest")
        assertTrue(checkLayoutInR(project, 1, 1))
        assertTrue(checkLayoutInR(project, 2, 2))

        searchAndReplace(
                project.file("src/androidTest/res/layout/test_layout_2.xml"),
                "test_layout_2_textview",
                "test_layout_3_textview")

        project.execute("assembleDebugAndroidTest")
        assertTrue(checkLayoutInR(project, 1, 1))
        assertFalse(checkLayoutInR(project, 2, 2))
        assertTrue(checkLayoutInR(project, 2, 3))

        delete(project.file("src/androidTest/res/layout/test_layout_2.xml"))

        project.execute("assembleDebugAndroidTest")
        assertTrue(checkLayoutInR(project, 1, 1))
        assertFalse(checkLayoutInR(project, 2, 2))
        assertFalse(checkLayoutInR(project, 2, 3))
    }

    @Test
    @Category(DeviceTests.class)
    public void "check test layout can be used in device tests"() {
        appProject.executeConnectedCheck()
    }


    private static boolean checkLayoutInR(GradleTestProject fixture, int layout, int textView) {
        def rFile = fixture.file(Joiner.on(File.separatorChar).join(
                "build", AndroidProject.FD_GENERATED, "source", "r",
                "androidTest", "debug", "com", "example", "helloworld", "test",  "R.java"))
        assertTrue("Should have generated R file", rFile.exists())
        def rFileContents = rFile.getText("UTF-8")

        return (rFileContents.contains("test_layout_${layout}")
                && rFileContents.contains("test_layout_${textView}_textview"))
    }
}
