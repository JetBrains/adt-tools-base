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

package com.android.build.gradle.internal.tasks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A Transforms that takes the project/project local streams for native libs and processes and
 * combines them, and put them in the bundle folder under jni/
 *
 * Regarding Streams, this is a no-op transform as it does not write any output to any stream. It
 * uses secondary outputs to write directly into the bundle folder.
 */
public class LibraryJniLibsTransform extends Transform {

    @NonNull
    private final File jniLibsFolder;

    private final Pattern pattern = Pattern.compile("lib/[^/]+/[^/]+\\.so");


    public LibraryJniLibsTransform(
            @NonNull File jniLibsFolder) {
        this.jniLibsFolder = jniLibsFolder;
    }

    @NonNull
    @Override
    public String getName() {
        return "syncJniLibs";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_NATIVE_LIBS;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_LIBRARY;
    }

    @Override
    public boolean isIncremental() {
        // TODO make incremental
        return false;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(jniLibsFolder);
    }

    @Override
    public void transform(
            @NonNull Context context,
            @NonNull Collection<TransformInput> unusedInputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider unusedOutputProvider,
            boolean isIncremental) throws IOException, TransformException, InterruptedException {

        FileUtils.emptyFolder(jniLibsFolder);

        for (TransformInput input : referencedInputs) {
            for (JarInput jarInput : input.getJarInputs()) {
                copyFromJar(jarInput.getFile());
            }

            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                copyFromFolder(directoryInput.getFile());
            }
        }
    }

    private void copyFromFolder(@NonNull File rootDirectory) throws IOException {
        copyFromFolder(rootDirectory, Lists.<String>newArrayListWithCapacity(3));
    }

    private void copyFromFolder(@NonNull File from, @NonNull List<String> pathSegments)
            throws IOException {
        File[] children = from.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                return file.isDirectory() || name.endsWith(SdkConstants.DOT_NATIVE_LIBS);
            }
        });

        if (children != null) {
            for (File child : children) {
                pathSegments.add(child.getName());
                if (child.isDirectory()) {
                    copyFromFolder(child, pathSegments);
                } else if (child.isFile()) {
                    if (pattern.matcher(Joiner.on('/').join(pathSegments)).matches()) {
                        // copy the file. However we do want to skip the first segment ('lib') here
                        // since the 'jni' folder is representing the same concept.
                        File to = FileUtils.join(jniLibsFolder, pathSegments.subList(1, 3));
                        FileUtils.mkdirs(to.getParentFile());
                        Files.copy(child, to);
                    }
                }

                pathSegments.remove(pathSegments.size() - 1);
            }
        }
    }

    private void copyFromJar(@NonNull File jarFile) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ZipFile zipFile = new ZipFile(jarFile);
        try {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                String entryPath = entry.getName();

                if (!pattern.matcher(entryPath).matches()) {
                    continue;
                }

                // read the content.
                buffer.reset();
                ByteStreams.copy(zipFile.getInputStream(entry), buffer);

                // get the output file and write to it.
                Files.write(buffer.toByteArray(), computeFile(jniLibsFolder, entryPath));
            }
        } finally {
            zipFile.close();
        }
    }

    /**
     * computes a file path from a root folder and a zip archive path.
     * @param rootFolder the root folder
     * @param path the archive path
     * @return the File
     */
    private static File computeFile(@NonNull File rootFolder, @NonNull String path) {
        path = FileUtils.toSystemDependentPath(path);
        return new File(rootFolder, path);
    }
}
