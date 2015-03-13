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

import static com.android.builder.core.BuilderConstants.CONNECTED;

import com.android.annotations.NonNull;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.TestExtension;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.test.TestApplicationTestData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.TestData;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * TaskManager for standalone test application that lives in a separate module from the tested
 * application.
 */
public class TestApplicationTaskManager extends ApplicationTaskManager {


    public TestApplicationTaskManager(Project project,
            AndroidBuilder androidBuilder,
            BaseExtension extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry) {
        super(project, androidBuilder, extension, sdkHandler, dependencyManager, toolingRegistry);
    }

    @Override
    public void createTasksForVariantData(TaskFactory tasks,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {

        super.createTasksForVariantData(tasks, variantData);

        // create a new configuration with the target application coordinates.
        Configuration testTarget = project.getConfigurations().create("testTarget");

        DependencyHandler dependencyHandler = project.getDependencies();
        TestExtension testExtension = (TestExtension) extension;
        dependencyHandler.add("testTarget",
                dependencyHandler.project(
                        ImmutableMap.of(
                                "path", testExtension.getTargetProjectPath(),
                                "configuration", testExtension.getTargetVariant())));

        TestData testData = new TestApplicationTestData(variantData, testTarget, androidBuilder);

        // create the test connected check task.
        DeviceProviderInstrumentTestTask testConnectedCheck =
                createDeviceProviderInstrumentTestTask(
                        project.getName() + "ConnectedCheck",
                        "Installs and runs the tests for ${baseVariantData.description} on connected devices.",
                        DeviceProviderInstrumentTestTask.class,
                        testData,
                        ImmutableList.of(variantData.assembleVariantTask),
                        new ConnectedDeviceProvider(sdkHandler.getSdkInfo().getAdb()),
                        CONNECTED
                );

        // make the test application connectedCheck depends on the configuration added above so
        // we can retrieve its artifacts
        testConnectedCheck.dependsOn(testTarget);

        // make the main ConnectedCheck task depends on this test connectedCheck
        Task connectedCheck = tasks.named(CONNECTED_CHECK);
        if (connectedCheck != null) {
            connectedCheck.dependsOn(testConnectedCheck);
        }
    }
}
