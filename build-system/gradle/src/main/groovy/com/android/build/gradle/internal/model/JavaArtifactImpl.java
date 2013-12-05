/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.SourceProvider;

import java.io.File;
import java.io.Serializable;

/**
 * Implementation of JavaArtifact that is serializable
 */
public class JavaArtifactImpl extends BaseArtifactImpl implements JavaArtifact, Serializable {
    private static final long serialVersionUID = 1L;

    public static JavaArtifactImpl clone(@NonNull JavaArtifact javaArtifact) {
        return new JavaArtifactImpl(
                javaArtifact.getName(),
                javaArtifact.getAssembleTaskName(),
                javaArtifact.getJavaCompileTaskName(),
                javaArtifact.getClassesFolder(),
                javaArtifact.getDependencies(), // TODO:FixME
                SourceProviderImpl.cloneProvider(javaArtifact.getVariantSourceProvider()),
                SourceProviderImpl.cloneProvider(javaArtifact.getMultiFlavorSourceProvider()));
    }

    public JavaArtifactImpl(@NonNull String name,
                            @NonNull String assembleTaskName,
                            @NonNull String javaCompileTaskName,
                            @NonNull File classesFolder,
                            @NonNull Dependencies dependencies,
                            @Nullable SourceProvider variantSourceProvider,
                            @Nullable SourceProvider multiFlavorSourceProviders) {
        super(name, assembleTaskName, javaCompileTaskName, classesFolder, dependencies,
                variantSourceProvider, multiFlavorSourceProviders);
    }
}
