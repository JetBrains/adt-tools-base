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

package com.android.build.gradle.internal.tasks;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.builder.model.AndroidProject;
import com.android.builder.testing.MockableJarGenerator;
import com.android.sdklib.IAndroidTarget;
import com.google.common.base.CharMatcher;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Task for generating a mockable android.jar
 */
@ParallelizableTask
public class MockableAndroidJarTask extends DefaultTask {

    private File mAndroidJar;

    private File mOutputFile;

    /**
     * Whether the generated jar should return default values from all methods or throw exceptions.
     */
    private boolean mReturnDefaultValues;

    @TaskAction
    public void createMockableJar() throws IOException {
        File outputFile = getOutputFile();
        if (outputFile.exists()) {
            // Modules share the mockable jar, all the "inputs" are reflected in the filename,
            // e.g. mockable-android-22.default-values.jar. If we ever change the generator logic,
            // it will be reflected in the name as well.
            //
            // This is not how Gradle understands tasks with overlapping outputs - it will run
            // all instances of this task, because the output was not created by this instance. We
            // need to return here manually because of that behavior.
            return;
        }
        MockableJarGenerator generator = new MockableJarGenerator(getReturnDefaultValues());
        getLogger().info(String.format("Creating %s from %s.", outputFile.getAbsolutePath(),
                getAndroidJar().getAbsolutePath()));
        generator.createMockableJar(getAndroidJar(), outputFile);
    }

    @Input
    public boolean getReturnDefaultValues() {
        return mReturnDefaultValues;
    }

    public void setReturnDefaultValues(boolean returnDefaultValues) {
        mReturnDefaultValues = returnDefaultValues;
    }

    @OutputFile
    public File getOutputFile() {
        return mOutputFile;
    }

    public void setOutputFile(File outputFile) {
        mOutputFile = outputFile;
    }

    @InputFile
    public File getAndroidJar() {
        return mAndroidJar;
    }

    public void setAndroidJar(File androidJar) {
        mAndroidJar = androidJar;
    }

    public static class ConfigAction implements TaskConfigAction<MockableAndroidJarTask> {

        final GlobalScope scope;

        public ConfigAction(GlobalScope scope) {
            this.scope = scope;
        }

        @Override
        public String getName() {
            return "mockableAndroidJar";
        }

        @Override
        public Class<MockableAndroidJarTask> getType() {
            return MockableAndroidJarTask.class;
        }

        @Override
        public void execute(final MockableAndroidJarTask task) {
            task.setGroup(TaskManager.BUILD_GROUP);
            task.setDescription(
                    "Creates a version of android.jar that's suitable for unit tests.");
            task.setReturnDefaultValues(
                    scope.getExtension().getTestOptions().getUnitTests().isReturnDefaultValues());

            ConventionMappingHelper.map(task, "androidJar", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    checkNotNull(scope.getAndroidBuilder().getTarget(), "ensureTargetSetup not called");
                    return new File(
                            scope.getAndroidBuilder().getTarget().getPath(IAndroidTarget.ANDROID_JAR));
                }
            });

            task.setOutputFile(scope.getMockableAndroidJarFile());
        }
    }
}
