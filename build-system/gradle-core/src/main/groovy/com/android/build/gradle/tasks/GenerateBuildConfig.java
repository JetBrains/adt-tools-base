/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.compiling.BuildConfigGenerator;
import com.android.builder.model.ClassField;
import com.android.utils.FileUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

@ParallelizableTask
public class GenerateBuildConfig extends BaseTask {

    // ----- PUBLIC TASK API -----

    private File sourceOutputDir;

    @OutputDirectory
    public File getSourceOutputDir() {
        return sourceOutputDir;
    }

    public void setSourceOutputDir(File sourceOutputDir) {
        this.sourceOutputDir = sourceOutputDir;
    }

    // ----- PRIVATE TASK API -----

    private String buildConfigPackageName;

    private String appPackageName;

    private boolean debuggable;

    private String flavorName;

    private List<String> flavorNamesWithDimensionNames;

    private String buildTypeName;

    private String versionName;

    private int versionCode;

    private List<Object> items;

    @Input
    public String getBuildConfigPackageName() {
        return buildConfigPackageName;
    }

    public void setBuildConfigPackageName(String buildConfigPackageName) {
        this.buildConfigPackageName = buildConfigPackageName;
    }

    @Input
    public String getAppPackageName() {
        return appPackageName;
    }

    public void setAppPackageName(String appPackageName) {
        this.appPackageName = appPackageName;
    }

    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    @Input
    public String getFlavorName() {
        return flavorName;
    }

    public void setFlavorName(String flavorName) {
        this.flavorName = flavorName;
    }

    @Input
    public List<String> getFlavorNamesWithDimensionNames() {
        return flavorNamesWithDimensionNames;
    }

    public void setFlavorNamesWithDimensionNames(
            List<String> flavorNamesWithDimensionNames) {
        this.flavorNamesWithDimensionNames = flavorNamesWithDimensionNames;
    }

    @Input
    public String getBuildTypeName() {
        return buildTypeName;
    }

    public void setBuildTypeName(String buildTypeName) {
        this.buildTypeName = buildTypeName;
    }

    @Input
    @Optional
    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    @Input
    public int getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    public List<Object> getItems() {
        return items;
    }

    public void setItems(List<Object> items) {
        this.items = items;
    }


    @SuppressWarnings("unused") // Synthetic input for items.
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
    void generate() throws IOException {
        // must clear the folder in case the packagename changed, otherwise,
        // there'll be two classes.
        File destinationDir = getSourceOutputDir();
        FileUtils.emptyFolder(destinationDir);

        BuildConfigGenerator generator = new BuildConfigGenerator(
                getSourceOutputDir(),
                getBuildConfigPackageName());

        // Hack (see IDEA-100046): We want to avoid reporting "condition is always true"
        // from the data flow inspection, so use a non-constant value. However, that defeats
        // the purpose of this flag (when not in debug mode, if (BuildConfig.DEBUG && ...) will
        // be completely removed by the compiler), so as a hack we do it only for the case
        // where debug is true, which is the most likely scenario while the user is looking
        // at source code.
        //map.put(PH_DEBUG, Boolean.toString(mDebug));
        generator.addField("boolean", "DEBUG",
                isDebuggable() ? "Boolean.parseBoolean(\"true\")" : "false")
                .addField("String", "APPLICATION_ID", '"' + getAppPackageName() + '"')
                .addField("String", "BUILD_TYPE", '"' + getBuildTypeName() + '"')
                .addField("String", "FLAVOR", '"' + getFlavorName() + '"')
                .addField("int", "VERSION_CODE", Integer.toString(getVersionCode()))
                .addField("String", "VERSION_NAME", '"' + Strings.nullToEmpty(getVersionName()) + '"')
                .addItems(getItems());

        List<String> flavors = getFlavorNamesWithDimensionNames();
        int count = flavors.size();
        if (count > 1) {
            for (int i = 0; i < count; i += 2) {
                generator.addField(
                        "String", "FLAVOR_" + flavors.get(i + 1), '"' + flavors.get(i) + '"');
            }
        }

        generator.generate();
    }

    // ----- Config Action -----

    public static final class ConfigAction implements TaskConfigAction<GenerateBuildConfig> {

        @NonNull
        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("generate", "BuildConfig");
        }

        @Override
        @NonNull
        public Class<GenerateBuildConfig> getType() {
            return GenerateBuildConfig.class;
        }


        @Override
        public void execute(@NonNull GenerateBuildConfig generateBuildConfigTask) {
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

            variantData.generateBuildConfigTask = generateBuildConfigTask;

            final GradleVariantConfiguration variantConfiguration =
                    variantData.getVariantConfiguration();

            generateBuildConfigTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            generateBuildConfigTask.setVariantName(scope.getVariantConfiguration().getFullName());

            ConventionMappingHelper.map(generateBuildConfigTask, "buildConfigPackageName",
                    (Callable<String>) variantConfiguration::getOriginalApplicationId);

            ConventionMappingHelper.map(generateBuildConfigTask, "appPackageName",
                    (Callable<String>) variantConfiguration::getApplicationId);

            ConventionMappingHelper.map(generateBuildConfigTask, "versionName",
                    (Callable<String>) variantConfiguration::getVersionName);

            ConventionMappingHelper.map(generateBuildConfigTask, "versionCode",
                    (Callable<Integer>) variantConfiguration::getVersionCode);
            ConventionMappingHelper.map(generateBuildConfigTask, "debuggable",
                    (Callable<Boolean>) () -> variantConfiguration.getBuildType().isDebuggable());

            ConventionMappingHelper.map(generateBuildConfigTask, "buildTypeName",
                    (Callable<String>) () -> variantConfiguration.getBuildType().getName());

            ConventionMappingHelper.map(generateBuildConfigTask, "flavorName",
                    (Callable<String>) variantConfiguration::getFlavorName);

            ConventionMappingHelper.map(generateBuildConfigTask, "flavorNamesWithDimensionNames",
                    (Callable<List<String>>) variantConfiguration::getFlavorNamesWithDimensionNames);

            ConventionMappingHelper.map(generateBuildConfigTask, "items",
                    (Callable<List<Object>>) variantConfiguration::getBuildConfigItems);

            generateBuildConfigTask.setSourceOutputDir(scope.getBuildConfigSourceOutputDir());
        }
    }
}
