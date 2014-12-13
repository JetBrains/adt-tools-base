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

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.build.gradle.integration.common.utils.ZipHelper
import com.android.builder.core.ApkInfoParser
import com.android.builder.model.*
import com.android.ide.common.internal.CommandLineRunner
import com.android.utils.StdLogger
import com.google.common.collect.Sets
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static org.junit.Assert.*

class VariantDependencyTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder().create()

    private static AndroidProject model
    private static ApkInfoParser apkInfoParser

    @BeforeClass
    public static void setUp() {
        new HelloWorldApp().writeSources(project.testDir)
        project.getBuildFile() << """
            apply plugin: 'com.android.application'

            configurations {
                freeLollipopDebugCompile
                paidIcsCompile
            }

            android {
                compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

                flavorDimensions 'model', 'api'
                productFlavors {
                    Lollipop {
                        flavorDimension 'api'
                        minSdkVersion 21
                    }
                    ics {
                        flavorDimension 'api'
                        minSdkVersion 15
                    }
                    free {
                        flavorDimension 'model'
                    }
                    paid {
                        flavorDimension 'model'
                    }
                }
            }

            dependencies {
                freeLollipopDebugCompile 'com.android.support:leanback-v17:21.0.0'
                paidIcsCompile 'com.android.support:appcompat-v7:21.0.0'
            }
            """.stripIndent()

        project.execute('clean', 'assemble')
        model = project.getSingleModel()

        File aapt = new File(project.getSdkDir(), "build-tools/20.0.0/aapt");
        assertTrue("Test requires build-tools 20.0.0", aapt.isFile());
        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));
        apkInfoParser = new ApkInfoParser(aapt, commandLineRunner);
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
        apkInfoParser = null
    }

    @Test
    public void buildVariantSpecificDependency() {
        // check that the dependency was added by looking for a res file coming from the
        // dependency.
        checkApkForContent('freeLollipopDebug', 'res/drawable/lb_background.xml')
    }

    @Test
    public void buildMultiFlavorDependency() {
        // check that the dependency was added by looking for a res file coming from the
        // dependency.
        checkApkForContent('paidIcsDebug', 'res/anim/abc_fade_in.xml')
        checkApkForContent('paidIcsRelease', 'res/anim/abc_fade_in.xml')
    }

    @Test
    public void buildDefaultDependency() {
        // make sure that the other variants do not include any file from the variant-specific
        // and multi-flavor dependencies.
        Set<String> paths = Sets.newHashSet(
                'res/anim/abc_fade_in.xml',
                'res/drawable/lb_background.xml')

        checkApkForMissingContent('paidLollipopDebug', paths)
        checkApkForMissingContent('paidLollipopRelease', paths)
        checkApkForMissingContent('freeLollipopRelease', paths)
        checkApkForMissingContent('freeIcsDebug', paths)
        checkApkForMissingContent('freeIcsRelease', paths)
    }

    @Test
    public void modelVariantCount() {
        Collection<Variant> variants = model.getVariants()
        assertEquals("Variant Count", 8 , variants.size())
    }

    @Test
    public void modelVariantSpecificDependency() {
        Collection<Variant> variants = model.getVariants()
        String variantName = 'freeLollipopDebug'
        checkVariant(variants, variantName, 'com.android.support:leanback-v17:21.0.0')
    }

    @Test
    public void modelMultiFlavorDependency() {
        Collection<Variant> variants = model.getVariants()

        String variantName = 'paidIcsDebug'
        checkVariant(variants, variantName, 'com.android.support:appcompat-v7:21.0.0')

        variantName = 'paidIcsRelease'
        checkVariant(variants, variantName, 'com.android.support:appcompat-v7:21.0.0')
    }

    @Test
    public void modelDefaultDependency() {
        Collection<Variant> variants = model.getVariants()

        String variantName = 'paidLollipopDebug'
        checkVariant(variants, variantName, null)

        variantName = 'paidLollipopRelease'
        checkVariant(variants, variantName, null)

        variantName = 'freeLollipopRelease'
        checkVariant(variants, variantName, null)

        variantName = 'freeIcsDebug'
        checkVariant(variants, variantName, null)

        variantName = 'freeIcsRelease'
        checkVariant(variants, variantName, null)
    }

    private static void checkVariant(
            @NonNull Collection<Variant> variants,
            @NonNull String variantName,
            @Nullable String dependencyName) {
        Variant variant = ModelHelper.findVariantByName(variants, variantName)
        assertNotNull("${variantName} variant null-check", variant)

        AndroidArtifact artifact = variant.getMainArtifact()
        assertNotNull("${variantName} main artifact null-check", artifact)

        Dependencies dependencies = artifact.getDependencies()
        assertNotNull("${variantName} dependencies null-check", artifact)

        if (dependencyName != null) {
            assertFalse("${variantName} aar deps empty",
                    dependencies.libraries.isEmpty())

            AndroidLibrary library = dependencies.libraries.iterator().next()
            assertNotNull("${variantName} first aar lib null-check", library)

            MavenCoordinates coordinates = library.resolvedCoordinates
            assertNotNull("${variantName} first aar lib coordinate null-check", coordinates)
            assertEquals("${variantName} first aar lib name check",
                    dependencyName,
                    "${coordinates.groupId}:${coordinates.artifactId}:${coordinates.version}".toString())
        } else {
            assertTrue("${variantName} aar deps empty",
                    dependencies.libraries.isEmpty())
        }
    }

    private static void checkApkForContent(
            @NonNull String variantName,
            @NonNull String checkFilePath) {
        // use the model to get the output APK!
        File apk = ModelHelper.findOutputFileByVariantName(model.getVariants(), variantName)

        assertTrue("${variantName} output check", apk.isFile())

        ZipHelper.checkFileExists(apk, checkFilePath)
    }

    private static void checkApkForMissingContent(
            @NonNull String variantName,
            @NonNull Set<String> checkFilePath) {
        // use the model to get the output APK!
        File apk = ModelHelper.findOutputFileByVariantName(model.getVariants(), variantName)

        assertTrue("${variantName} output check", apk.isFile())

        ZipHelper.checkFileDoesNotExist(apk, checkFilePath)
    }
}
