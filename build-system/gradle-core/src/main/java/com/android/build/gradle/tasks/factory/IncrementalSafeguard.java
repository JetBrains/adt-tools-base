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

package com.android.build.gradle.tasks.factory;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Pre-task for {@link AndroidJavaCompile} that will use all generated source folders as inputs.
 * if any generated source file has changed, we wipe out the AndroidJavaCompile output folder to
 * force the recompilation.
 */
public class IncrementalSafeguard extends BaseTask {

    File javaOutputDir;
    protected List<ConfigurableFileTree> source = new ArrayList<ConfigurableFileTree>();

    // create an output directory so the task can be incremental.
    // we will end up storing a marker file.
    @OutputDirectory
    File getGeneratedOutputDir() {
        return generatedOutputDir;
    }

    File generatedOutputDir;

    @InputFiles
    @SkipWhenEmpty
    public FileTree getSource() {
        ArrayList<Object> copy = new ArrayList<Object>(this.source);
        return this.getProject().files(copy).getAsFileTree();
    }

    /**
     * Incremental task will only execute when any of the generated files (in particular R.java)
     * has changed.
     *
     * @param inputs the incremental changes as provided by Gradle.
     * @throws IOException if something went wrong cleaning up the javac compilation output.
     */
    @TaskAction
    protected void execute(IncrementalTaskInputs inputs) throws IOException {
        getLogger().debug("Removing old bits to force javac non incremental mode.");
        File outputFile = new File(generatedOutputDir, "tag.txt");
        Files.createParentDirs(outputFile);
        Files.write("incremental task execution", outputFile, Charsets.UTF_8);
        // since we execute, force the recompilation of all the user's classes.
        try {
            FileUtils.deletePath(javaOutputDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class ConfigAction implements TaskConfigAction<IncrementalSafeguard> {

        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("incremental", "JavaCompilationSafeguard");
        }

        @NonNull
        @Override
        public Class<IncrementalSafeguard> getType() {
            return IncrementalSafeguard.class;
        }

        @Override
        public void execute(@NonNull IncrementalSafeguard task) {

            task.setVariantName(scope.getVariantConfiguration().getFullName());
            task.javaOutputDir = scope.getJavaOutputDir();

            // this does not need to be shared.
            task.generatedOutputDir = new File(
                    scope.getGlobalScope().getIntermediatesDir(), "/incremental-safeguard/" +
                    scope.getVariantData().getVariantConfiguration().getDirName());

            // we only depend on the generated java sources.
            task.source = scope.getVariantData().getGeneratedJavaSources();
        }
    }
}
