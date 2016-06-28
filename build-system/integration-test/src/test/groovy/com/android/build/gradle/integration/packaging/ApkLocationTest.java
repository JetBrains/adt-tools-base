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

package com.android.build.gradle.integration.packaging;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.builder.model.AndroidProject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Collection;

@RunWith(FilterableParameterized.class)
public class ApkLocationTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Packaging.getParameters();
    }

    @Parameterized.Parameter
    public Packaging mPackaging;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void outputToInjectedLocation() throws Exception {
        project.executor()
                .withPackaging(mPackaging)
                .withProperty(
                        AndroidProject.PROPERTY_APK_LOCATION,
                        mTemporaryFolder.getRoot().getAbsolutePath())
                .run("assembleDebug");

        File[] files = mTemporaryFolder.getRoot().listFiles();
        assertNotNull(files);

        // There can be one or two APKs in the directory, depending on whether we use old or new
        // packaging.
        if (mPackaging == Packaging.NEW_PACKAGING) {
            assertThat(files).hasLength(1);
        } else {
            assertThat(files).hasLength(2);
        }

        for (File file : files) {
            assertThat(file).isFile();
            assertThat(file.getName()).endsWith(".apk");
        }
    }
}
