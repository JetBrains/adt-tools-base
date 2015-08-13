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

package com.android.build.gradle.integration.dependencies
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.android.builder.model.Variant
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * test for optional aar (using the provided scope)
 */
@CompileStatic
class OptionalAarTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()
    static Map<String, AndroidProject> models

    @BeforeClass
    static void setUp() {
        project.getSubproject('app').getBuildFile() << """

dependencies {
    compile project(':library')
}
"""
        project.getSubproject('library').getBuildFile() << """

dependencies {
    provided project(':library2')
}
"""
        models = project.executeAndReturnMultiModel("clean", ":app:assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void "check app doesn't contain provided lib's layout"() {
        File apk = project.getSubproject('app').getApk("debug")

        assertThatApk(apk).doesNotContainResource("layout/lib2layout.xml")
    }

    @Test
    void "check app doesn't contain provided lib's code"() {
        File apk = project.getSubproject('app').getApk("debug")

        assertThatApk(apk).doesNotContainClass("Lcom/example/android/multiproject/library2/PersonView2;")
    }

    @Test
    void "check lib doesn't contain provided lib's layout"() {
        File aar = project.getSubproject('library').getAar("release")

        assertThatAar(aar).doesNotContainResource("layout/lib2layout.xml")
        assertThatAar(aar).textSymbolFile().contains("int layout liblayout")
        assertThatAar(aar).textSymbolFile().doesNotContain("int layout lib2layout")
    }

    @Test
    void "check app model doesn't include optional library"() {
        Collection<Variant> variants = models.get(":app").getVariants()

        // get the main artifact of the debug artifact and its dependencies
        Variant variant = ModelHelper.getVariant(variants, "debug")
        AndroidArtifact artifact = variant.getMainArtifact()
        Dependencies dependencies = artifact.getDependencies()
        Collection<AndroidLibrary> libs = dependencies.getLibraries();

        assertThat(libs).hasSize(1);

        AndroidLibrary library = libs.first()
        assertThat(library.getProject()).isEqualTo(":library")
        assertThat(library.isOptional()).isFalse()
    }

    @Test
    void "check library model includes optional library"() {
        Collection<Variant> variants = models.get(":library").getVariants()

        // get the main artifact of the debug artifact and its dependencies
        Variant variant = ModelHelper.getVariant(variants, "debug")
        AndroidArtifact artifact = variant.getMainArtifact()
        Dependencies dependencies = artifact.getDependencies()
        Collection<AndroidLibrary> libs = dependencies.getLibraries();

        assertThat(libs).hasSize(1);

        AndroidLibrary library = libs.first()
        assertThat(library.getProject()).isEqualTo(":library2")
        assertThat(library.isOptional()).isTrue()
    }
}
