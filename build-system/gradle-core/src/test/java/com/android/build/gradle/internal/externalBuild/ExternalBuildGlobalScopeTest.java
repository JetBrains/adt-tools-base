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

import com.android.builder.model.AndroidProject;
import com.android.builder.model.OptionalCompilationStep;

import org.gradle.api.Project;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

/**
 * Tests for the {@link ExternalBuildGlobalScope} class
 */
public class ExternalBuildGlobalScopeTest {

    @Mock
    Project project;

    ExternalBuildGlobalScope scope;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBuildDir() {
        when(project.getBuildDir()).thenReturn(new File("/tmp/out/folder"));
        scope = new ExternalBuildGlobalScope(project);
        assertThat(scope.getBuildDir().getPath()).isEqualTo(
                "/tmp/out/folder".replace('/', File.separatorChar));
    }


    @Test
    public void testIsActive() {
        when(project.hasProperty(AndroidProject.OPTIONAL_COMPILATION_STEPS)).thenReturn(true);
        when(project.property(AndroidProject.OPTIONAL_COMPILATION_STEPS)).thenReturn("INSTANT_DEV");
        scope = new ExternalBuildGlobalScope(project);

        assertThat(scope.isActive(OptionalCompilationStep.INSTANT_DEV)).isTrue();
        assertThat(scope.isActive(OptionalCompilationStep.LOCAL_JAVA_ONLY)).isFalse();
    }
}
