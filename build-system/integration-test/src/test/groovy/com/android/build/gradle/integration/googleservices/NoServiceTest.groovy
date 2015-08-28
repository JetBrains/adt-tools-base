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

package com.android.build.gradle.integration.googleservices

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.google.common.base.Joiner
import com.google.common.io.Files
import com.google.common.truth.Truth
import groovy.json.internal.Charsets
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * Test for mostly empty json file.
 */
@CompileStatic
class NoServiceTest {

    public static final AndroidTestApp helloWorldApp = new EmptyAndroidTestApp("com.example.app.free")

    private static final File resDataFolder = new File(GradleTestProject.TEST_RES_DIR, "noservice")
    static {
        File source = new File(resDataFolder, "no_services.json")
        helloWorldApp.addFile(new TestSourceFile(
                "",
                TestHelper.JSON_FILE_NAME,
                Files.toString(source, Charsets.UTF_8)))
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
        .fromTestApp(helloWorldApp)
        .create()

    public static AndroidProject model
    private static File generatedResFolder

    @BeforeClass
    public static void setUp() {

        project.getBuildFile() << """
apply plugin: 'com.android.application'
apply plugin: 'com.google.gms.google-services'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

    defaultConfig {
        minSdkVersion 15
        targetSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    }
}
"""
        model = project.executeAndReturnModel("clean", "assembleDebug")
        generatedResFolder = new File(project.getTestDir(),
                Joiner.on(File.separator).join("build", "generated", "res", "google-services", "debug"))
    }

    @AfterClass
    public static void cleanUp() {
        project = null

    }
    @Test
    public void "test values res file is generated"() {
        File valuesFolder = new File(generatedResFolder, "values")
        File values = new File(valuesFolder, "values.xml")
        Assert.assertTrue(values.isFile())

        File goldenFile = new File(resDataFolder, "values.xml")
        Truth.assert_().that(Files.toString(values, Charsets.UTF_8).trim())
                .isEqualTo(Files.toString(goldenFile, Charsets.UTF_8).trim());
    }

    @Test
    public void "test generated res folder is in model"() {
        Variant debugVariant = ModelHelper.getVariant(model.getVariants(), "debug");
        Collection<File> generatedResFolders = debugVariant.getMainArtifact().getGeneratedResourceFolders()

        Truth.assert_().that(generatedResFolders).contains(generatedResFolder.getCanonicalFile())
    }
}
