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
import static com.android.SdkConstants.DOT_JAR;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.transform.api.CombinedTransform;
import com.android.build.transform.api.Context;
import com.android.build.transform.api.ScopedContent;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutput;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Transform all the local dependencies into a set of jars.
 *
 * This only jars the class files, not the resources.
 */
public class MultiStreamJarTransform extends Transform implements CombinedTransform {

    private static final String FILE_NAME_PREFIX = "localjar";
    private static final int PREFIX_LENGTH = FILE_NAME_PREFIX.length();

    @NonNull
    @Override
    public String getName() {
        return "localJar";
    }

    @NonNull
    @Override
    public Set<ScopedContent.ContentType> getInputTypes() {
        return Sets.immutableEnumSet(ScopedContent.ContentType.CLASSES);
    }

    @NonNull
    @Override
    public Set<ScopedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(ScopedContent.Scope.PROJECT_LOCAL_DEPS);
    }

    @Nullable
    @Override
    public ScopedContent.Format getOutputFormat() {
        return ScopedContent.Format.SINGLE_FOLDER;
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
            @NonNull TransformOutput output,
            boolean isIncremental) throws IOException, TransformException, InterruptedException {

        File outFolder = output.getOutFile();
        FileUtils.emptyFolder(outFolder);

        StringBuilder fileNameBuilder = new StringBuilder(20);
        fileNameBuilder.append(FILE_NAME_PREFIX);
        int index = 1;

        // let's do non-incremental for now.
        // TODO: let's do this incrementally.
        // TODO: let's multi-thread this.
        for (TransformInput input : inputs) {

            switch (input.getFormat()) {
                case JAR:
                    // that's stupid, but just copy the jars.
                    for (File jarFile : input.getFiles()) {
                        fileNameBuilder.setLength(PREFIX_LENGTH);
                        fileNameBuilder.append(index++).append(DOT_JAR);
                        File outFile = new File(outFolder, fileNameBuilder.toString());
                        Files.copy(jarFile, outFile);
                    }
                    break;
                case SINGLE_FOLDER:
                    for (File folder : input.getFiles()) {
                        fileNameBuilder.setLength(PREFIX_LENGTH);
                        fileNameBuilder.append(index++).append(DOT_JAR);
                        File outFile = new File(outFolder, fileNameBuilder.toString());
                        jarFolder(folder, outFile);
                    }
                    break;
                case MULTI_FOLDER:
                    throw new RuntimeException("MULTI_FOLDER format received in Transform method");
                default:
                    throw new RuntimeException("Unsupported ScopedContent.Format value: " + input.getFormat().name());
            }
        }
    }

    private static void jarFolder(@NonNull File folder, @NonNull File jarFile) throws IOException {
        Closer closer = Closer.create();
        try {

            FileOutputStream fos = closer.register(new FileOutputStream(jarFile));
            JarOutputStream jos = closer.register(new JarOutputStream(fos));

            final byte[] buffer = new byte[8192];
            processFolder(jos, "", folder, buffer);

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
}
