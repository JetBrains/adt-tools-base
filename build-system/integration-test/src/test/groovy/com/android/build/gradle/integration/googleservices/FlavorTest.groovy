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
 * Basic test with gcm + ga
 */
@CompileStatic
class FlavorTest {

    public static final AndroidTestApp helloWorldApp = new EmptyAndroidTestApp("com.example.app")

    private static final File resDataFolder = new File(GradleTestProject.TEST_RES_DIR, "flavor")
    static {
        File source = new File(resDataFolder, "example.json")
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
    private static File generatedFreeResFolder
    private static File generatedPaidResFolder

    @BeforeClass
    public static void setUp() {

        project.getBuildFile() << """
apply plugin: 'com.android.application'
apply plugin: 'com.google.gms.google-services'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

    productFlavors {
        free {
            applicationId 'com.example.app.free'
        }
        paid {
            applicationId 'com.example.app.paid'
        }
    }

    defaultConfig {
        minSdkVersion 15
        targetSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    }
}
"""
        model = project.executeAndReturnModel("clean", "assembleDebug")

        generatedFreeResFolder = new File(project.getTestDir(),
                Joiner.on(File.separator).join("build", "generated", "res", "google-services", "free", "debug"))
        generatedPaidResFolder = new File(project.getTestDir(),
                Joiner.on(File.separator).join("build", "generated", "res", "google-services", "paid", "debug"))
    }

    @AfterClass
    public static void cleanUp() {
        project = null
        model = null
    }

    @Test
    public void "test values res file is generated"() {
        checkResValuesFile(generatedFreeResFolder, "free.values.xml")
        checkResValuesFile(generatedPaidResFolder, "paid.values.xml")
    }

    private static void checkResValuesFile(File generatedResFolder, String goldenFileName) {
        File valuesFolder = new File(generatedResFolder, "values")
        File values = new File(valuesFolder, "values.xml")
        Assert.assertTrue(values.isFile())

        File goldenFile = new File(resDataFolder, goldenFileName)
        Truth.assert_().that(Files.toString(values, Charsets.UTF_8).trim())
                .isEqualTo(Files.toString(goldenFile, Charsets.UTF_8).trim());
    }

    @Test
    public void "test ga res file is generated"() {
        checkGlobalTracker(generatedFreeResFolder, "free.global_tracker.xml")
        checkGlobalTracker(generatedPaidResFolder, "paid.global_tracker.xml")
    }

    private static void checkGlobalTracker(File generatedResFolder, String goldenFileName) {
        File xmlFolder = new File(generatedResFolder, "xml")
        File global_tracker = new File(xmlFolder, "global_tracker.xml")
        Assert.assertTrue(global_tracker.isFile())

        File goldenFile = new File(resDataFolder, goldenFileName)
        Truth.assert_().that(Files.toString(global_tracker, Charsets.UTF_8).trim())
                .isEqualTo(Files.toString(goldenFile, Charsets.UTF_8).trim());
    }

    @Test
    public void "test generated res folder is in model"() {
        checkModel("freeDebug", generatedFreeResFolder)
        checkModel("paidDebug", generatedPaidResFolder)
    }

    private static void checkModel(String variantName, File generatedResFolder) {
        Variant freeDebugVariant = ModelHelper.getVariant(model.getVariants(), variantName);
        Collection<File> generatedResFolders = freeDebugVariant.getMainArtifact().getGeneratedResourceFolders()

        Truth.assert_().that(generatedResFolders).contains(generatedResFolder.getCanonicalFile())
    }
}
