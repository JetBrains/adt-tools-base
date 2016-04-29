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

package com.android.build.gradle.internal.externalBuild;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;

/**
 * Tests for the {@link ExternalBuildTask}
 */
public class ExternalBuildTaskTest {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Mock
    ExternalBuildExtension mExternalBuildExtension;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void externalBuildTaskConfigurationTest() throws IOException {

        when(mExternalBuildExtension.getManifestPath()).thenReturn("/tmp/foo/bar");

        ExternalBuildTask.ConfigAction configAction = new ExternalBuildTask.ConfigAction(
                tmpFolder.getRoot(),
                mExternalBuildExtension);
        Project project = ProjectBuilder.builder().build();
        ExternalBuildTask task = project.getTasks().create("task", ExternalBuildTask.class);
        configAction.execute(task);

        assertThat(task.getManifestPath()).isEqualTo("/tmp/foo/bar");
    }

    @Test
    public void externalBuildTaskExecutionTest() throws IOException {

        when(mExternalBuildExtension.getManifestPath()).thenReturn("/tmp/foo/bar");

        ExternalBuildTask.ConfigAction configAction = new ExternalBuildTask.ConfigAction(
                tmpFolder.getRoot(),
                mExternalBuildExtension);
        Project project = ProjectBuilder.builder().build();
        ExternalBuildTask task = project.getTasks().create("task", ExternalBuildTask.class);
        configAction.execute(task);

        // now execute the task.
        task.execute();

        File outputFile = new File(new File(tmpFolder.getRoot(), "outputs"), "build");
        assertThat(outputFile.isFile()).isTrue();
        assertThat(Files.readFirstLine(outputFile, Charsets.UTF_8)).isEqualTo("/tmp/foo/bar");
    }
}
