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
import com.android.build.gradle.model.AndroidBinary;
import com.android.build.gradle.model.DefaultAndroidBinary;
import com.android.build.gradle.model.JniLibsSourceSet;
import com.android.build.gradle.ndk.internal.NdkNamingScheme;
import com.google.common.collect.ImmutableMap;

import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.file.CopySpec;
import org.gradle.api.tasks.Copy;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.platform.base.BinarySpec;

import java.io.File;
import java.util.Map;

import groovy.lang.Closure;

/**
 * {@link LanguageTransform} for {@link JniLibsSourceSet}.
 *
 * The transform creates a task to copy the dependencies into a folder that will be packaged into
 * the APK.
 */
public class JniLibsLanguageTransform implements LanguageTransform<JniLibsSourceSet, SharedObjectFile> {

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

    private static class TransformConfig implements SourceTransformTaskConfig {

        @Override
        public String getTaskPrefix() {
            return "copyJniLibs";
        }

        @Override
        public Class<? extends DefaultTask> getTaskType() {
            return Copy.class;
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
                                    "shared")).resolve();
            Copy copyTask = (Copy) task;
            for (NativeBinarySpec nativeBinary: dependencies.getNativeBinaries()) {
                // TODO: Handle transitive dependencies.
                final String abi = nativeBinary.getTargetPlatform().getName();
                if (binary.getMergedNdkConfig().getAbiFilters().contains(abi)) {
                    copyTask.dependsOn(
                            nativeBinary.getBuildTask().getProject().getPath() + ":" +
                                    NdkNamingScheme.getNdkBuildTaskName(nativeBinary));

                    copyTask.from(
                            ((NativeBinarySpecInternal) nativeBinary).getPrimaryOutput()
                                    .getParentFile(), new Closure<Void>(this, this) {
                                public void doCall(CopySpec copySpec) {
                                    copySpec.into(abi);
                                }
                            });
                }
            }
            for (final Map.Entry<Abi, File> entry : dependencies.getLibraryFiles().entries()) {
                copyTask.from(
                        entry.getValue(), new Closure<Void>(this, this) {
                            public void doCall(CopySpec copySpec) {
                                copySpec.into(entry.getKey().getName());
                            }
                        });
            }
            copyTask.into(new File(
                    task.getProject().getBuildDir(),
                    NdkNamingScheme.getDependencyLibraryDirectoryName(
                            binaryBuildType,
                            binaryProductFlavor,
                            "")));
            binarySpec.builtBy(task);
        }
    }
}
