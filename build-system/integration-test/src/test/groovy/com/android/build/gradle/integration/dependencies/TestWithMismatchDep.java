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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.fail;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
/**
 * Tests the handling of test dependencies.
 */
public class TestWithMismatchDep {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("testDependency")
            .create();

    @Before
    public void setUp() throws IOException {
        appendToFile(project.getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    androidTestCompile 'com.google.guava:guava:15.0'\n" +
                "}\n");
    }

    private static final String ERROR_MSG = "Conflict with dependency \'com.google.guava:guava\'." +
            " Resolved versions for app (17.0) and test app (15.0) differ." +
            " See http://g.co/androidstudio/app-test-app-conflict for details.";

    @Test
    @Ignore
    public void testMismatchDependencyErrorIsInTheModel() {
        // Query the model to get the mismatch dep sync error.
        AndroidProject model = project.model().ignoreSyncIssues().getSingle();

        assertThat(model).hasSingleIssue(
                SyncIssue.SEVERITY_ERROR,
                SyncIssue.TYPE_MISMATCH_DEP,
                "com.google.guava:guava",
                ERROR_MSG);
    }

    @Test
    public void testMismatchDependencyBreaksTestBuild() {
        // want to check the log, so can't use Junit's expected exception mechanism.

        GradleBuildResult result =
                project.executor().expectFailure().run("assembleAndroidTest");
        Throwable t = result.getException();
        while (t.getCause() != null) {
            t = t.getCause();
        }

        // looks like we can't actually test the instance t against GradleException
        // due to it coming through the tooling API from a different class loader.
        assertThat(t.getClass().getCanonicalName()).isEqualTo("org.gradle.api.GradleException");
        assertThat(t.getMessage()).isEqualTo("Dependency Error. See console for details.");


        // check there is a version of the error, after the task name:
        assertThat(result.getStderr()).named("stderr").contains(ERROR_MSG);

    }

    @Test
    public void testMismatchDependencyDoesNotBreakDebugBuild() {
        project.execute("assembleDebug");

        // check there is a log output
        assertThat(project.getStdout()).named("stdout").contains(ERROR_MSG);
    }

    @Test
    public void testMismatchDependencyCanRunNonBuildTasks() {
        // it's important to be able to run the dependencies task to
        // investigate dependency issues.
        project.execute("dependencies");

        // check there is a log output
        assertThat(project.getStdout()).named("stdout").contains(ERROR_MSG);
    }
}
