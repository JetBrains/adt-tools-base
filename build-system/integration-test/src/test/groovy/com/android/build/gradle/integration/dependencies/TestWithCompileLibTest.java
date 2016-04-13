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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.utils.ModelHelper.getAndroidArtifact;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import com.android.ide.common.process.ProcessException;
import com.google.common.truth.Truth;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * test for compile library in a test app
 */
public class TestWithCompileLibTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static Map<String, AndroidProject> models;

    @BeforeClass
    public static void setUp() throws IOException {
        appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                        "dependencies {\n" +
                        "    androidTestCompile project(\":library\")\n" +
                        "}\n");
        models = project.executeAndReturnMultiModel("clean", ":app:assembleDebugAndroidTest");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkCompiledLibraryIsPackaged() throws IOException, ProcessException {
        assertThatApk(project.getSubproject("app").getTestApk("debug"))
                .containsClass("Lcom/example/android/multiproject/library/PersonView;");
    }

    @Test
    public void checkCompiledLibraryIsInTheTestArtifactModel() {
        Variant variant = ModelHelper.getVariant(models.get(":app").getVariants(), "debug");

        Collection<AndroidArtifact> androidArtifacts = variant.getExtraAndroidArtifacts();
        AndroidArtifact testArtifact = getAndroidArtifact(androidArtifacts, ARTIFACT_ANDROID_TEST);
        assertNotNull(testArtifact);

        Dependencies deps = testArtifact.getDependencies();
        TruthHelper.assertThat(deps.getLibraries()).hasSize(1);
    }
}
