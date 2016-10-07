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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.builder.model.OptionalCompilationStep;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifier;
import com.android.build.gradle.internal.incremental.InstantRunVerifier.ClassBytesJarEntryProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.GradleBuildProfileSpan.ExecutionType;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * No-op transform that verifies that changes between 2 versions of the same class are supported
 * by the InstantRun implementation.
 *
 * To verify class changes, this transform will save all .class files in a private directory
 * see {@link InstantRunVariantScope#getIncrementalVerifierDir}.
 *
 * When new classes are compiled, this transform will receive an incremental notification and will
 * compare the new versions to the ones saved in the private directory. The result of this
 * verification process will be encapsulated in an instance of {@link VerificationResult} and stored
 * in the VariantScope.
 */
public class InstantRunVerifierTransform extends Transform {

    private static final ILogger LOGGER =
            new LoggerWrapper(Logging.getLogger(InstantRunVerifierTransform.class));

    private final InstantRunVariantScope variantScope;
    private final File outputDir;

    /**
     * Object that encapsulates the result of the verification process.
     */
    private static class VerificationResult {

        @Nullable
        private final InstantRunVerifierStatus changes;

        @VisibleForTesting
        VerificationResult(@Nullable InstantRunVerifierStatus changes) {
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

    public InstantRunVerifierTransform(InstantRunVariantScope variantScope) {
        this.variantScope = variantScope;
        this.outputDir = variantScope.getIncrementalVerifierDir();
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {

        if (invocation.getReferencedInputs().isEmpty()) {
            throw new RuntimeException("Empty list of referenced inputs");
        }
        try {
            variantScope.getInstantRunBuildContext().startRecording(
                    InstantRunBuildContext.TaskType.VERIFIER);
            doTransform(invocation.getReferencedInputs(), invocation.isIncremental());
        } finally {
            variantScope.getInstantRunBuildContext().stopRecording(
                    InstantRunBuildContext.TaskType.VERIFIER);
        }
    }

    private void doTransform(@NonNull Collection<TransformInput> inputs, boolean isIncremental)
            throws IOException, TransformException, InterruptedException {

        if (!isIncremental && outputDir.exists()) {
            FileUtils.cleanOutputDir(outputDir);
        } else {
            FileUtils.mkdirs(outputDir);
        }

        InstantRunVerifierStatus resultSoFar = InstantRunVerifierStatus.COMPATIBLE;
        for (TransformInput transformInput : inputs) {
            resultSoFar = processFolderInputs(resultSoFar, isIncremental, transformInput);
            resultSoFar = processJarInputs(resultSoFar, transformInput);
        }

        // if we are being asked to produce the RESTART artifacts, there is no need to set the
        // verifier result, however the transform needed to run to backup the .class files.
        if (!variantScope.getGlobalScope().isActive(OptionalCompilationStep.RESTART_ONLY)) {
            variantScope.getInstantRunBuildContext().setVerifierResult(resultSoFar);
        }
    }

    @NonNull
    private InstantRunVerifierStatus processFolderInputs(
            @NonNull InstantRunVerifierStatus verificationResult,
            boolean isIncremental,
            @NonNull TransformInput transformInput) throws IOException {

        for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {

            File inputDir = directoryInput.getFile();

            if (!isIncremental) {
                // non incremental mode, we need to traverse the folder
                for (File file : Files.fileTreeTraverser().breadthFirstTraversal(inputDir)) {

                    if (file.isDirectory()) {
                        continue;
                    }
                    copyFile(file, getOutputFile(inputDir, file, outputDir));
                }
                continue;
            }
            for (Map.Entry<File, Status> changedFile :
                    directoryInput.getChangedFiles().entrySet()) {

                File inputFile = changedFile.getKey();
                if (inputFile.isDirectory()) {
                    continue;
                }
                File lastIterationFile = getOutputFile(inputDir, inputFile, outputDir);
                switch(changedFile.getValue()) {
                    case REMOVED:
                        // remove the backup file.
                        if (lastIterationFile.exists() && !lastIterationFile.delete()) {
                            // it's not a big deal if the file cannot be deleted, hopefully
                            // no code is still referencing it, yet we should notify.
                            LOGGER.warning("Cannot delete %1$s file", lastIterationFile);
                        }
                        break;
                    case ADDED:
                        // new file, save it for next iteration.
                        copyFile(inputFile, lastIterationFile);
                        verificationResult = InstantRunVerifierStatus.CLASS_ADDED;
                        break;
                    case CHANGED:
                        // a new version of the class has been compiled, we should compare
                        // it with the one saved during the last iteration on the file, but only
                        // if we have not failed any verification so far.
                        if (verificationResult == InstantRunVerifierStatus.COMPATIBLE) {
                            if (lastIterationFile.exists()) {
                                verificationResult = runVerifier(inputFile.getName(),
                                        new InstantRunVerifier.ClassBytesFileProvider(
                                                lastIterationFile),
                                        new InstantRunVerifier.ClassBytesFileProvider(inputFile));
                                LOGGER.verbose("%1$s : verifier result : %2$s",
                                        inputFile.getName(), verificationResult);
                            } else {
                                verificationResult = InstantRunVerifierStatus.INSTANT_RUN_FAILURE;
                                LOGGER.verbose("Changed file %1$s not found in verifier backup",
                                        inputFile.getAbsolutePath());
                            }
                        }

                        // always copy the new file over to our private backup directory for the
                        // next iteration verification.
                        copyFile(inputFile, lastIterationFile);
                        break;
                    case NOTCHANGED:
                        break;
                    default:
                        throw new IllegalArgumentException("Unhandled DirectoryInput status "
                                + changedFile.getValue());
                }

            }
        }
        return verificationResult;
    }

    @NonNull
    private InstantRunVerifierStatus processJarInputs(
            @NonNull InstantRunVerifierStatus resultSoFar,
            @NonNull TransformInput transformInput) throws IOException {

        // can jarInput have colliding names ?
        for (JarInput jarInput : transformInput.getJarInputs()) {
            File backupJar = new File(outputDir, jarInput.getName());
            switch(jarInput.getStatus()) {
                case REMOVED:
                    if (backupJar.exists() && !backupJar.delete()) {
                        // it's not a big deal if the file cannot be deleted, hopefully
                        // no code is still referencing it, yet we should notify
                        LOGGER.warning("Cannot delete %1$s file", backupJar);
                    }
                    break;
                case CHANGED:
                    // get a Map of the back up jar entries indexed by name.
                    if (resultSoFar == InstantRunVerifierStatus.COMPATIBLE) {
                        if (backupJar.exists()) {
                            if (backupJar.isDirectory()) {
                                LOGGER.warning("Unexpected backup folder at %s while processing %s",
                                        backupJar.getAbsolutePath(), jarInput.getFile());
                                try {
                                    FileUtils.deleteDirectoryContents(backupJar);
                                } catch(IOException e) {
                                    LOGGER.warning(String.format("Cannot delete %s : %s",
                                            backupJar.getAbsolutePath(), e));
                                }
                                if (!backupJar.delete()) {
                                    LOGGER.warning("Cannot delete " + backupJar.getAbsolutePath());
                                }
                                resultSoFar = InstantRunVerifierStatus.INSTANT_RUN_FAILURE;
                            } else {
                                try (JarFile backupJarFile = new JarFile(backupJar)) {
                                    try (JarFile jarFile = new JarFile(jarInput.getFile())) {
                                        resultSoFar = processChangedJar(backupJarFile, jarFile);
                                    }
                                }
                            }
                        }
                    }
                    // fall through ADDED case.
                case ADDED:
                    if (!jarInput.getFile().exists() || jarInput.getFile().isDirectory()) {
                        LOGGER.warning(String.format(
                                "Please file a bug : VerifierTransform expected a file"
                                + " at:\n %s \nbut the file does not exist or is a directory",
                                jarInput.getFile()));
                        resultSoFar = InstantRunVerifierStatus.INSTANT_RUN_FAILURE;
                    }
                    copyFile(jarInput.getFile(), backupJar);
                    break;
                case NOTCHANGED:
                    break;
                default:
                    throw new IllegalArgumentException("Unhandled JarInput status "
                            + jarInput.getStatus());
            }
        }
        return resultSoFar;
    }

    @NonNull
    private InstantRunVerifierStatus processChangedJar(JarFile backupJar, JarFile newJar)
            throws IOException {

        Map<String, JarEntry> backupEntries = new HashMap<>();
        Enumeration<JarEntry> backupJarEntries = backupJar.entries();
        while (backupJarEntries.hasMoreElements()) {
            JarEntry jarEntry = backupJarEntries.nextElement();
            backupEntries.put(jarEntry.getName(), jarEntry);
        }
        // go through the jar file, entry by entry.
        Enumeration<JarEntry> jarEntries = newJar.entries();
        while (jarEntries.hasMoreElements()) {
            JarEntry jarEntry = jarEntries.nextElement();
            if (jarEntry.getName().endsWith(".class")) {
                JarEntry backupEntry = backupEntries.get(jarEntry.getName());
                if (backupEntry != null) {
                    InstantRunVerifierStatus verificationResult =
                            runVerifier(
                                    newJar.getName() + ":" + jarEntry.getName(),
                                    new ClassBytesJarEntryProvider(backupJar, backupEntry),
                                    new ClassBytesJarEntryProvider(newJar, jarEntry));
                    if (verificationResult != InstantRunVerifierStatus.COMPATIBLE) {
                        return verificationResult;
                    }
                }

            }
        }
        return InstantRunVerifierStatus.COMPATIBLE;
    }

    @VisibleForTesting
    @NonNull
    protected InstantRunVerifierStatus runVerifier(String name,
            @NonNull final InstantRunVerifier.ClassBytesProvider originalClass ,
            @NonNull final InstantRunVerifier.ClassBytesProvider updatedClass) throws IOException {
        if (!name.endsWith(SdkConstants.DOT_CLASS)) {
            return InstantRunVerifierStatus.COMPATIBLE;
        }
        InstantRunVerifierStatus status = ThreadRecorder.get().record(
                ExecutionType.TASK_FILE_VERIFICATION,
                variantScope.getGlobalScope().getProject().getPath(),
                variantScope.getFullVariantName(),
                new Recorder.Block<InstantRunVerifierStatus>() {
                    @Override
                    @NonNull
                    public InstantRunVerifierStatus call() throws Exception {
                        return InstantRunVerifier.run(originalClass, updatedClass);
                    }
                });
        // TODO: re-add approximation of target.
        if (status == null) {
            LOGGER.warning("No verifier result provided for %1$s", name);
            return InstantRunVerifierStatus.NOT_RUN;
        }
        return status;
    }

    @VisibleForTesting
    protected void copyFile(File inputFile, File outputFile) {
        try {
            Files.createParentDirs(outputFile);
            Files.copy(inputFile, outputFile);
        } catch(IOException e) {
            LOGGER.error(e, "Cannot copy %1$s to back up folder, build will continue but "
                    + "next time this file is modified will result in a cold swap.",
                    inputFile.getAbsolutePath());
        }
    }

    @NonNull
    @Override
    public String getName() {
        return "instantRunVerifier";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_CLASS;    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(
                QualifiedContent.Scope.PROJECT, QualifiedContent.Scope.SUB_PROJECTS);
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
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
     */
    protected static File getOutputFile(File inputDir, File inputFile, File outputDir) {
        String relativePath = FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir);
        return new File(outputDir, relativePath);
    }
}
