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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.DexClassSubject;
import com.android.ide.common.process.ProcessException;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.xml.sax.SAXException;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatDexClass;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

public class DataBindingIncrementalTest {
    @Rule
    public GradleTestProject project;

    private static final String UP_TO_DATE_LAYOUT_PROCESS =
            ":dataBindingProcessLayoutsDebug UP-TO-DATE";
    private static final String MAIN_ACTIVITY_BINDING_CLASS =
            "Landroid/databinding/testapp/databinding/ActivityMainBinding;";
    private static final String ACTIVITY_MAIN_XML = "src/main/res/layout/activity_main.xml";

    public DataBindingIncrementalTest() {
        project = GradleTestProject.builder()
                .fromTestProject("databindingIncremental")
                .captureStdOut(true)
                .create();
    }


    @Before
    public void setUp() {
        project.getStdout().reset();
    }

    @Test
    public void compileWithoutChange() {
        project.execute("assembleDebug");
        assertNotUpToDate();
        project.getStdout().reset();
        project.execute("assembleDebug");
        assertUpToDate();
    }

    @Test
    public void changeVariableName()
            throws IOException, ProcessException, ParserConfigurationException, SAXException {
        project.execute("assembleDebug");
        project.replaceLine(ACTIVITY_MAIN_XML, 20,
                "<variable name=\"foo2\" type=\"String\"/>");
        project.replaceLine(ACTIVITY_MAIN_XML, 29,
                "<TextView android:text='@{foo2 + \" \" + foo2}'");
        project.getStdout().reset();
        project.execute("assembleDebug");
        assertNotUpToDate();

        ApkSubject apk = assertThatApk(project.getApk("debug"));
        DexClassSubject bindingClass = assertThatDexClass(
                apk.getClassDexDump(MAIN_ACTIVITY_BINDING_CLASS));
        bindingClass.doesNotHaveMethod("setFoo");
        bindingClass.hasMethod("setFoo2");
    }

    @Test
    public void addVariable()
            throws IOException, ProcessException, SAXException, ParserConfigurationException {
        project.execute("assembleDebug");
        project.replaceLine(ACTIVITY_MAIN_XML, 20,
                "<variable name=\"foo\" type=\"String\"/><variable name=\"foo2\" type=\"String\"/>");
        project.getStdout().reset();
        project.execute("assembleDebug");
        assertNotUpToDate();
        ApkSubject apk = assertThatApk(project.getApk("debug"));
        DexClassSubject bindingClass = assertThatDexClass(
                apk.getClassDexDump(MAIN_ACTIVITY_BINDING_CLASS));
        bindingClass.hasMethod("setFoo");
        bindingClass.hasMethod("setFoo2");
    }

    @Test
    public void addIdToView()
            throws IOException, ProcessException, SAXException, ParserConfigurationException {
        project.execute("assembleDebug");
        project.replaceLine(ACTIVITY_MAIN_XML, 30,
                "android:id=\"@+id/myTextView\"");
        project.getStdout().reset();
        project.execute("assembleDebug");
        assertNotUpToDate();
        ApkSubject apk = assertThatApk(project.getApk("debug"));
        DexClassSubject bindingClass = assertThatDexClass(
                apk.getClassDexDump(MAIN_ACTIVITY_BINDING_CLASS));
        bindingClass.hasField("myTextView");
        project.replaceLine(ACTIVITY_MAIN_XML, 30, "");
        project.getStdout().reset();
        project.execute("assembleDebug");
        apk = assertThatApk(project.getApk("debug"));
        bindingClass = assertThatDexClass(
                apk.getClassDexDump(MAIN_ACTIVITY_BINDING_CLASS));
        bindingClass.doesNotHaveField("myTextView");
    }

    @Test
    public void addNewLayout()
            throws IOException, ProcessException, SAXException, ParserConfigurationException {
        project.execute("assembleDebug");
        project.getStdout().reset();
        File mainActiviy = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActiviy.getParentFile(), "activity2.xml");
        Files.copy(mainActiviy, activity2);
        project.getStdout().reset();
        project.execute("assembleDebug");
        assertNotUpToDate();
        ApkSubject apk = assertThatApk(project.getApk("debug"));
        DexClassSubject bindingClass = assertThatDexClass(
                apk.getClassDexDump("Landroid/databinding/testapp/databinding/Activity2Binding;"));
        bindingClass.hasMethod("setFoo");
    }

    @Test
    public void removeLayout() throws IOException, ProcessException {
        File mainActiviy = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActiviy.getParentFile(), "activity2.xml");
        Files.copy(mainActiviy, activity2);
        project.getStdout().reset();
        project.execute("assembleDebug");
        assertThatApk(project.getApk("debug")).containsClass(
                "Landroid/databinding/testapp/databinding/Activity2Binding;");
        assertThat(activity2.delete()).isTrue();
        project.getStdout().reset();
        project.execute("assembleDebug");
        assertThatApk(project.getApk("debug")).doesNotContainClass(
                "Landroid/databinding/testapp/databinding/Activity2Binding;");
    }

    @Test
    public void renameLayout() throws IOException, ProcessException {
        String activity2ClassName = "Landroid/databinding/testapp/databinding/Activity2Binding;";
        File mainActiviy = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActiviy.getParentFile(), "activity2.xml");
        Files.copy(mainActiviy, activity2);
        project.getStdout().reset();
        project.execute("assembleDebug");
        assertThatApk(project.getApk("debug")).containsClass(
                activity2ClassName);
        project.replaceLine("src/main/res/layout/activity2.xml", 19,
                "<data class=\"MyCustomName\">");
        project.getStdout().reset();
        project.execute("assembleDebug");
        assertThatApk(project.getApk("debug")).doesNotContainClass(
                activity2ClassName);
        assertThatApk(project.getApk("debug")).containsClass(
                "Landroid/databinding/testapp/databinding/MyCustomName;");
    }

    private void assertNotUpToDate() {
        assertThat(project.getStdout().toString()).doesNotContain(UP_TO_DATE_LAYOUT_PROCESS);
    }

    private void assertUpToDate() {
        assertThat(project.getStdout().toString()).contains(UP_TO_DATE_LAYOUT_PROCESS);
    }
}
