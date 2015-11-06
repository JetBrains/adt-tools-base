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
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.incremental.IncompatibleChange;
import com.android.build.gradle.internal.incremental.InstantRunVerifier;
import com.android.build.gradle.internal.incremental.InstantRunVerifier.ClassBytesJarEntryProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.profile.ExecutionType;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Optional;
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
 * {@see VariantScope#getIncrementalVerifierDir}.
 *
 * When new classes are compiled, this transform will receive an incremental notification and will
 * compare the new versions to the ones saved in the private directory. The result of this
 * verification process will be encapsulated in an instance of {@link VerificationResult} and stored
 * in the VariantScope.
 */
public class InstantRunVerifierTransform extends Transform {

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
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider outputProvider, boolean isIncremental)
            throws IOException, TransformException, InterruptedException {

        long startTime = System.currentTimeMillis();
        if (referencedInputs.isEmpty()) {
            throw new RuntimeException("Empty list of referenced inputs");
        }
        doTransform(referencedInputs, isIncremental);
        LOGGER.info(String.format("Wall time for verifier : %1$d ms",
                System.currentTimeMillis() - startTime));
    }

    public void doTransform(@NonNull Collection<TransformInput> inputs, boolean isIncremental)
            throws IOException, TransformException, InterruptedException {

        if (!isIncremental && outputDir.exists()) {
            FileUtils.emptyFolder(outputDir);
        } else {
            FileUtils.mkdirs(outputDir);
        }

        Optional<IncompatibleChange> resultSoFar = Optional.absent();
        for (TransformInput transformInput : inputs) {
            resultSoFar = processFolderInputs(isIncremental, transformInput);
            resultSoFar = processJarInputs(resultSoFar, transformInput);
        }
        // So far, a null changes means success.
        variantScope.setVerificationResult(new VerificationResult(resultSoFar.orNull()));
    }

    @NonNull
    private Optional<IncompatibleChange> processFolderInputs(
            boolean isIncremental,
            @NonNull TransformInput transformInput) throws IOException {

        Optional<IncompatibleChange> verificationResult = Optional.absent();
        for (DirectoryInput DirectoryInput : transformInput.getDirectoryInputs()) {

            File inputDir = DirectoryInput.getFile();

            if (!isIncremental) {
                // non incremental mode, we need to traverse the folder
                for (File file : Files.fileTreeTraverser().breadthFirstTraversal(inputDir)) {

                    if (file.isDirectory()) {
                        continue;
                    }
                    try {
                        copyFile(file, getOutputFile(inputDir, file, outputDir));
                    } catch (IOException e) {
                        throw new RuntimeException("Exception while copying "
                                + file.getAbsolutePath());
                    }
                }
                continue;
            }
            for (Map.Entry<File, Status> changedFile :
                    DirectoryInput.getChangedFiles().entrySet()) {

                File inputFile = changedFile.getKey();
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
                        // new file, no verification necessary, but save it for next iteration.
                        copyFile(inputFile, lastIterationFile);
                        break;
                    case CHANGED:
                        // a new version of the class has been compiled, we should compare
                        // it with the one saved during the last iteration on the file, but only
                        // if we have not failed any verification so far.
                        if (!verificationResult.isPresent()) {
                            verificationResult = Optional.fromNullable(
                                    verifyAndSaveFile(inputFile, lastIterationFile));
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
        // all changes are compatible.
        return verificationResult;
    }

    @NonNull
    private Optional<IncompatibleChange> processJarInputs(
            @NonNull Optional<IncompatibleChange> resultSoFar,
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
                case ADDED:
                    copyFile(jarInput.getFile(), backupJar);
                    break;
                case CHANGED:
                    // get a Map of the back up jar entries indexed by name.
                    if (!resultSoFar.isPresent()) {
                        JarFile backupJarFile = new JarFile(backupJar);
                        try {
                            JarFile jarFile = new JarFile(jarInput.getFile());
                            try {
                                resultSoFar = Optional.fromNullable(
                                        processChangedJar(backupJarFile, jarFile));
                            } finally {
                                jarFile.close();
                            }
                        } finally {
                            backupJarFile.close();
                        }
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
        // all changes are compatible.
        return resultSoFar;
    }

    private IncompatibleChange processChangedJar(JarFile backupJar, JarFile newJar)
            throws IOException {

        Map<String, JarEntry> backupEntries = new HashMap<String, JarEntry>();
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
                    IncompatibleChange verificationResult =
                            runVerifier(
                                    newJar.getName() + ":" + jarEntry.getName(),
                                    new ClassBytesJarEntryProvider(backupJar, backupEntry),
                                    new ClassBytesJarEntryProvider(newJar, jarEntry));
                    if (verificationResult != null) {
                        return verificationResult;
                    }
                }

            }
        }
        return null;
    }

    @Nullable
    private IncompatibleChange verifyAndSaveFile(
            @NonNull File lastIterationFile,
            @NonNull File newFile) throws IOException {

        IncompatibleChange verificationResult = null;
        if (lastIterationFile.exists()) {
            verificationResult = runVerifier(newFile.getName(),
                    new InstantRunVerifier.ClassBytesFileProvider(lastIterationFile),
                    new InstantRunVerifier.ClassBytesFileProvider(newFile));
            LOGGER.verbose(
                    "%1$s : verifier result : %2$s", newFile.getName(), verificationResult);
        }
        return verificationResult;
    }

    @VisibleForTesting
    @Nullable
    protected IncompatibleChange runVerifier(String name,
            @NonNull final InstantRunVerifier.ClassBytesProvider originalClass ,
            @NonNull final InstantRunVerifier.ClassBytesProvider updatedClass) throws IOException {

        return ThreadRecorder.get().record(ExecutionType.TASK_FILE_VERIFICATION,
                new Recorder.Block<IncompatibleChange>() {
                    @Override
                    public IncompatibleChange call() throws Exception {
                        return InstantRunVerifier.run(originalClass, updatedClass);
                    }
                }, new Recorder.Property("target", name)
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
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT);

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
     * @throws IOException
     */
    protected static File getOutputFile(File inputDir, File inputFile, File outputDir)
            throws IOException {
        String relativePath = FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir);
        return new File(outputDir, relativePath);
    }
}
