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

package com.android.build.gradle.integration.dependencies
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidProject
import com.android.builder.model.SyncIssue
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
/**
 * Tests the handling of test dependencies.
 */
class TestWithMismatchDep {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("testDependency")
            .create()

    @Test
    public void "Test with lower dep version than Tested"() {
        project.getBuildFile() << """
dependencies {
    androidTestCompile 'com.google.guava:guava:17.0'
}
"""
        // no need to do a full build. Let's just run the manifest task.
        AndroidProject model = project.getSingleModel()

        assertThat(model).issues().hasSingleIssue(
                SyncIssue.SEVERITY_ERROR,
                SyncIssue.TYPE_MISMATCH_DEP,
                'com.google.guava:guava',
                'Conflict with dependency \'com.google.guava:guava\'. Resolved versions for app and test app differ.')
    }
}