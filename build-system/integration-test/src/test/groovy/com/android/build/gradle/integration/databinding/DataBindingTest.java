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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.AarSubject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Joiner;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class DataBindingTest {

    @Parameterized.Parameters(name="library={0},forExperimentalPlugin={1},withoutAdapters={2}")
    public static Collection<Object[]> getParameters() {
        List<Object[]> options = new ArrayList<Object[]>();
        for (int i = 0 ; i < 8; i ++) {
            options.add(new Object[]{
                (i & 1) != 0, (i & 2) != 0, (i & 4) != 0
            });
        }
        return options;
    }
    private final boolean myWithoutAdapters;
    private final boolean myLibrary;

    public DataBindingTest(boolean library, boolean forExperimentalPlugin, boolean withoutAdapters) {
        myWithoutAdapters = withoutAdapters;
        myLibrary = library;

        List<String> options = new ArrayList<String>();
        if (library) {
            options.add("library");
        }
        if (withoutAdapters) {
            options.add("withoutadapters");
        }
        if (forExperimentalPlugin) {
            options.add("forexperimental");
        }
        project = GradleTestProject.builder()
                .fromTestProject("databinding", options.isEmpty() ? null : Joiner.on('-').join(options))
                .captureStdOut(true)
                .forExperimentalPlugin(forExperimentalPlugin)
                .create();
    }

    @Rule
    public final GradleTestProject project;

    private String buildOutput;

    @Before
    public void setUp() {
        project.getStdout().reset();
        project.execute("assembleDebug");
        buildOutput = project.getStdout().toString();
    }

    @Test
    public void checkApkContainsDatabindingClasses() throws IOException, ProcessException {
        assertTrue(buildOutput.contains(":dataBindingProcessLayoutsDebug"));
        if (myLibrary) {
            AarSubject aar = assertThatAar(project.getAar("debug"));
            aar.doesNotContainClass("Landroid/g/testapp/databinding/ActivityMainBinding;");
            aar.doesNotContain("Landroid/databinding/adapters/Converters;");
            aar.doesNotContainClass("Landroid/databinding/DataBindingComponent;");
        } else {
            ApkSubject apk = assertThatApk(project.getApk("debug"));
            apk.containsClass("Landroid/databinding/testapp/databinding/ActivityMainBinding;");
            apk.containsClass("Landroid/databinding/DataBindingComponent;");
            if (myWithoutAdapters) {
                apk.doesNotContain("Landroid/databinding/adapters/Converters;");
            } else {
                apk.containsClass("Landroid/databinding/adapters/Converters;");
            }
        }

    }
}
