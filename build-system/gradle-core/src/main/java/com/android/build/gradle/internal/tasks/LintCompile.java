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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.utils.FileUtils;

import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

/**
 * This is a stub task.
 *
 * TODO - should compile src/lint/java from src/lint/java and jar it into build/lint/lint.jar
 */
public class LintCompile extends BaseTask {

    private File outputDirectory;

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @TaskAction
    public void compile() {
        // TODO
        FileUtils.mkdirs(getOutputDirectory());
    }


    public static class ConfigAction implements TaskConfigAction<LintCompile> {

        private final GlobalScope globalScope;

        public ConfigAction(@NonNull GlobalScope globalScope) {
            this.globalScope = globalScope;
        }

        @NonNull
        @Override
        public String getName() {
            return "compileLint";
        }

        @NonNull
        @Override
        public Class<LintCompile> getType() {
            return LintCompile.class;
        }

        @Override
        public void execute(@NonNull LintCompile task) {
            task.setOutputDirectory(new File(globalScope.getIntermediatesDir(), "lint"));
            task.setVariantName("");
        }
    }
}
