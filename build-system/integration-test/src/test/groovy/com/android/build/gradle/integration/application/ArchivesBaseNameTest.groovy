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
import com.android.builder.model.AndroidProject
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.utils.FileHelper.searchAndReplace
/**
 * Ensures that archivesBaseName setting on android project is used when choosing the apk file
 * names
 */
@CompileStatic
class ArchivesBaseNameTest {
    private static final String OLD_NAME = "random_apk_name"
    private static final String NEW_NAME = "changed_name"


    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("basic")
            .create()

    @Before
    void setUp() {
        project.getBuildFile() << """
android {
    archivesBaseName = '$OLD_NAME'
}
"""
    }

    @Test
    void "check model failed to load"() {
        checkApkName(OLD_NAME)

        searchAndReplace(project.buildFile, OLD_NAME, NEW_NAME)
        checkApkName(NEW_NAME)
    }

    private void checkApkName(String apkName) {
        AndroidProject model = project.executeAndReturnModel("assembleDebug")
        File outputFile = model.getVariants().find { it.name == "debug" }
                .getMainArtifact()
                .getOutputs().first()
                .getMainOutputFile()
                .getOutputFile()


        assertThat(outputFile.getName()).startsWith(apkName)
        assertThat(outputFile).isFile()
    }
}
