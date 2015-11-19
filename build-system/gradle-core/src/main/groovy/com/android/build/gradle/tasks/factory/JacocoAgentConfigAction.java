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
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.coverage.JacocoPlugin;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.google.common.collect.Lists;

import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.Copy;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Configuration Action for the Jacoco agent unzip task.
 */
public class JacocoAgentConfigAction implements TaskConfigAction<Copy> {

    @NonNull
    private final GlobalScope scope;

    public JacocoAgentConfigAction(@NonNull GlobalScope scope) {
        this.scope = scope;
    }

    @NonNull
    @Override
    public String getName() {
        return "unzipJacocoAgent";
    }

    @NonNull
    @Override
    public Class<Copy> getType() {
        return Copy.class;
    }

    @Override
    public void execute(Copy task) {
        task.from(new Callable<List<FileTree>>() {
            @Override
            public List<FileTree> call() throws Exception {
                List<FileTree> fileTrees = Lists.newArrayList();
                for (File file : scope.getProject().getConfigurations().getByName(
                        JacocoPlugin.AGENT_CONFIGURATION_NAME)) {
                    fileTrees.add(scope.getProject().zipTree(file));
                }
                return fileTrees;
            }
        });
        task.include(TaskManager.FILE_JACOCO_AGENT);
        task.into(new File(scope.getIntermediatesDir(), "jacoco"));
    }

}
