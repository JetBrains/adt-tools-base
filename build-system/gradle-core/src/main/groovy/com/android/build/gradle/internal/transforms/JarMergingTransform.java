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

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.utils.FileUtils.deleteIfExists;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.transform.api.CombinedTransform;
import com.android.build.transform.api.Context;
import com.android.build.transform.api.ScopedContent;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutput;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A transform that merges all the incoming streams into a single jar in a single combined
 * stream.
 */
public class JarMergingTransform extends Transform implements CombinedTransform {

    @NonNull
    private final ImmutableSet<Scope> scopes;

    @NonNull
    private final Set<ContentType> types;

    public JarMergingTransform(@NonNull Set<Scope> scopes, @NonNull Set<ContentType> types) {
        this.scopes = ImmutableSet.copyOf(scopes);
        this.types = ImmutableSet.copyOf(types);
    }

    @NonNull
    @Override
    public String getName() {
        return "jarMerging";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return types;
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return scopes;
    }

    @NonNull
    @Override
    public ScopedContent.Format getOutputFormat() {
        return ScopedContent.Format.JAR;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(
            @NonNull Context context,
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedStreams,
            @NonNull TransformOutput combinedOutput,
            boolean isIncremental) throws TransformException, IOException {
        Closer closer = Closer.create();
        try {
            // all the output will be the same since the transform type is COMBINED.
            // and format is SINGLE_JAR so output is a jar
            checkNotNull(combinedOutput, "Found no output in transform with Type=COMBINED");
            File jarFile = combinedOutput.getOutFile();
            deleteIfExists(jarFile);

            FileOutputStream fos = closer.register(new FileOutputStream(jarFile));
            JarOutputStream jos = closer.register(new JarOutputStream(fos));

            final byte[] buffer = new byte[8192];

            for (TransformInput input : inputs) {
                switch (input.getFormat()) {
                    case SINGLE_FOLDER:
                        for (File inputFile : input.getFiles()) {
                            if (inputFile.isFile()) {
                                processJarFile(jos, inputFile, buffer);
                            } else if (inputFile.isDirectory()) {
                                processFolder(jos, "", inputFile, buffer);
                            }

                        }
                        break;
                    case MULTI_FOLDER:
                        for (File file : input.getFiles()) {
                            File[] subStreams = file.listFiles();
                            if (subStreams != null) {
                                for (File subStream : subStreams) {
                                    if (subStream.isDirectory()) {
                                        processFolder(jos, "", subStream, buffer);
                                    }
                                }
                            }
                        }

                        break;
                    case JAR:
                        for (File f : input.getFiles()) {
                            processJarFile(jos, f, buffer);
                        }
                        break;
                    default:
                        throw new RuntimeException("Unsupported ScopedContent.Format value: " + input.getFormat().name());
                }
            }

        } catch (FileNotFoundException e) {
            throw new TransformException(e);
        } catch (IOException e) {
            throw new TransformException(e);
        } finally {
            closer.close();
        }
    }

    private static void processFolder(
            @NonNull JarOutputStream jos,
            @NonNull String path,
            @NonNull File folder,
            @NonNull byte[] buffer)
            throws IOException {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    if (file.getName().endsWith(DOT_CLASS)) {
                        // new entry
                        jos.putNextEntry(new JarEntry(path + file.getName()));

                        // put the file content
                        Closer closer = Closer.create();
                        try {
                            FileInputStream fis = closer.register(new FileInputStream(file));
                            int count;
                            while ((count = fis.read(buffer)) != -1) {
                                jos.write(buffer, 0, count);
                            }
                        } finally {
                            closer.close();
                        }

                        // close the entry
                        jos.closeEntry();
                    }
                } else if (file.isDirectory()) {
                    processFolder(jos, path + file.getName() + "/", file, buffer);
                }
            }
        }
    }

    private static void processJarFile(JarOutputStream jos, File file, byte[] buffer)
            throws IOException {

        Closer closer = Closer.create();
        try {
            FileInputStream fis = closer.register(new FileInputStream(file));
            ZipInputStream zis = closer.register(new ZipInputStream(fis));

            // loop on the entries of the jar file package and put them in the final jar
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // do not take directories or anything inside a potential META-INF folder.
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (!name.endsWith(DOT_CLASS)) {
                    continue;
                }

                JarEntry newEntry;

                // Preserve the STORED method of the input entry.
                if (entry.getMethod() == JarEntry.STORED) {
                    newEntry = new JarEntry(entry);
                } else {
                    // Create a new entry so that the compressed len is recomputed.
                    newEntry = new JarEntry(name);
                }

                // add the entry to the jar archive
                jos.putNextEntry(newEntry);

                // read the content of the entry from the input stream, and write it into the archive.
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    jos.write(buffer, 0, count);
                }

                // close the entries for this file
                jos.closeEntry();
                zis.closeEntry();
            }
        } finally {
            closer.close();
        }
    }
}
