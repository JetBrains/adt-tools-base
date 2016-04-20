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

package com.android.build.gradle.integration.databinding;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.DexClassSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ide.common.process.ProcessException;
import com.google.common.io.Files;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;


@RunWith(FilterableParameterized.class)
public class DataBindingIncrementalTest {

    @Rule
    public GradleTestProject project;

    private static final String EXPORT_INFO_TASK = ":dataBindingExportBuildInfoDebug";

    private static final String PROCESS_LAYOUTS_TASK = ":dataBindingProcessLayoutsDebug";

    private static final String MAIN_ACTIVITY_BINDING_CLASS =
            "Landroid/databinding/testapp/databinding/ActivityMainBinding;";

    private static final String ACTIVITY_MAIN_XML = "src/main/res/layout/activity_main.xml";

    private static final String ACTIVITY_MAIN_JAVA
            = "src/main/java/android/databinding/testapp/MainActivity.java";

    private final boolean experimental;

    @Parameterized.Parameters(name = "experimental_{0}")
    public static List<Boolean> parameters() {
        return Arrays.asList(true, false);
    }

    public DataBindingIncrementalTest(boolean experimental) {
        this.experimental = experimental;
        project = GradleTestProject.builder()
                .fromTestProject("databindingIncremental")
                .useExperimentalGradleVersion(experimental)
                .create();
    }

    @Before
    public void skipOnJack() throws Exception {
        Assume.assumeFalse(GradleTestProject.USE_JACK);
        project.setBuildFile(experimental ? "build.forexperimental.gradle" : null);
    }

    @Test
    public void compileWithoutChange() throws UnsupportedEncodingException {
        project.execute("assembleDebug");
        assertUpToDate(EXPORT_INFO_TASK, false);
        assertUpToDate(PROCESS_LAYOUTS_TASK, false);
        project.execute("assembleDebug");
        assertUpToDate(EXPORT_INFO_TASK, true);
        assertUpToDate(PROCESS_LAYOUTS_TASK, true);
        assertRecompile();
    }

