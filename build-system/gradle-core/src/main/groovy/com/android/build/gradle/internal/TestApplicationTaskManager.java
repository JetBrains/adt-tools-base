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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.test.TestApplicationTestData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.TestData;
import com.google.common.collect.ImmutableMap;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import android.databinding.tool.DataBindingBuilder;
/**
 * TaskManager for standalone test application that lives in a separate module from the tested
 * application.
 */
public class TestApplicationTaskManager extends ApplicationTaskManager {


    public TestApplicationTaskManager(Project project,
            AndroidBuilder androidBuilder,
            DataBindingBuilder dataBindingBuilder,
            AndroidConfig extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry) {
        super(project, androidBuilder, dataBindingBuilder, extension, sdkHandler, dependencyManager,
                toolingRegistry);
    }

    @Override
    public void createTasksForVariantData(@NonNull TaskFactory tasks,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {

        super.createTasksForVariantData(tasks, variantData);

        // create a new configuration with the target application coordinates.
        // This is for the tested APK.
        final Configuration testTarget = project.getConfigurations().create("testTarget");

        DependencyHandler dependencyHandler = project.getDependencies();
        TestAndroidConfig testExtension = (TestAndroidConfig) extension;
        dependencyHandler.add("testTarget",
                dependencyHandler.project(
                        ImmutableMap.of(
                                "path", testExtension.getTargetProjectPath(),
                                "configuration", testExtension.getTargetVariant())));

        // and create the configuration for the project's metadata.
        final Configuration testTargetMetadata = project.getConfigurations().create("testTargetMetadata");

        dependencyHandler.add("testTargetMetadata", dependencyHandler.project(
                        ImmutableMap.of(
                                "path", testExtension.getTargetProjectPath(),
                                "configuration", testExtension.getTargetVariant() + "-metadata"
                        )));

        TestData testData = new TestApplicationTestData(
                variantData, testTarget, testTargetMetadata, androidBuilder);

        // create the test connected check task.
        AndroidTask<DeviceProviderInstrumentTestTask> instrumentTestTask =
                getAndroidTasks().create(
                        tasks,
                        new DeviceProviderInstrumentTestTask.ConfigAction(
                                variantData.getScope(),
                                new ConnectedDeviceProvider(
                                        sdkHandler.getSdkInfo().getAdb(),
                                        getGlobalScope().getExtension().getAdbOptions().getTimeOutInMs(),
                                        new LoggerWrapper(getLogger())),
                                testData) {
                            @NonNull
                            @Override
                            public String getName() {
                                return super.getName() + VariantType.ANDROID_TEST.getSuffix();
                            }
                        });

        // make the test application connectedCheck depends on the configuration added above so
        // we can retrieve its artifacts

        instrumentTestTask.dependsOn(tasks,
                testTarget,
                testTargetMetadata,
                variantData.assembleVariantTask);

        Task connectedAndroidTest = tasks.named(BuilderConstants.CONNECTED + VariantType.ANDROID_TEST.getSuffix());
        if (connectedAndroidTest != null) {
            connectedAndroidTest.dependsOn(instrumentTestTask.getName());
        }
    }

    @Override
    protected void createMinifyTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull VariantScope variantScope,
            boolean createJarFile) {

        DependencyHandler dependencyHandler = project.getDependencies();
        TestAndroidConfig testExtension = (TestAndroidConfig) extension;
        Configuration testTargetMapping = project.getConfigurations().create("testTargetMapping");

        dependencyHandler.add("testTargetMapping", dependencyHandler.project(
                ImmutableMap.of(
                        "path", testExtension.getTargetProjectPath(),
                        "configuration", testExtension.getTargetVariant() + "-mapping"
                )));

        if (testTargetMapping.getFiles().isEmpty()
                || variantScope.getVariantConfiguration().getProvidedOnlyJars().isEmpty()) {
            return;
        }

        doCreateMinifyTransform(taskFactory, variantScope, testTargetMapping, false);
    }
}
