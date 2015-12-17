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

package com.android.build.gradle.integration.application
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.builder.model.AndroidProject
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.utils.FileHelper.searchAndReplace
/**
 * Ensures that archivesBaseName setting on android project is used when choosing the apk file
 * names
 */
@CompileStatic
@RunWith(FilterableParameterized)
class ArchivesBaseNameTest {
    private static final String OLD_NAME = "random_name"
    private static final String NEW_NAME = "changed_name"

     @Parameterized.Parameters(name = "{0}")
     public static Iterable<Object[]> data() {
         return [
                 ["com.android.application", "apk"].toArray(),
                 ["com.android.library", "aar"].toArray(),
         ]
     }

    @Rule
    public GradleTestProject project

    private String extension

    public ArchivesBaseNameTest(String plugin, String extension) {
        project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin(plugin))
            .create()

        this.extension = extension
    }

    @Test
    void testArtifactName() {
        checkApkName('project', extension)

        project.buildFile << """
            archivesBaseName = '$OLD_NAME'
        """
        checkApkName(OLD_NAME, extension)

        searchAndReplace(project.buildFile, OLD_NAME, NEW_NAME)
        checkApkName(NEW_NAME, extension)
    }

    private void checkApkName(String name, String extension) {
        AndroidProject model = project.executeAndReturnModel("assembleDebug")
        File outputFile = model.getVariants().find { it.name == "debug" }
                .getMainArtifact()
                .getOutputs().first()
                .getMainOutputFile()
                .getOutputFile()


        assertThat(outputFile.getName()).isEqualTo("$name-debug.$extension".toString())
        assertThat(outputFile).isFile()
    }
}
