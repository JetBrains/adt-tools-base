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
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.test.TestApplicationTestData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.TestData;
import com.android.manifmerger.ManifestMerger2;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableMap;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import android.databinding.tool.DataBindingBuilder;

import java.util.List;

/**
 * TaskManager for standalone test application that lives in a separate module from the tested
 * application.
 */
public class TestApplicationTaskManager extends ApplicationTaskManager {

    private static final String TEST_CONFIGURATION_PREFIX = "testTarget";

    private Configuration mTestTargetMapping = null;
    private Configuration mTargetManifestConfiguration = null;

    public TestApplicationTaskManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry) {
        super(
                project,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                ndkHandler,
                dependencyManager,
                toolingRegistry);
    }

    @Override
    public void createTasksForVariantData(@NonNull TaskFactory tasks,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {

        super.createTasksForVariantData(tasks, variantData);

        // create a new configuration with the target application coordinates.
        // This is for the tested APK.
        Configuration testTarget = getTestTargetConfiguration("");

        // and create the configuration for the project's metadata.
        Configuration testTargetMetadata = getTestTargetConfiguration("metadata");

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
                variantData.getScope().getAssembleTask());

        Task connectedAndroidTest = tasks.named(BuilderConstants.CONNECTED
                + VariantType.ANDROID_TEST.getSuffix());
        if (connectedAndroidTest != null) {
            connectedAndroidTest.dependsOn(instrumentTestTask.getName());
        }
    }

    @Override
    protected boolean isTestedAppMinified(@NonNull VariantScope variantScope){
        return getTestTargetMapping(variantScope) != null;
    }

    @Override
    protected void createMinifyTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull VariantScope variantScope,
            boolean createJarFile) {
        if (getTestTargetMapping(variantScope) != null) {
            doCreateMinifyTransform(
                    taskFactory, variantScope, getTestTargetMapping(variantScope), false);
        }
    }

    /** Returns the mapping configuration of the tested app, if it is used */
    @Nullable
    private Configuration getTestTargetMapping(@NonNull VariantScope variantScope){
        if (mTestTargetMapping == null){
            mTestTargetMapping = getTestTargetConfiguration("mapping");
        }

        if (mTestTargetMapping.getFiles().isEmpty()
                || variantScope.getVariantConfiguration().getProvidedOnlyJars().isEmpty()){
            return null;
        }
        else {
            return mTestTargetMapping;
        }
    }

    /** Returns the manifest configuration of the tested application */
    @NonNull
    private Configuration getTargetManifestConfiguration(){
        if (mTargetManifestConfiguration == null){
            mTargetManifestConfiguration = getTestTargetConfiguration("manifest");
        }

        return mTargetManifestConfiguration;
    }

    @Override
    @NonNull
    protected TaskConfigAction<? extends ManifestProcessorTask> getMergeManifestConfig(
            @NonNull VariantOutputScope scope,
            @NonNull List<ManifestMerger2.Invoker.Feature> optionalFeatures) {
        return new ProcessTestManifest.ConfigAction(
                scope.getVariantScope(), getTargetManifestConfiguration());
    }

    /** Creates the test target configuration that depends on the supplied target configuration */
    @NonNull
    private Configuration getTestTargetConfiguration(@NonNull String targetConfigurationName){

        String testTargetConfigurationName = TEST_CONFIGURATION_PREFIX;
        String prefix = "";
        if (!targetConfigurationName.isEmpty()){
            testTargetConfigurationName += StringHelper.capitalize(targetConfigurationName);
            prefix = "-";
        }

        Configuration testTargetConfiguration = project.getConfigurations()
                .create(testTargetConfigurationName);

        DependencyHandler dependencyHandler = project.getDependencies();
        TestAndroidConfig testExtension = (TestAndroidConfig) extension;

        dependencyHandler.add(testTargetConfigurationName, dependencyHandler.project(
                ImmutableMap.of(
                        "path", testExtension.getTargetProjectPath(),
                        "configuration",
                        testExtension.getTargetVariant() + prefix + targetConfigurationName)));

        return testTargetConfiguration;
    }
}
