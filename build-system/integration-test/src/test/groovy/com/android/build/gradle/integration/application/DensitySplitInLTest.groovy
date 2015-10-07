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
import com.android.build.OutputFile
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Sets
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.builder.core.BuilderConstants.DEBUG
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
 * Assemble tests for class densitySplitInL
 .
 */
class DensitySplitInLTest {

    static AndroidProject model

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("densitySplitInL")
            .create()

    @BeforeClass
    static void setUp() {
        GradleTestProject.assumeBuildToolsAtLeast(21)
        model = project.executeAndReturnModel("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void "check split outputs"() throws Exception {

        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(5)
        expected.add(null)
        expected.add("mdpi")
        expected.add("hdpi")
        expected.add("xhdpi")
        expected.add("xxhdpi")

        List<? extends OutputFile> outputs = getOutputs(model);
        assertThat(outputs).hasSize(5)
        for (OutputFile outputFile : outputs) {
            String densityFilter = ModelHelper.getFilter(outputFile, OutputFile.DENSITY)
            assertEquals(densityFilter == null ? OutputFile.MAIN : OutputFile.SPLIT,
                    outputFile.getOutputType())

            expected.remove(densityFilter)
        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty())
    }

    @Test
    void "check adding a density incrementally"() throws Exception {
        // get the last modified time of the initial APKs so we can make sure incremental build
        // does not rebuild things unnecessarily.
        Map<String, Long> lastModifiedTimePerDensity =
                getApkModifiedTimePerDensity(getOutputs(model));

        TemporaryProjectModification.doTest(project) {
            it.replaceInFile("build.gradle", "exclude \"ldpi\", \"tvdpi\", \"xxxhdpi\"",
                    "exclude \"ldpi\", \"tvdpi\"")
            AndroidProject incrementalModel = project.executeAndReturnModel("assembleDebug")

            List<? extends OutputFile> outputs = getOutputs(incrementalModel);
            assertThat(outputs).hasSize(6);
            boolean foundAddedAPK = false;
            for (OutputFile output : outputs) {

                String filter = ModelHelper.getFilter(output, OutputFile.DENSITY)

                if ("xxxhdpi".equals(filter)) {
                    // found our added density, done.
                    foundAddedAPK = true;
                } else {
                    // check that the APK was not rebuilt.
                    String key = output.getOutputType() + filter;
                    Long initialApkModifiedTime = lastModifiedTimePerDensity.get(key);
                    assertNotNull(
                            "Cannot find initial APK for density : " + filter,
                                initialApkModifiedTime);
                    // uncomment once PackageSplitRes is made incremental.
//                    assertTrue("APK should not have been rebuilt in incremental mode : " + filter,
//                            initialApkModifiedTime.longValue()
//                                    == output.getOutputFile().lastModified());
                }
            }
            if (!foundAddedAPK) {
                fail("Did not find the xxxhdpi density pure split in the outputs")
            }
        }
    }

    @Test
    void "check deleting a density incrementally"() throws Exception {

        // get the last modified time of the initial APKs so we can make sure incremental build
        // does not rebuild things unnecessarily.
        Map<String, Long> lastModifiedTimePerDensity =
                getApkModifiedTimePerDensity(getOutputs(model));

        TemporaryProjectModification.doTest(project) {
            it.replaceInFile("build.gradle", "exclude \"ldpi\", \"tvdpi\", \"xxxhdpi\"",
                    "exclude \"ldpi\", \"tvdpi\", \"xxxhdpi\", \"xxhdpi\"")
            AndroidProject incrementalModel = project.executeAndReturnModel("assembleDebug")

            List<? extends OutputFile> outputs = getOutputs(incrementalModel);
            assertThat(outputs).hasSize(4);
            for (OutputFile output : outputs) {
                String filter = ModelHelper.getFilter(output, OutputFile.DENSITY);
                if (filter == null) continue;
                if ("xxhdpi".equals(filter)) {
                    fail("Found deleted xxhdpi pure split split in the outputs")
                }  else {
                    // check that the APK was not rebuilt.
                    String key = output.getOutputType() + filter;
                    Long initialApkModifiedTime = lastModifiedTimePerDensity.get(key);
                    assertNotNull("Cannot find initial APK for density : " + filter,
                            initialApkModifiedTime);
                    // uncomment once PackageSplitRes is made incremental.
//                    assertTrue("APK should not have been rebuilt in incremental mode : " + filter,
//                            initialApkModifiedTime.longValue()
//                                    == output.getOutputFile().lastModified());
                }
            }
        }
    }

    private static Collection<? extends  OutputFile> getOutputs(AndroidProject projectModel) {
        Collection<Variant> variants = projectModel.getVariants()
        assertEquals("Variant Count", 2 , variants.size())

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG)
        assertNotNull("debug Variant null-check", debugVariant)
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact()
        assertNotNull("Debug main info null-check", debugMainArtifact)

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs()
        assertNotNull(debugOutputs)

        assertEquals(1, debugOutputs.size())
        AndroidArtifactOutput output = debugOutputs.iterator().next()

        // with pure splits, all split have the same version code.
        assertEquals(12, output.getVersionCode())

        return output.getOutputs()
    }

    @NonNull
    private static Map<String, Long> getApkModifiedTimePerDensity(
            Collection<? extends OutputFile> outputs) {
        ImmutableMap.Builder<String, Long> builder = ImmutableMap.builder();
        for (OutputFile output : outputs) {
            String key = output.getOutputType() + ModelHelper.getFilter(output, OutputFile.DENSITY);
            builder.put(key, output.getOutputFile().lastModified());
        }
        return builder.build();
    }

    @Nullable
    private OutputFile getAPK(List<? extends OutputFile> outputs, String abiFilter) {
        for (OutputFile output : outputs) {
            if (ModelHelper.getFilter(output, OutputFile.DENSITY).equals(abiFilter)) {
                return output;
            }
        }
        return null;
    }
}
