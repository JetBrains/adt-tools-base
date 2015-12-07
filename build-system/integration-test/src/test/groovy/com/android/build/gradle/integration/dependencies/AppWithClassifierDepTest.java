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

package com.android.build.gradle.integration.dependencies;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.Collection;

/**
 * test for same dependency with and without classifier.
 */
public class AppWithClassifierDepTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithClassifierDep")
            .create();
    AndroidProject model;

    @Before
    public void setUp() {
        model = project.getSingleModel();
    }

    @After
    public void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkDebugDepInModel() {
        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug");
        Truth.assertThat(variant).isNotNull();
        Dependencies dependencies = variant.getMainArtifact().getDependencies();

        Collection<JavaLibrary> javaLibs = dependencies.getJavaLibraries();
        assertNotNull(javaLibs);
        assertEquals(1, javaLibs.size());

        JavaLibrary javaLib = javaLibs.iterator().next();
        assertEquals(
                new File(project.getTestDir(), "repo/com/foo/sample/1.0/sample-1.0.jar"),
                javaLib.getJarFile());
    }

    @Test
    public void checkAndroidTestDepInModel() {
        Variant debugVariant = ModelHelper.getVariant(model.getVariants(), "debug");
        Truth.assertThat(debugVariant).isNotNull();

        AndroidArtifact androidTestArtifact = ModelHelper.getAndroidArtifact(
                debugVariant.getExtraAndroidArtifacts(), ARTIFACT_ANDROID_TEST);
        Truth.assertThat(androidTestArtifact).isNotNull();

        Dependencies dependencies = androidTestArtifact.getDependencies();

        Collection<JavaLibrary> javaLibs = dependencies.getJavaLibraries();
        assertNotNull(javaLibs);
        assertEquals(1, javaLibs.size());

        JavaLibrary javaLib = javaLibs.iterator().next();
        assertEquals(
                new File(project.getTestDir(), "repo/com/foo/sample/1.0/sample-1.0-testlib.jar"),
                javaLib.getJarFile());
    }
}
