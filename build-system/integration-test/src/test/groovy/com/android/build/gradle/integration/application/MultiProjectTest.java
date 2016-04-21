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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.google.common.collect.Iterables;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import java.util.Collection;
import java.util.Map;

/**
 * Assemble tests for multiproject.
 */
public class MultiProjectTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("multiproject")
            .create();
    static Map<String, AndroidProject> models;

    @BeforeClass
    public static void setUp() {
        models = project.model().getMulti();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void checkModel() {

        AndroidProject baseLibModel = models.get(":baseLibrary");
        assertThat(baseLibModel).named("Module app").isNotNull();

        Collection<Variant> variants = baseLibModel.getVariants();
        assertThat(variants).named("variant list").hasSize(2);

        Variant variant = ModelHelper.getVariant(variants, "release");
        assertThat(variant).named("release variant").isNotNull();

        //noinspection ConstantConditions
        AndroidArtifact mainArtifact = variant.getMainArtifact();
        assertThat(mainArtifact).named("release main artifact").isNotNull();

        Dependencies dependencies = mainArtifact.getCompileDependencies();
        assertThat(dependencies).named("release main artifact dependencies").isNotNull();

        Collection<JavaLibrary> javaLibraries = dependencies.getJavaLibraries();
        assertThat(javaLibraries).named("java dependencies").hasSize(1);
        JavaLibrary javaLibrary = Iterables.getOnlyElement(javaLibraries);
        assertThat(javaLibrary.getProject()).named("single java dep path").isEqualTo(":util");
    }
}
