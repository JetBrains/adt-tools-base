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

package com.android.tools.profiler;

import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * An abstract transform that provides a simple API for transforming all the possible classes.
 * The only abstract method is {@link #transform(InputStream, OutputStream)} which will be run
 * for every class. This class takes care of all the incremental and jar vs class details.
 */
abstract class ClassTransform extends Transform {

    @NonNull
    private final String mName;

    public ClassTransform(String name) {
        mName = name;
    }

    @NonNull
    @Override
    public String getName() {
        return mName;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of(
                QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES,
                QualifiedContent.Scope.PROJECT_LOCAL_DEPS
        );
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    private String formatOutputName(int number, String name) {
        return String.format("%d_%s", number, name);
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws InterruptedException, IOException {
        assert invocation.getOutputProvider() != null;

        int outputNumber = 0;
        for (TransformInput ti : invocation.getInputs()) {
            for (JarInput jarInput : ti.getJarInputs()) {
                File inputJar = jarInput.getFile();
                String name = formatOutputName(outputNumber++,
                        Files.getNameWithoutExtension(inputJar.getName()));
                File outputJar = invocation.getOutputProvider().getContentLocation(
                        name, jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);

                if (invocation.isIncremental()) {
                    switch (jarInput.getStatus()) {
                        case NOTCHANGED:
                            break;
                        case ADDED:
                        case CHANGED:
                            transformJar(inputJar, outputJar);
                            break;
                        case REMOVED:
                            FileUtils.delete(outputJar);
                            break;
                    }
                } else {
                    transformJar(inputJar, outputJar);
                }
            }
            for (DirectoryInput di : ti.getDirectoryInputs()) {
                File inputDir = di.getFile();
                String name = formatOutputName(outputNumber++, inputDir.getName());
                File outputDir = invocation.getOutputProvider().getContentLocation(name,
                        di.getContentTypes(), di.getScopes(), Format.DIRECTORY);

                if (invocation.isIncremental()) {
                    for (Map.Entry<File, Status> entry : di.getChangedFiles().entrySet()) {
                        File inputFile = entry.getKey();
                        switch (entry.getValue()) {
                            case NOTCHANGED:
                                break;
                            case ADDED:
                            case CHANGED:
                                for (File in : FileUtils.getAllFiles(inputFile)) {
                                    File out = toOutputFile(outputDir, inputDir, in);
                                    transformFile(in, out);
                                }
                                break;
                            case REMOVED:
                                File outputFile = toOutputFile(outputDir, inputDir, inputFile);
                                FileUtils.delete(outputFile);
                                break;
                        }
                    }
                } else {
                    for (File in : FileUtils.getAllFiles(inputDir)) {
                        File out = toOutputFile(outputDir, inputDir, in);
                        transformFile(in, out);
                    }
                }
            }
        }
    }

    private void transformJar(File inputJar, File outputJar) throws IOException {
        Files.createParentDirs(outputJar);
        try (FileInputStream fis = new FileInputStream(inputJar);
             ZipInputStream zis = new ZipInputStream(fis);
             FileOutputStream fos = new FileOutputStream(outputJar);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    transform(zis, zos);
                } else {
                    ByteStreams.copy(zis, zos);
                }
                entry = zis.getNextEntry();
            }
        }
    }

    private void transformFile(File inputFile, File outputFile) throws IOException {
        Files.createParentDirs(outputFile);
        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            transform(fis, fos);
        }
    }

    @NonNull
    private static File toOutputFile(File outputDir, File inputDir, File inputFile) {
        return new File(outputDir, FileUtils.relativePath(inputFile, inputDir));
    }

    protected abstract void transform(InputStream in, OutputStream out) throws IOException;
}
