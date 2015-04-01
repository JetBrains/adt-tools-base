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
package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.scope.ConventionMappingHelper
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.ApkVariantData
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.builder.core.VariantConfiguration
import com.android.builder.internal.InstallUtils
import com.android.builder.sdk.SdkInfo
import com.android.builder.sdk.TargetInfo
import com.android.builder.testing.ConnectedDeviceProvider
import com.android.builder.testing.api.DeviceConfigProviderImpl
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.ide.common.build.SplitOutputMatcher
import com.android.ide.common.process.ProcessExecutor
import com.android.utils.ILogger
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

import java.util.concurrent.Callable

import static com.android.sdklib.BuildToolInfo.PathId.SPLIT_SELECT

/**
 * Task installing an app variant. It looks at connected device and install the best matching
 * variant output on each device.
 */
public class InstallVariantTask extends BaseTask {

    @InputFile
    File adbExe

    @InputFile
    @Optional
    File splitSelectExe

    ProcessExecutor processExecutor;

    String projectName

    @Input
    int timeOutInMs = 0

    @Input @Optional
    Collection<String> installOptions;

    BaseVariantData<? extends BaseVariantOutputData> variantData

    InstallVariantTask() {
        this.getOutputs().upToDateWhen {
            logger.debug("Install task is always run.");
            false;
        }
    }

    @TaskAction
    void install() {
        final ILogger iLogger = new LoggerWrapper(getLogger(), LogLevel.LIFECYCLE)
        DeviceProvider deviceProvider = new ConnectedDeviceProvider(getAdbExe(), iLogger)
        deviceProvider.init()

        VariantConfiguration variantConfig = variantData.variantConfiguration
        String variantName = variantConfig.fullName

        int successfulInstallCount = 0;

        for (DeviceConnector device : deviceProvider.getDevices()) {
            if (InstallUtils.checkDeviceApiLevel(
                    device, variantConfig.minSdkVersion, iLogger, projectName, variantName)) {
                // When InstallUtils.checkDeviceApiLevel returns false, it logs the reason.
                List<File> apkFiles = SplitOutputMatcher.computeBestOutput(processExecutor,
                        getSplitSelectExe(),
                        new DeviceConfigProviderImpl(device),
                        variantData.outputs,
                        variantData.variantConfiguration.getSupportedAbis())

                if (apkFiles.isEmpty()) {
                    logger.lifecycle(
                            "Skipping device '${device.getName()}' for " +
                                    "'${projectName}:${variantName}': " +
                                    "Could not find build of variant which supports " +
                                    "density " + "${device.getDensity()} " +
                                    "and an ABI in " + Joiner.on(", ").join(device.getAbis()));
                } else {
                    logger.lifecycle(
                            "Installing APK '${Joiner.on(", ").join(apkFiles*.getName())}'" +
                                    " on '${device.getName()}'")

                    List<String> extraArgs = installOptions == null ? ImmutableList.of() :
                            installOptions;
                    if (apkFiles.size() > 1 || device.getApiLevel() >= 21) {
                        device.installPackages(apkFiles, extraArgs, getTimeOutInMs(), getILogger());
                        successfulInstallCount++
                    } else {
                        device.installPackage(apkFiles.get(0), extraArgs, getTimeOutInMs(),
                                getILogger())
                        successfulInstallCount++
                    }
                }
            }
        }

        if (successfulInstallCount == 0) {
            throw new GradleException("Failed to install on any devices.")
        } else {
            logger.quiet("Installed on ${successfulInstallCount} " +
                    "${successfulInstallCount==1?'device':'devicess'}.");
        }
    }

    public static class ConfigAction implements TaskConfigAction<InstallVariantTask> {

        private final VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @Override
        String getName() {
            return "install${scope.getVariantConfiguration().fullName.capitalize()}";
        }

        @Override
        Class<InstallVariantTask> getType() {
            return InstallVariantTask.class
        }

        @Override
        public void execute(InstallVariantTask installTask) {
            installTask.setDescription("Installs the " + scope.getVariantData().getDescription() + ".");
            installTask.setGroup(TaskManager.INSTALL_GROUP);
            installTask.setProjectName(scope.getGlobalScope().getProject().getName());
            installTask.setVariantData(scope.getVariantData());
            installTask.setTimeOutInMs(scope.getGlobalScope().getExtension().getAdbOptions().getTimeOutInMs());
            installTask.setInstallOptions(scope.getGlobalScope().getExtension().getAdbOptions().getInstallOptions());
            installTask.setProcessExecutor(scope.getGlobalScope().getAndroidBuilder().getProcessExecutor());
            ConventionMappingHelper.map(installTask, "adbExe", new Closure<File>(this, this) {
                public File doCall(Object it) {
                    final SdkInfo info = scope.getGlobalScope().getSdkHandler().getSdkInfo();
                    return (info == null ? null : info.getAdb());
                }

                public File doCall() {
                    return doCall(null);
                }

            });
            ConventionMappingHelper.map(installTask, "splitSelectExe", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    final TargetInfo info = scope.getGlobalScope().getAndroidBuilder().getTargetInfo();
                    String path = info == null ? null : info.getBuildTools().getPath(SPLIT_SELECT);
                    if (path != null) {
                        File splitSelectExe = new File(path);
                        return splitSelectExe.exists() ? splitSelectExe : null;
                    } else {
                        return null;
                    }
                }
            });
            ((ApkVariantData) scope.getVariantData()).installTask = installTask;
        }
    }
}
