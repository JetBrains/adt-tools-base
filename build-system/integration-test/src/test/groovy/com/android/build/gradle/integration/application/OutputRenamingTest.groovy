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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.builder.core.BuilderConstants.DEBUG
import static com.android.builder.core.BuilderConstants.RELEASE
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

/**
 * Assemble tests for class densitySplitInL
 */
class OutputRenamingTest {

    static AndroidProject model

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("densitySplitInL")
            .create()

    @BeforeClass
    static void setup() {
        GradleTestProject.assumeBuildToolsAtLeast(21)
        project.getBuildFile() << """android {
applicationVariants.all { variant ->
    // Custom APK names (do not do this for 'dev' build type)
    println variant.buildType.name
    def baseFileName = "project-\${variant.flavorName}-\${variant.versionCode}-\${variant.buildType.name}"
    variant.outputs.each { output ->
      // Unaligned (name it 'unsigned' because we remove the signature from the apk later)
      def unalignedFileName = "\${baseFileName}-unsigned.apk"
      println "renaming from \${output.packageApplication.outputFile} to \${unalignedFileName}"
      File parent = output.packageApplication.outputFile.parentFile
      output.packageApplication.outputFile = new File(parent, unalignedFileName)
      // Signed
      def signedFileName = "\${baseFileName}-signed.apk"
      parent = output.outputFile.parentFile
      output.outputFile = new File(parent, signedFileName)
    }
  }
        }"""
        model = project.executeAndReturnModel("clean", "assemble")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void "check split outputs"() throws Exception {
        Collection<Variant> variants = model.getVariants()
        assertEquals("Variant Count", 2 , variants.size())

        assertFileRenaming(DEBUG)
        assertFileRenaming(RELEASE)
    }

    private assertFileRenaming(String buildType) {
        Variant variant = ModelHelper.getVariant(model.getVariants(), buildType)
        assertNotNull("Variant null-check", variant)
        AndroidArtifact mainArtifact = variant.getMainArtifact()
        assertNotNull("main info null-check", mainArtifact)

        // get the outputs.
        Collection<AndroidArtifactOutput> outputs = mainArtifact.getOutputs()
        assertNotNull(outputs)

        assertEquals(1, outputs.size())
        AndroidArtifactOutput output = outputs.iterator().next()
        assertEquals(5, output.getOutputs().size())

        String expectedFileName = "project--12-"+ buildType.toLowerCase() + "-signed.apk"
        File mainOutputFile = output.getMainOutputFile().getOutputFile();
        assertEquals(expectedFileName, mainOutputFile.name)
        assertTrue(mainOutputFile.exists());
    }
}