    @Test
    public void changeJavaCode() throws IOException {
        project.execute("assembleDebug");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_JAVA), 44, "return false;");
        project.execute("assembleDebug");
        assertUpToDate(EXPORT_INFO_TASK, false);
        assertUpToDate(PROCESS_LAYOUTS_TASK, true);
        assertRecompile();
    }

    @Test
    public void changeVariableName()
            throws IOException, ProcessException, ParserConfigurationException, SAXException {
        project.execute("assembleDebug");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 20,
                "<variable name=\"foo2\" type=\"String\"/>");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 29,
                "<TextView android:text='@{foo2 + \" \" + foo2}'");
        project.execute("assembleDebug");
        assertUpToDate(EXPORT_INFO_TASK, false);
        assertUpToDate(PROCESS_LAYOUTS_TASK, false);

        DexClassSubject bindingClass = assertThatApk(project.getApk("debug")).hasMainDexFile()
                .that().hasClass(MAIN_ACTIVITY_BINDING_CLASS).that();
        bindingClass.doesNotHaveMethod("setFoo");
        bindingClass.hasMethod("setFoo2");
        assertRecompile();
    }

    @Test
    public void addVariable()
            throws IOException, ProcessException, SAXException, ParserConfigurationException {
        project.execute("assembleDebug");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 20,
                "<variable name=\"foo\" type=\"String\"/><variable name=\"foo2\" type=\"String\"/>");
        project.execute("assembleDebug");
        assertUpToDate(EXPORT_INFO_TASK, false);
        assertUpToDate(PROCESS_LAYOUTS_TASK, false);
        assertThatApk(project.getApk("debug")).hasMainDexFile()
                .that().hasClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that().hasMethods("setFoo", "setFoo2");
        assertRecompile();
    }

    @Test
    public void addIdToView()
            throws IOException, ProcessException, SAXException, ParserConfigurationException {
        project.execute("assembleDebug");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 30,
                "android:id=\"@+id/myTextView\"");
        project.execute("assembleDebug");

        assertUpToDate(EXPORT_INFO_TASK, false);
        assertUpToDate(PROCESS_LAYOUTS_TASK, false);

        assertThatApk(project.getApk("debug")).hasMainDexFile()
                .that().hasClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that().hasField("myTextView");

        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 30, "");
        project.execute("assembleDebug");
        assertUpToDate(EXPORT_INFO_TASK, false);
        assertUpToDate(PROCESS_LAYOUTS_TASK, false);
        assertThatApk(project.getApk("debug")).hasMainDexFile()
                .that().hasClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that().doesNotHaveField("myTextView");
        assertRecompile();
    }

    @Test
    public void addNewLayout()
            throws IOException, ProcessException, SAXException, ParserConfigurationException {
        project.execute("assembleDebug");
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        project.execute("assembleDebug");

        assertUpToDate(EXPORT_INFO_TASK, false);
        assertUpToDate(PROCESS_LAYOUTS_TASK, false);

        assertThatApk(project.getApk("debug")).hasMainDexFile()
                .that().hasClass("Landroid/databinding/testapp/databinding/Activity2Binding;")
                .that().hasMethod("setFoo");
        assertRecompile();
    }

    @Test
    public void removeLayout() throws IOException, ProcessException {
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        project.execute("assembleDebug");
        assertThatApk(project.getApk("debug")).containsClass(
                "Landroid/databinding/testapp/databinding/Activity2Binding;");
        assertThat(activity2.delete()).isTrue();
        project.execute("assembleDebug");
        assertThatApk(project.getApk("debug")).doesNotContainClass(
                "Landroid/databinding/testapp/databinding/Activity2Binding;");
        assertRecompile();
    }

    @Test
    public void renameLayout() throws IOException, ProcessException {
        String activity2ClassName = "Landroid/databinding/testapp/databinding/Activity2Binding;";
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        project.execute("assembleDebug");

        assertUpToDate(EXPORT_INFO_TASK, false);
        assertUpToDate(PROCESS_LAYOUTS_TASK, false);

        assertThatApk(project.getApk("debug")).containsClass(
                activity2ClassName);
        TestFileUtils.replaceLine(project.file("src/main/res/layout/activity2.xml"), 19,
                "<data class=\"MyCustomName\">");
        project.execute("assembleDebug");

        assertUpToDate(EXPORT_INFO_TASK, false);
        assertUpToDate(PROCESS_LAYOUTS_TASK, false);

        assertThatApk(project.getApk("debug")).doesNotContainClass(
                activity2ClassName);
        assertThatApk(project.getApk("debug")).containsClass(
                "Landroid/databinding/testapp/databinding/MyCustomName;");
        assertRecompile();
    }

    @Test
    public void testUpdateExpression() throws IOException, ProcessException {
        project.execute("assembleDebug");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 29,
                "<TextView android:text='@{foo + ` changed`}'");
        project.execute("assembleDebug");
        assertUpToDate(EXPORT_INFO_TASK, false);
        assertUpToDate(PROCESS_LAYOUTS_TASK, false);
        assertRecompile();
    }

    @Test
    public void testUpdateToTwoWayExpression() throws IOException, ProcessException {
        project.execute("assembleDebug");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 29,
                "<TextView android:text='@={foo}'");
        project.execute("assembleDebug");
        assertUpToDate(EXPORT_INFO_TASK, false);
        assertUpToDate(PROCESS_LAYOUTS_TASK, false);
        assertRecompile();
    }

    private void assertRecompile() {
        project.execute("assembleDebug");

        assertUpToDate(EXPORT_INFO_TASK, true);
        assertUpToDate(PROCESS_LAYOUTS_TASK, true);
    }

    private void assertUpToDate(String task, boolean isUpToDate) {
        String line = task + " UP-TO-DATE";
        if (isUpToDate) {
            assertThat(project.getStdout()).contains(line);
        } else {
            assertThat(project.getStdout()).doesNotContain(line);
        }
    }
}
