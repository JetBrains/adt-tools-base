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

package com.android.build.gradle.internal.transforms;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.TransformInput;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Collection;

/**
 * Utility methods for retrieving files from TransformInput.
 */
public class TransformInputUtil {

    public static Collection<File> getAllFiles(Collection<TransformInput> transformInputs) {
        return getAllFiles(transformInputs, true, true);
    }

    public static Collection<File> getJarFiles(Collection<TransformInput> transformInputs) {
        return getAllFiles(transformInputs, false, true);
    }

    public static Collection<File> getDirectories(Collection<TransformInput> transformInputs) {
        return getAllFiles(transformInputs, true, false);
    }

    private static Collection<File> getAllFiles(
            Collection<TransformInput> transformInputs,
            boolean includeDirectoryInput,
            boolean includeJarInput) {
        ImmutableList.Builder<File> inputFiles = ImmutableList.builder();
        for (TransformInput input : transformInputs) {
            if (includeDirectoryInput) {
                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    inputFiles.add(directoryInput.getFile());
                }
            }
            if (includeJarInput) {
                for (JarInput jarInput : input.getJarInputs()) {
                    inputFiles.add(jarInput.getFile());
                }
            }
        }
        return inputFiles.build();
    }
}
