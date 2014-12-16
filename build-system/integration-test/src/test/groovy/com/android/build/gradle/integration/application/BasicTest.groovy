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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.build.gradle.integration.common.utils.SigningConfigHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.JavaCompileOptions
import com.android.builder.model.SigningConfig
import com.android.builder.model.Variant
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.builder.core.BuilderConstants.DEBUG
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

/**
 * Assemble tests for basic.
 */
@CompileStatic
class BasicTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("basic")
            .create()

    static public AndroidProject model

    @BeforeClass
    static void setUp() {
        model = project.executeAndReturnModel("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void report() {
        project.execute("androidDependencies", "signingReport")
    }

    @Test
    void basicModel() {
        assertFalse("Library Project", model.isLibrary())
        assertEquals("Compile Target", "android-21", model.getCompileTarget())
        assertFalse("Non empty bootclasspath", model.getBootClasspath().isEmpty())

        assertNotNull("aaptOptions not null", model.getAaptOptions())
        assertEquals("aaptOptions noCompress", 1, model.getAaptOptions().getNoCompress().size())
        assertTrue("aaptOptions noCompress", model.getAaptOptions().getNoCompress().contains("txt"))
        assertEquals(
                "aaptOptions ignoreAssetsPattern",
                "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~",
                model.getAaptOptions().getIgnoreAssets())
        assertFalse(
                "aaptOptions getFailOnMissingConfigEntry",
                model.getAaptOptions().getFailOnMissingConfigEntry())

        JavaCompileOptions javaCompileOptions = model.getJavaCompileOptions()
        assertEquals("1.6", javaCompileOptions.getSourceCompatibility())
        assertEquals("1.6", javaCompileOptions.getTargetCompatibility())
    }

    @Test
    public void sourceProvidersModel() {
        ModelHelper.testDefaultSourceSets(model, project.getTestDir())

        // test the source provider for the artifacts
        for (Variant variant : model.getVariants()) {
            AndroidArtifact artifact = variant.getMainArtifact()
            assertNull(artifact.getVariantSourceProvider())
            assertNull(artifact.getMultiFlavorSourceProvider())
        }
    }

    @Test
    public void signingConfigsModel() {
        Collection<SigningConfig> signingConfigs = model.getSigningConfigs()
        assertNotNull("SigningConfigs null-check", signingConfigs)
        assertEquals("Number of signingConfig", 2, signingConfigs.size())

        SigningConfig debugSigningConfig = ModelHelper.getSigningConfig(signingConfigs, DEBUG)
        assertNotNull("debug signing config null-check", debugSigningConfig)
        new SigningConfigHelper(debugSigningConfig, DEBUG, true).test()

        SigningConfig mySigningConfig = ModelHelper.getSigningConfig(signingConfigs, "myConfig")
        assertNotNull("myConfig signing config null-check", mySigningConfig)
        new SigningConfigHelper(mySigningConfig, "myConfig", true)
                .setStoreFile(new File(project.getTestDir(), "debug.keystore"))
                .test()
    }

    @Test
    void "check custom signing"() throws Exception {
        Collection<Variant> variants = model.getVariants();

        for (Variant variant : variants) {
            // Release variant doesn't specify the signing config, so it should not be considered
            // signed.
            if (variant.getName().equals("release")) {
                assertFalse(variant.getMainArtifact().isSigned());
            }

            // customSigning is identical to release, but overrides the signing check.
            if (variant.getName().equals("customSigning")) {
                assertTrue(variant.getMainArtifact().isSigned());
            }
        }
    }

    @Test
    void "check debug and release output have different names"() {
        ModelHelper.compareDebugAndReleaseOutput(model)
    }

    @Test
    @Category(DeviceTests.class)
    void install() {
        project.execute("installDebug", "uninstallAll")
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.execute("connectedCheck")
    }
}
