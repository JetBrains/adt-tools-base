/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.externalBuild

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static com.google.common.truth.Truth.assertThat
/**
 * Basic tests for Blaze plugin instantiation, extension instantiation and tasks population.
 */
public class ExternalBuildPluginTest {

    @Test
    public void externalBuildExtensionInstantiation() {
        Project project = createAndConfigureBasicProject()
        assertThat(project.extensions.externalBuild instanceof ExternalBuildExtension).isTrue();
    }

    @Test
    public void externalBuildExtensionPopulation() {
        Project project = createAndConfigureBasicProject()
        assertThat(project.extensions.externalBuild.getBuildManifestPath()).isEqualTo('/usr/tmp/foo')
        assertThat(project.extensions.externalBuild.getExecutionRoot()).isEqualTo("/Users/user/project")
    }

    private static Project createBasicProject() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'com.android.external.build'
        return project;
    }

    private void configureBasicProject(Project project) {
        project.externalBuild {
            executionRoot = '/Users/user/project'
            buildManifestPath = '/usr/tmp/foo'
        }
    }

    private Project createAndConfigureBasicProject() {
        Project project = createBasicProject();
        configureBasicProject(project);
        return project;
    }
}
