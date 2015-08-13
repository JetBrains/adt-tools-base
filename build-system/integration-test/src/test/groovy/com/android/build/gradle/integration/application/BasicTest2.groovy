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
import com.android.build.gradle.integration.common.utils.ProductFlavorHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.ClassField
import com.android.builder.model.Dependencies
import com.android.builder.model.JavaLibrary
import com.android.builder.model.MavenCoordinates
import com.android.builder.model.ProductFlavor
import com.android.builder.model.Variant
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.builder.core.BuilderConstants.DEBUG
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

/**
 * Assemble tests for basic that loads the model but doesn't build.
 */
@CompileStatic
class BasicTest2 {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("basic")
            .create()

    static public AndroidProject model

    @BeforeClass
    static void setUp() {
        model = project.executeAndReturnModel("clean")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    public void "check variant details"() throws Exception {
        Collection<Variant> variants = model.getVariants()
        assertEquals("Variant Count", 2 , variants.size())

        // debug variant
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG)
        assertNotNull("debug Variant null-check", debugVariant)
        new ProductFlavorHelper(debugVariant.getMergedFlavor(), "Debug Merged Flavor")
                .setVersionCode(12)
                .setVersionName("2.0")
                .setMinSdkVersion(16)
                .setTargetSdkVersion(16)
                .setTestInstrumentationRunner("android.test.InstrumentationTestRunner")
                .setTestHandleProfiling(Boolean.FALSE)
                .setTestFunctionalTest(null)
                .test()

        // debug variant, tested.
        AndroidArtifact debugMainInfo = debugVariant.getMainArtifact()
        assertNotNull("Debug main info null-check", debugMainInfo)
        assertEquals("Debug package name", "com.android.tests.basic.debug",
                debugMainInfo.getApplicationId())
        assertTrue("Debug signed check", debugMainInfo.isSigned())
        assertEquals("Debug signingConfig name", "myConfig", debugMainInfo.getSigningConfigName())
        assertEquals("Debug sourceGenTask", "generateDebugSources", debugMainInfo.getSourceGenTaskName())
        assertEquals("Debug compileTask", "compileDebugSources", debugMainInfo.getCompileTaskName())

        Collection<AndroidArtifactOutput> debugMainOutputs = debugMainInfo.getOutputs()
        assertNotNull("Debug main output null-check", debugMainOutputs)
        assertEquals("Debug main output size", 1, debugMainOutputs.size())
        AndroidArtifactOutput debugMainOutput = debugMainOutputs.iterator().next()
        assertNotNull(debugMainOutput)
        assertNotNull(debugMainOutput.getMainOutputFile())
        assertNotNull(debugMainOutput.getAssembleTaskName())
        assertNotNull(debugMainOutput.getGeneratedManifest())
        assertEquals(12, debugMainOutput.getVersionCode())

        // check debug dependencies
        Dependencies debugDependencies = debugMainInfo.getDependencies()
        assertNotNull(debugDependencies)
        Collection<AndroidLibrary> debugLibraries = debugDependencies.getLibraries()
        assertNotNull(debugLibraries)
        assertEquals(1, debugLibraries.size())
        assertTrue(debugDependencies.getProjects().isEmpty())

        AndroidLibrary androidLibrary = debugLibraries.iterator().next()
        assertNotNull(androidLibrary)
        assertNotNull(androidLibrary.getBundle())
        assertNotNull(androidLibrary.getFolder())
        MavenCoordinates coord = androidLibrary.getResolvedCoordinates()
        assertNotNull(coord)
        assertEquals("com.google.android.gms:play-services:3.1.36",
                coord.getGroupId() + ":" + coord.getArtifactId() + ":" + coord.getVersion())


        Collection<JavaLibrary> javaLibraries = debugDependencies.getJavaLibraries()
        assertNotNull(javaLibraries)
        assertEquals(2, javaLibraries.size())

        Set<String> javaLibs = Sets.newHashSet(
                "com.android.support:support-v13:13.0.0",
                "com.android.support:support-v4:13.0.0"
        )

        for (JavaLibrary javaLib : javaLibraries) {
            coord = javaLib.getResolvedCoordinates()
            assertNotNull(coord)
            String lib = coord.getGroupId() + ":" + coord.getArtifactId() + ":" + coord.getVersion()
            assertTrue(javaLibs.contains(lib))
            javaLibs.remove(lib)
        }

        // this variant is tested.
        Collection<AndroidArtifact> debugExtraAndroidArtifacts = debugVariant.getExtraAndroidArtifacts()
        AndroidArtifact debugTestInfo = ModelHelper.getAndroidArtifact(debugExtraAndroidArtifacts,
                ARTIFACT_ANDROID_TEST)
        assertNotNull("Test info null-check", debugTestInfo)
        assertEquals("Test package name", "com.android.tests.basic.debug.test",
                debugTestInfo.getApplicationId())
        assertTrue("Test signed check", debugTestInfo.isSigned())
        assertEquals("Test signingConfig name", "myConfig", debugTestInfo.getSigningConfigName())
        assertEquals("Test sourceGenTask", "generateDebugAndroidTestSources", debugTestInfo.getSourceGenTaskName())
        assertEquals("Test compileTask", "compileDebugAndroidTestSources", debugTestInfo.getCompileTaskName())

        Collection<File> generatedResFolders = debugTestInfo.getGeneratedResourceFolders()
        assertNotNull(generatedResFolders)
        // size 2 = rs output + resValue output
        assertEquals(2, generatedResFolders.size())

        Collection<AndroidArtifactOutput> debugTestOutputs = debugTestInfo.getOutputs()
        assertNotNull("Debug test output null-check", debugTestOutputs)
        assertEquals("Debug test output size", 1, debugTestOutputs.size())
        AndroidArtifactOutput debugTestOutput = debugTestOutputs.iterator().next()
        assertNotNull(debugTestOutput)
        assertNotNull(debugTestOutput.getMainOutputFile())
        assertNotNull(debugTestOutput.getAssembleTaskName())
        assertNotNull(debugTestOutput.getGeneratedManifest())

        // test the resValues and buildConfigFields.
        ProductFlavor defaultConfig = model.getDefaultConfig().getProductFlavor()
        Map<String, ClassField> buildConfigFields = defaultConfig.getBuildConfigFields()
        assertNotNull(buildConfigFields)
        assertEquals(2, buildConfigFields.size())

        assertEquals("true", buildConfigFields.get("DEFAULT").getValue())
        assertEquals("\"foo2\"", buildConfigFields.get("FOO").getValue())

        Map<String, ClassField> resValues = defaultConfig.getResValues()
        assertNotNull(resValues)
        assertEquals(1, resValues.size())

        assertEquals("foo", resValues.get("foo").getValue())

        // test on the debug build type.
        Collection<BuildTypeContainer> buildTypes = model.getBuildTypes()
        for (BuildTypeContainer buildTypeContainer : buildTypes) {
            if (buildTypeContainer.getBuildType().getName().equals(DEBUG)) {
                buildConfigFields = buildTypeContainer.getBuildType().getBuildConfigFields()
                assertNotNull(buildConfigFields)
                assertEquals(1, buildConfigFields.size())

                assertEquals("\"bar\"", buildConfigFields.get("FOO").getValue())

                resValues = buildTypeContainer.getBuildType().getResValues()
                assertNotNull(resValues)
                assertEquals(1, resValues.size())

                assertEquals("foo2", resValues.get("foo").getValue())
            }
        }

        // now test the merged flavor
        ProductFlavor mergedFlavor = debugVariant.getMergedFlavor()

        buildConfigFields = mergedFlavor.getBuildConfigFields()
        assertNotNull(buildConfigFields)
        assertEquals(2, buildConfigFields.size())

        assertEquals("true", buildConfigFields.get("DEFAULT").getValue())
        assertEquals("\"foo2\"", buildConfigFields.get("FOO").getValue())

        resValues = mergedFlavor.getResValues()
        assertNotNull(resValues)
        assertEquals(1, resValues.size())

        assertEquals("foo", resValues.get("foo").getValue())


        // release variant, not tested.
        Variant releaseVariant = ModelHelper.getVariant(variants, "release")
        assertNotNull("release Variant null-check", releaseVariant)

        AndroidArtifact relMainInfo = releaseVariant.getMainArtifact()
        assertNotNull("Release main info null-check", relMainInfo)
        assertEquals("Release package name", "com.android.tests.basic",
                relMainInfo.getApplicationId())
        assertFalse("Release signed check", relMainInfo.isSigned())
        assertNull("Release signingConfig name", relMainInfo.getSigningConfigName())
        assertEquals("Release sourceGenTask", "generateReleaseSources", relMainInfo.getSourceGenTaskName())
        assertEquals("Release javaCompileTask", "compileReleaseSources", relMainInfo.getCompileTaskName())

        Collection<AndroidArtifactOutput> relMainOutputs = relMainInfo.getOutputs()
        assertNotNull("Rel Main output null-check", relMainOutputs)
        assertEquals("Rel Main output size", 1, relMainOutputs.size())
        AndroidArtifactOutput relMainOutput = relMainOutputs.iterator().next()
        assertNotNull(relMainOutput)
        assertNotNull(relMainOutput.getMainOutputFile())
        assertNotNull(relMainOutput.getAssembleTaskName())
        assertNotNull(relMainOutput.getGeneratedManifest())
        assertEquals(13, relMainOutput.getVersionCode())


        Collection<AndroidArtifact> releaseExtraAndroidArtifacts = releaseVariant.getExtraAndroidArtifacts()
        AndroidArtifact relTestInfo = ModelHelper.getAndroidArtifact(releaseExtraAndroidArtifacts, ARTIFACT_ANDROID_TEST)
        assertNull("Release test info null-check", relTestInfo)

        // check release dependencies
        Dependencies releaseDependencies = relMainInfo.getDependencies()
        assertNotNull(releaseDependencies)
        Collection<AndroidLibrary> releaseLibraries = releaseDependencies.getLibraries()
        assertNotNull(releaseLibraries)
        assertEquals(3, releaseLibraries.size())

        // map for each aar we expect to find and how many local jars they each have.
        Map<String, Integer> aarLibs = Maps.newHashMapWithExpectedSize(3)
        aarLibs.put("com.android.support:support-v13:21.0.0", 1)
        aarLibs.put("com.android.support:support-v4:21.0.0", 1)
        aarLibs.put("com.google.android.gms:play-services:3.1.36", 0)
        for (AndroidLibrary androidLib : releaseLibraries) {
            assertNotNull(androidLib.getBundle())
            assertNotNull(androidLib.getFolder())
            coord = androidLib.getResolvedCoordinates()
            assertNotNull(coord)
            String lib = coord.getGroupId() + ":" + coord.getArtifactId() + ":" + coord.getVersion()

            Integer localJarCount = aarLibs.get(lib)
            assertNotNull("Check presense of " + lib, localJarCount)
            assertEquals("Check local jar count for " + lib,
                    localJarCount.intValue(), androidLib.getLocalJars().size())
            System.out.println(">>" + androidLib.getLocalJars())
            aarLibs.remove(lib)
        }

        assertTrue("check for missing libs", aarLibs.isEmpty())
    }


    @Test
    @Category(DeviceTests.class)
    void install() {
        GradleTestProject.assumeLocalDevice();
        project.execute("installDebug", "uninstallAll")
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck()
    }
}
