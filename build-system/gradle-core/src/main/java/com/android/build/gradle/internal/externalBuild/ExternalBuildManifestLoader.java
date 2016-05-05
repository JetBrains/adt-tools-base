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

package com.android.build.gradle.internal.externalBuild;

import com.android.annotations.NonNull;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads the manifest file produced by an external build system.
 */
public class ExternalBuildManifestLoader {

    /**
     * Loads the passed manifest file and populates its information in the passed context object.
     * @param file the manifest file
     * @param projectDir the porject directory in case the manifest contains relative paths.
     * @param externalBuildContext the context to populate.
     * @throws IOException if the file cannot be read correctly
     */
    public static void loadAndPopulateContext(
            @NonNull File file,
            @NonNull File projectDir,
            @NonNull ExternalBuildContext externalBuildContext) throws IOException {

        if (!file.exists()) {
            throw new FileNotFoundException(file.getAbsolutePath());
        }
        ExternalBuildApkManifest.ApkManifest manifest;
        // read the manifest file
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            manifest = ExternalBuildApkManifest.ApkManifest.parseFrom(is);
        }

        List<File> jarFiles = manifest.getJarsList().stream()
                .map(artifact -> {
                    File artifactFile = new File(artifact.getExecRootPath());
                    return artifactFile.isAbsolute()
                            ? artifactFile
                            : new File(projectDir, artifact.getExecRootPath()) ;
                })
                .collect(Collectors.toList());

        externalBuildContext.setInputJarFiles(jarFiles);
        externalBuildContext.setBuildManifest(manifest);
    }
}
