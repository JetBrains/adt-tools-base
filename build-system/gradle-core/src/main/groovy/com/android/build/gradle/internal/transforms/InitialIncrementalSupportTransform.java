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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.incremental.IncrementalSupportVisitor;
import com.android.build.gradle.internal.incremental.IncrementalVisitor;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.transform.api.ScopedContent;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutput;
import com.android.utils.ILogger;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.logging.Logging;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the {@link Transform} to run the byte code enhancement logic on compiled
 * classes in order to support runtime hot swapping.
 */
public class InitialIncrementalSupportTransform implements Transform {

    private static final ILogger LOGGER =
            new LoggerWrapper(Logging.getLogger(InitialIncrementalSupportTransform.class));

    @NonNull
    @Override
    public String getName() {
        return "initialIncrementalSupport";
    }

    @NonNull
    @Override
    public Set<ScopedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<ScopedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<ScopedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(ScopedContent.Scope.PROJECT);
    }

    @NonNull
    @Override
    public Set<ScopedContent.Scope> getReferencedScopes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Type getTransformType() {
        return Type.AS_INPUT;
    }

    @NonNull
    @Override
    public ScopedContent.Format getOutputFormat() {
        return ScopedContent.Format.SINGLE_FOLDER;
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
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of();
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull Map<TransformInput, TransformOutput> inputs,
            @NonNull List<TransformInput> referencedInputs, boolean isIncremental)
            throws IOException, TransformException, InterruptedException {


        for (final Map.Entry<TransformInput, TransformOutput> entry : inputs.entrySet()) {
            if (entry.getKey().getFiles().size() != 1) {
                throw new RuntimeException("Multiple input folder detected : " +
                    Joiner.on(",").join(entry.getKey().getFiles()));
            }
            final File inputDir = entry.getKey().getFiles().iterator().next();
            if (inputDir.isFile()) {
                throw new RuntimeException(
                        "Expected a directory in non incremental, got a file " +
                                inputDir.getAbsolutePath());

            }
            final File outputDir = entry.getValue().getOutFile();

            if (isIncremental) {

                for (Map.Entry<File, TransformInput.FileStatus> changedEntry :
                        entry.getKey().getChangedFiles().entrySet()) {

                    switch(changedEntry.getValue()) {
                        case REMOVED:
                            // removed the enhanced file.
                            File outputFile = getOutputFile(inputDir, changedEntry.getKey(),
                                    outputDir);
                            if (outputFile.exists() && !outputFile.delete()) {
                                // it's not a big deal if the file cannot be deleted, hopefully
                                // no code is still referencing it, yet we should notify.
                                LOGGER.warning("Cannot delete %1$s file", outputFile);
                            }
                            break;
                        case ADDED:
                        case CHANGED:
                            // process file
                            transformFile(
                                    inputDir, changedEntry.getKey(), outputDir);
                            break;

                        default:
                            throw new RuntimeException("Unhandled FileStatus : "
                                    + changedEntry.getValue());
                    }
                }

            } else {
                // non incremental mode, we need to traverse the TransformInput#getFiles() folder.}
                Files.fileTreeTraverser().breadthFirstTraversal(inputDir).transform(
                        new Function<File, Object>() {

                    @Override
                    public Object apply(File file) {
                        try {
                            transformFile(inputDir, file, outputDir);
                        } catch (IOException e) {
                            throw new RuntimeException("Exception while preparing "
                                    + file.getAbsolutePath());
                        }
                        return null;
                    }
                });
            }
        }

    }

    /**
     * Transform a single file.
     *
     * @param inputDir the input directory containing the input file.
     * @param inputFile the input file within the input directory to transform.
     * @param outputDir the output directory where to place the transformed file.
     * @throws IOException if the transformation failed.
     */
    protected void transformFile(File inputDir, File inputFile, File outputDir) throws IOException {

        File outputFile = getOutputFile(inputDir, inputFile, outputDir);
        Files.createParentDirs(outputFile.getParentFile());

        IncrementalVisitor.instrumentClass(inputFile, outputFile,
                new IncrementalVisitor.VisitorBuilder() {
                    @Override
                    public IncrementalVisitor build(@NonNull ClassNode classNode,
                            List<ClassNode> parentNodes,
                            ClassVisitor classVisitor) {
                        return new IncrementalSupportVisitor(classNode, parentNodes, classVisitor);
                    }

                    @Override
                    public boolean processParents() {
                        return false;
                    }
                });
    }

    /**
     * Return the expected output {@link File} for an input file located in the transform input
     * directory. The output file will have a similar relative path than the input file (to the
     * input dir) inside the output directory.
     *
     * @param inputDir the input directory containing the input file
     * @param inputFile the input file within the input directory
     * @param outputDir the output directory
     * @return the output file within the output directory with the right relative path.
     * @throws IOException
     */
    protected File getOutputFile(File inputDir, File inputFile, File outputDir) throws IOException {
        String relativePath = inputFile.getAbsolutePath().substring(
                inputDir.getAbsolutePath().length());

        return new File(outputDir, relativePath);
    }
}
