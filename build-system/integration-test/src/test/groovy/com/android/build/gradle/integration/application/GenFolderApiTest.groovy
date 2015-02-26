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
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
/**
 * Assemble tests for genFolderApi.
 */
class GenFolderApiTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("genFolderApi")
            .create()
    static AndroidProject model

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
    void lint() {
        project.execute("lint")
    }

    @Test
    void "check the custom java generation task ran"() throws Exception {
        assertThatApk(project.getApk("debug")).containsClass("Lcom/custom/Foo;")
    }

    @Test
    void "check the custom res generation task ran"() throws Exception {
        assertThatZip(project.getApk("debug")).contains("res/xml/generated.xml")
    }

    @Test
    void "check Java folder in Model"() throws Exception {
        File projectDir = project.getTestDir()

        File buildDir = new File(projectDir, "build")

        for (Variant variant : model.getVariants()) {

            AndroidArtifact mainInfo = variant.getMainArtifact()
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(),
                    mainInfo)

            // get the generated source folders.
            Collection<File> genSourceFolder = mainInfo.getGeneratedSourceFolders()

            // We're looking for a custom folder
            String sourceFolderStart = new File(buildDir, "customCode").getAbsolutePath() + File.separatorChar
            boolean found = false
            for (File f : genSourceFolder) {
                if (f.getAbsolutePath().startsWith(sourceFolderStart)) {
                    found = true
                    break
                }
            }

            assertTrue("custom generated source folder check", found)
        }
    }

    @Test
    void "check Res Folder in Model"() throws Exception {
        File projectDir = project.getTestDir()

        File buildDir = new File(projectDir, "build")

        for (Variant variant : model.getVariants()) {

            AndroidArtifact mainInfo = variant.getMainArtifact()
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(),
                    mainInfo)

            // get the generated res folders.
            Collection<File> genResFolder = mainInfo.getGeneratedResourceFolders()
            String resFolderStart = new File(buildDir, "customRes").getAbsolutePath() + File.separatorChar
            boolean found = false
            for (File f : genResFolder) {
                if (f.getAbsolutePath().startsWith(resFolderStart)) {
                    found = true
                    break
                }
            }

            assertTrue("custom generated res folder check", found)
        }
    }
}
