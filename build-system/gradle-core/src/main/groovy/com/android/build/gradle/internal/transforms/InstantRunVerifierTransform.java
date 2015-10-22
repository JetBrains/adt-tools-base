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
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.incremental.IncompatibleChange;
import com.android.build.gradle.internal.incremental.InstantRunVerifier;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.transform.api.Context;
import com.android.build.transform.api.NoOpTransform;
import com.android.build.transform.api.ScopedContent;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.builder.profile.ExecutionType;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * No-op transform that verifies that changes between 2 versions of the same class are supported
 * by the InstantRun implementation.
 *
 * To verify class changes, this transform will save all .class files in a private directory
 * {@see VariantScope#getIncrementalVerifierDir}.
 *
 * When new classes are compiled, this transform will receive an incremental notification and will
 * compare the new versions to the ones saved in the private directory. The result of this
 * verification process will be encapsulated in an instance of {@link VerificationResult} and stored
 * in the VariantScope.
 */
public class InstantRunVerifierTransform extends Transform implements NoOpTransform {

    protected static final ILogger LOGGER =
            new LoggerWrapper(Logging.getLogger(InstantRunVerifierTransform.class));

    private final VariantScope variantScope;
    private final File outputDir;

    /**
     * Object that encapsulates the result of the verification process.
     */
    public static class VerificationResult {

        @Nullable
        private final IncompatibleChange changes;

        @VisibleForTesting
        VerificationResult(@Nullable IncompatibleChange changes) {
            this.changes = changes;
        }

        /**
         * Returns true if the verification process has determined that the new class' version is
         * compatible with hot swap technology, false otherwise.
         * @return true if the new class can be hot swapped, false otherwise.
         */
        public boolean isCompatible() {
            return changes == null;
        }
    }

    public InstantRunVerifierTransform(VariantScope variantScope) {
        this.variantScope = variantScope;
        this.outputDir = variantScope.getIncrementalVerifierDir();
    }

    @Override
    public void transform(@NonNull Context context, @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs, boolean isIncremental)
            throws IOException, TransformException, InterruptedException {

        long startTime = System.currentTimeMillis();
        doTransform(context, inputs, referencedInputs, isIncremental);
        LOGGER.info(String.format("Wall time for verifier : %1$d ms",
                System.currentTimeMillis() - startTime));
    }

    public void doTransform(@NonNull Context context, @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs, boolean isIncremental)
            throws IOException, TransformException, InterruptedException {

        if (!isIncremental && outputDir.exists()) {
            FileUtils.emptyFolder(outputDir);
        } else {
            FileUtils.mkdirs(outputDir);
        }

        Optional<VerificationResult> verificationResult = Optional.absent();
        for (TransformInput transformInput : inputs) {
            if (transformInput.getFormat() != ScopedContent.Format.SINGLE_FOLDER) {
                throw new RuntimeException("Unexpected stream input format : "
                        + transformInput.getFormat());
            }
            if (transformInput.getFiles().size() != 1) {
                throw new RuntimeException("Unexpected stream input size : "
                        + transformInput.getFiles().size());
            }
            final File inputDir = transformInput.getFiles().iterator().next();
            if (inputDir.isFile()) {
                throw new RuntimeException(
                        "Expected a directory in non incremental, got a file " +
                                inputDir.getAbsolutePath());
            }

            if (isIncremental) {

                for (Map.Entry<File, TransformInput.FileStatus> changedEntry :
                        transformInput.getChangedFiles().entrySet()) {

                    File inputFile = changedEntry.getKey();
                    switch(changedEntry.getValue()) {
                        case REMOVED:
                            // remove the classes.2 and classes.3 files.
                            File classes_backup = getOutputFile(inputDir, inputFile, outputDir);
                            if (classes_backup.exists() && !classes_backup.delete()) {
                                // it's not a big deal if the file cannot be deleted, hopefully
                                // no code is still referencing it, yet we should notify.
                                LOGGER.warning("Cannot delete %1$s file", classes_backup);
                            }
                            break;
                        case ADDED:
                            // new file, no verification necessary, but save it for next iteration.
                            copyFile(inputFile, getOutputFile(inputDir, inputFile, outputDir));
                            break;
                        case CHANGED:
                            // a new version of the class has been compiled, we should compare
                            // it with the one saved during the last iteration on the file.
                            verificationResult = verifyAndSaveFile(verificationResult, inputDir, inputFile);
                            break;

                        default:
                            throw new RuntimeException("Unhandled FileStatus : "
                                    + changedEntry.getValue());
                    }
                }

            } else {
                // non incremental mode, we need to traverse the TransformInput#getFiles() folder}
                for (File file : Files.fileTreeTraverser().breadthFirstTraversal(inputDir)) {

                    if (file.isDirectory()) {
                        continue;
                    }
                    try {
                        copyFile(file, getLastIterationFile(inputDir, file));
                    } catch (IOException e) {
                        throw new RuntimeException("Exception while copying "
                                + file.getAbsolutePath());
                    }
                }
            }
        }
        // So far, a null changes means success.
        variantScope.setVerificationResult(verificationResult.or(new VerificationResult(null)));
    }

    @NonNull
    private File getLastIterationFile(File inputDir, File inputFile) {
        String relativePath = FileUtils.relativePath(inputFile, inputDir);
        return new File(outputDir, relativePath);
    }

    @NonNull
    private Optional<VerificationResult> verifyAndSaveFile(
            @NonNull Optional<VerificationResult> pastResults,
            @NonNull File inputDir,
            @NonNull File newFile) throws IOException {

        File lastIterationFile = getLastIterationFile(inputDir, newFile);
        if (lastIterationFile.exists() && !pastResults.isPresent()) {
            IncompatibleChange changes = runVerifier(lastIterationFile, newFile);
            LOGGER.verbose(
                    "%1$s : verifier result : %2$s", newFile.getName(), changes);
            if (changes != null) {
                pastResults = Optional.of(new VerificationResult(changes));
            }
        }
        // always copy the new file over to our private backup directory for the next iteration
        // verification.
        copyFile(newFile, lastIterationFile);
        return pastResults;
    }

    @VisibleForTesting
    protected IncompatibleChange runVerifier(final File originalClass, final File updatedClass)
            throws IOException {

        return ThreadRecorder.get().record(ExecutionType.TASK_FILE_VERIFICATION,
                new Recorder.Block<IncompatibleChange>() {
                    @Override
                    public IncompatibleChange call() throws Exception {
                        return InstantRunVerifier.run(originalClass, updatedClass);
                    }
                }, new Recorder.Property("file", originalClass.getName())
        );
    }

    @VisibleForTesting
    protected void copyFile(File inputFile, File outputFile) throws IOException {
        Files.createParentDirs(outputFile);
        Files.copy(inputFile, outputFile);
    }

    @NonNull
    @Override
    public String getName() {
        return "instantRunVerifier";
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

    @Nullable
    @Override
    public ScopedContent.Format getOutputFormat() {
        return ScopedContent.Format.SINGLE_FOLDER;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFolderOutputs() {
        return ImmutableList.of(outputDir);
    }

    @Override
    public boolean isIncremental() {
        return true;
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
