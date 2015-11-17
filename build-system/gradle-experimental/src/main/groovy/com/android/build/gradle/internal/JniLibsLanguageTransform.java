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

import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.dependency.AndroidNativeDependencySpec;
import com.android.build.gradle.internal.dependency.NativeDependencyResolveResult;
import com.android.build.gradle.internal.dependency.NativeDependencyResolver;
import com.android.build.gradle.internal.dependency.NativeLibraryArtifact;
import com.android.build.gradle.model.AndroidBinary;
import com.android.build.gradle.model.DefaultAndroidBinary;
import com.android.build.gradle.model.JniLibsSourceSet;
import com.android.build.gradle.tasks.StripDependenciesTask;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.platform.base.BinarySpec;

import java.io.File;
import java.util.Map;

/**
 * {@link LanguageTransform} for {@link JniLibsSourceSet}.
 *
 * The transform creates a task that strips or copy the dependencies into a folder that will be
 * packaged into the APK.
 */
public class JniLibsLanguageTransform implements LanguageTransform<JniLibsSourceSet, SharedObjectFile> {

    private NdkHandler ndkHandler;

    public JniLibsLanguageTransform(NdkHandler ndkHandler) {
        this.ndkHandler = ndkHandler;
    }

    @Override
    public Class<JniLibsSourceSet> getSourceSetType() {
        return JniLibsSourceSet.class;
    }

    @Override
    public Class<SharedObjectFile> getOutputType() {
        return SharedObjectFile.class;
    }

    @Override
    public Map<String, Class<?>> getBinaryTools() {
        return ImmutableMap.of();
    }

    @Override
    public SourceTransformTaskConfig getTransformTask() {
        return new TransformConfig();
    }

    @Override
    public boolean applyToBinary(BinarySpec binary) {
        return binary instanceof AndroidBinary;
    }

    private class TransformConfig implements SourceTransformTaskConfig {

        @Override
        public String getTaskPrefix() {
            return "copyJniLibs";
        }

        @Override
        public Class<? extends DefaultTask> getTaskType() {
            return StripDependenciesTask.class;
        }

        @Override
        public void configureTask(
                Task task,
                BinarySpec binarySpec,
                LanguageSourceSet languageSourceSet,
                ServiceRegistry serviceRegistry) {
            DefaultAndroidBinary binary = (DefaultAndroidBinary) binarySpec;

            String binaryBuildType = binary.getBuildType().getName();
            String binaryProductFlavor =
                    ProductFlavorCombo.getFlavorComboName(binary.getProductFlavors());

            JniLibsSourceSet sourceSet = (JniLibsSourceSet) languageSourceSet;

            for (final AndroidNativeDependencySpec dependencySpec :
                    sourceSet.getDependencies().getDependencies()) {
                dependencySpec.validate();
                if (dependencySpec.getLinkage() != null) {
                    throw new InvalidUserDataException(
                            "Cannot specify linkage for native dependency for jniLibs.");
                }
            }
            NativeDependencyResolveResult dependencies =
                    new NativeDependencyResolver(
                            serviceRegistry,
                            sourceSet.getDependencies(),
                            new AndroidNativeDependencySpec(
                                    null,
                                    null,
                                    binaryBuildType,
                                    binaryProductFlavor,
                                    null,
                                    NativeDependencyLinkage.SHARED)).resolve();

            Map<File, Abi> inputFiles = Maps.newHashMap();
            StripDependenciesTask stripTask = (StripDependenciesTask) task;

            for (NativeLibraryArtifact artifacts: dependencies.getNativeArtifacts()) {
                final String abi = artifacts.getAbi();
                assert abi != null;

                if (binary.getMergedNdkConfig().getAbiFilters().contains(abi)) {
                    stripTask.dependsOn(artifacts.getBuiltBy());

                    // Debug libraries created from another subproject may have debug symbols,
                    // therefore, the library is stripped before packaging.
                    for (File output : artifacts.getLibraries()) {
                        if (output.getName().endsWith(".so")) {
                            inputFiles.put(output, Abi.getByName(abi));
                        }
                    }
                }
            }

            Map<File, Abi> stripedFiles = Maps.newHashMap();
            for (final Map.Entry<Abi, File> entry : dependencies.getLibraryFiles().entries()) {
                System.out.println(entry.getValue());
                // For dependency on a library file, there is no way to know if it contains debug
                // symbol, and NDK may not not be set.  We may not have access to the strip tool,
                // therefore, we assume the library do not have debug symbols and simply copy the
                // file.
                stripedFiles.put(entry.getValue(), entry.getKey());
            }

            new StripDependenciesTask.ConfigAction(binary.getBuildType().getName(),
                    ProductFlavorCombo.getFlavorComboName(binary.getProductFlavors()),
                    inputFiles,
                    stripedFiles,
                    task.getProject().getBuildDir(),
                    ndkHandler).execute(stripTask);


            binarySpec.builtBy(task);
        }
    }
}
