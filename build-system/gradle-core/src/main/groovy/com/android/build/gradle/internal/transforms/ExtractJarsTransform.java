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
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.packaging.PackagingUtils;
import com.android.utils.FileUtils;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Transform to extract jars.
 *
 */
public class ExtractJarsTransform extends Transform {

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
    public Set<Scope> getScopes() {
        return scopes;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(
            @NonNull Context context,
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider outputProvider,
            boolean isIncremental) throws IOException, TransformException, InterruptedException {
        checkNotNull(outputProvider, "Missing output object for transform " + getName());

        // as_input transform and no referenced scopes, all the inputs will in InputOutputStreams.
        final boolean extractCode = contentTypes.contains(DefaultContentType.CLASSES);

        Logger logger = Logging.getLogger(ExtractJarsTransform.class);

        if (!isIncremental) {
            outputProvider.deleteAll();
        }

        try {
            WaitableExecutor<Void> executor = new WaitableExecutor<Void>();

            for (TransformInput input : inputs) {
                if (!input.getDirectoryInputs().isEmpty()) {
                    for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                        logger.warn("Extract Jars input contains folder. Ignoring: " +
                                directoryInput.getFile());
                    }
                }

                for (JarInput jarInput : input.getJarInputs()) {
                    final File jarFile = jarInput.getFile();

                    // create an output folder for this jar, keeping its type and scopes.
                    final File outJarFolder = outputProvider.getContentLocation(
                            jarFile.getName() + "-" + jarFile.getPath().hashCode(),
                            jarInput.getContentTypes(),
                            jarInput.getScopes(),
                            Format.DIRECTORY);
                    FileUtils.mkdirs(outJarFolder);

                    if (!isIncremental) {
                        executor.execute(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                extractJar(outJarFolder, jarFile, extractCode);
                                return null;
                            }
                        });
                    } else {
                        switch (jarInput.getStatus()) {
                            case CHANGED:
                                executor.execute(new Callable<Void>() {
                                    @Override
                                    public Void call() throws Exception {
                                        emptyFolder(outJarFolder);
                                        extractJar(outJarFolder, jarFile, extractCode);
                                        return null;
                                    }
                                });
                                break;
                            case ADDED:
                                executor.execute(new Callable<Void>() {
                                    @Override
                                    public Void call() throws Exception {
                                        extractJar(outJarFolder, jarFile, extractCode);
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
                                break;
                        }
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
