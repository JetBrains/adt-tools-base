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

package com.android.build.gradle.internal.transforms;

import static com.android.utils.FileUtils.delete;
import static com.android.utils.FileUtils.emptyFolder;
import static com.android.utils.FileUtils.mkdirs;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.transform.api.AsInputTransform;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformInput.FileStatus;
import com.android.build.transform.api.TransformOutput;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.packaging.PackagingUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Transform to extract jars.
 *
 * This only supports {@link Format#JAR} and works with {@link Type#AS_INPUT} so that each input
 * stream has a matching output stream.
 *
 * The output format is {@link Format#MULTI_FOLDER} because each input jar has its own sub-folder
 * in the output stream root folder.
 */
public class ExtractJarsTransform implements AsInputTransform {

    @NonNull
    private final Set<ContentType> contentTypes;
    @NonNull
    private final Set<Scope> scopes;

    public ExtractJarsTransform(
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<Scope> scopes) {
        this.contentTypes = contentTypes;
        this.scopes = scopes;
    }

    @NonNull
    @Override
    public String getName() {
        return "extractJars";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return contentTypes;
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return contentTypes;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return scopes;
    }

    @NonNull
    @Override
    public Format getOutputFormat() {
        return Format.MULTI_FOLDER;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Type getTransformType() {
        return Type.AS_INPUT;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFolderOutputs() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of();
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(
            @NonNull Map<TransformInput, TransformOutput> inputOutputs,
            @NonNull Collection<TransformInput> referencedInputs,
            boolean isIncremental) throws IOException, InterruptedException, TransformException {

        // as_input transform and no referenced scopes, all the inputs will in InputOutputStreams.
        final boolean extractCode = contentTypes.contains(ContentType.CLASSES);

        try {
            WaitableExecutor<Void> executor = new WaitableExecutor<Void>();

            for (Entry<TransformInput, TransformOutput> entry : inputOutputs.entrySet()) {
                TransformInput input = entry.getKey();

                Format format = input.getFormat();
                if (format != Format.JAR) {
                    throw new RuntimeException(
                            String.format(
                                    "%s only supports JAR streams. Current stream format: %s:\n\t%s",
                                    getName(),
                                    format.name(),
                                    Iterables.getFirst(input.getFiles(), null)));
                }

                final File outFolder = entry.getValue().getOutFile();

                if (isIncremental) {
                    for (final Entry<File, FileStatus> fileEntry : input.getChangedFiles().entrySet()) {
                        final File outJarFolder = getFolder(outFolder, fileEntry.getKey());
                        switch (fileEntry.getValue()) {
                            case CHANGED:
                                executor.execute(new Callable<Void>() {
                                    @Override
                                    public Void call() throws Exception {
                                        emptyFolder(outJarFolder);
                                        extractJar(outJarFolder, fileEntry.getKey(), extractCode);
                                        return null;
                                    }
                                });
                                break;
                            case ADDED:
                                executor.execute(new Callable<Void>() {
                                    @Override
                                    public Void call() throws Exception {
                                        extractJar(outJarFolder, fileEntry.getKey(), extractCode);
                                        return null;
                                    }
                                });
                                break;
                            case REMOVED:
                                executor.execute(new Callable<Void>() {
                                    @Override
                                    public Void call() throws Exception {
                                        delete(outJarFolder);
                                        return null;
                                    }
                                });
                        }
                    }

                } else {
                    for (final File jarFile : entry.getKey().getFiles()) {
                        executor.execute(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                File outJarFolder = getFolder(outFolder, jarFile);
                                emptyFolder(outJarFolder);
                                extractJar(outJarFolder, jarFile, extractCode);
                                return null;
                            }
                        });
                    }
                }
            }

            executor.waitForTasksWithQuickFail(true);

        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new TransformException(e);
        }
    }

    private static void extractJar(
            @NonNull File outJarFolder,
            @NonNull File jarFile,
            boolean extractCode) throws IOException {
        mkdirs(outJarFolder);

        Closer closer = Closer.create();
        try {
            FileInputStream fis = closer.register(new FileInputStream(jarFile));
            ZipInputStream zis = closer.register(new ZipInputStream(fis));
            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // do not take directories
                if (entry.isDirectory()) {
                    continue;
                }

                Action action = getAction(name, extractCode);
                if (action == Action.COPY) {
                    File outputFile = new File(outJarFolder, name.replace('/', File.separatorChar));
                    mkdirs(outputFile.getParentFile());

                    Closer closer2 = Closer.create();
                    try {
                        java.io.OutputStream outputStream = closer2.register(
                                new BufferedOutputStream(new FileOutputStream(outputFile)));
                        ByteStreams.copy(zis, outputStream);
                        outputStream.flush();
                    } finally {
                        closer2.close();
                    }
                }
            }
        } finally {
            closer.close();
        }
    }

    /**
     * Define all possible actions for a Jar file entry.
     */
    enum Action {
        /**
         * Copy the file to the output destination.
         */
        COPY,
        /**
         * Ignore the file.
         */
        IGNORE
    }

    /**
     * Provides an {@link Action} for the archive entry.
     * @param archivePath the archive entry path in the archive.
     * @param extractCode whether to extractCode
     * @return the action to implement.
     */
    @NonNull
    public static Action getAction(@NonNull String archivePath, boolean extractCode) {
        // Manifest files are never merged.
        if (JarFile.MANIFEST_NAME.equals(archivePath)) {
            return Action.IGNORE;
        }

        // split the path into segments.
        String[] segments = archivePath.split("/");

        // empty path? skip to next entry.
        if (segments.length == 0) {
            return Action.IGNORE;
        }

        // Check each folders to make sure they should be included.
        // Folders like CVS, .svn, etc.. should already have been excluded from the
        // jar file, but we need to exclude some other folder (like /META-INF) so
        // we check anyway.
        for (int i = 0 ; i < segments.length - 1; i++) {
            if (!PackagingUtils.checkFolderForPackaging(segments[i])) {
                return Action.IGNORE;
            }
        }

        // get the file name from the path
        String fileName = segments[segments.length-1];

        return PackagingUtils.checkFileForPackaging(fileName, extractCode)
                ? Action.COPY
                : Action.IGNORE;
    }

    @NonNull
    private static File getFolder(
            @NonNull File outFolder,
            @NonNull File jarFile) {
        return new File(outFolder, jarFile.getName() + "-" + jarFile.getPath().hashCode());
    }
}
