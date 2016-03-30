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
package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.compiling.ResValueGenerator;
import com.android.builder.model.ClassField;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import javax.xml.parsers.ParserConfigurationException;

@ParallelizableTask
public class GenerateResValues extends BaseTask {

    // ----- PUBLIC TASK API -----

    private File resOutputDir;

    @OutputDirectory
    public File getResOutputDir() {
        return resOutputDir;
    }

    public void setResOutputDir(File resOutputDir) {
        this.resOutputDir = resOutputDir;
    }

    // ----- PRIVATE TASK API -----

    public List<Object> getItems() {
        return items;
    }

    public void setItems(List<Object> items) {
        this.items = items;
    }

    private List<Object> items;

    @SuppressWarnings("unused") // Synthetic input.
    @Input
    List<String> getItemValues() {
        List<Object> resolvedItems = getItems();
        List<String> list = Lists.newArrayListWithCapacity(resolvedItems.size() * 3);

        for (Object object : resolvedItems) {
            if (object instanceof String) {
                list.add((String) object);
            } else if (object instanceof ClassField) {
                ClassField field = (ClassField) object;
                list.add(field.getType());
                list.add(field.getName());
                list.add(field.getValue());
            }
        }

        return list;
    }

    @TaskAction
    void generate() throws IOException, ParserConfigurationException {
        File folder = getResOutputDir();
        List<Object> resolvedItems = getItems();

        if (resolvedItems.isEmpty()) {
            FileUtils.emptyFolder(folder);
        } else {
            ResValueGenerator generator = new ResValueGenerator(folder);
            generator.addItems(getItems());

            generator.generate();
        }
    }


    public static class ConfigAction implements TaskConfigAction<GenerateResValues> {

        @NonNull
        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("generate", "ResValues");
        }

        @NonNull
        @Override
        public Class<GenerateResValues> getType() {
            return GenerateResValues.class;
        }

        @Override
        public void execute(@NonNull GenerateResValues generateResValuesTask) {
            scope.getVariantData().generateResValuesTask = generateResValuesTask;

            final GradleVariantConfiguration variantConfiguration =
                    scope.getVariantData().getVariantConfiguration();

            generateResValuesTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            generateResValuesTask.setVariantName(variantConfiguration.getFullName());

            ConventionMappingHelper.map(generateResValuesTask, "items",
                    (Callable<List<Object>>) variantConfiguration::getResValues);

            generateResValuesTask.setResOutputDir(scope.getGeneratedResOutputDir());
        }
    }
}
