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
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.incremental.IncrementalChangeVisitor;
import com.android.build.gradle.internal.incremental.IncrementalSupportVisitor;
import com.android.build.gradle.internal.incremental.IncrementalVisitor;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.transform.api.ForkTransform;
import com.android.build.transform.api.ScopedContent;
import com.android.build.transform.api.Transform;
import com.android.build.transform.api.TransformException;
import com.android.build.transform.api.TransformInput;
import com.android.build.transform.api.TransformOutput;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.logging.Logging;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the {@link Transform} to run the byte code enhancement logic on compiled
 * classes in order to support runtime hot swapping.
 */
public class InstantRunTransform implements ForkTransform {

    protected static final ILogger LOGGER =
            new LoggerWrapper(Logging.getLogger(InstantRunTransform.class));
    private final ImmutableList.Builder<String> generatedClasses2Files = ImmutableList.builder();
    private final ImmutableList.Builder<String> generatedClasses3Names = ImmutableList.builder();
    private final ImmutableList.Builder<String> generatedClasses3Files = ImmutableList.builder();

    enum RecordingPolicy {RECORD, DO_NOT_RECORD}


    @NonNull
    @Override
    public String getName() {
        return "instantRun";
    }

    @NonNull
    @Override
    public Set<ScopedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<ScopedContent.ContentType> getOutputTypes() {
        return Sets.immutableEnumSet(ScopedContent.ContentType.CLASSES,
                ScopedContent.ContentType.CLASSES_ENHANCED);
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
        return Type.FORK_INPUT;
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

    @NonNull
    @Override
    public Collection<File> getSecondaryFolderOutputs() {
        return ImmutableList.of();
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull Map<TransformInput, Collection<TransformOutput>> inputs,
            @NonNull Collection<TransformInput> referencedInputs, boolean isIncremental)
            throws IOException, TransformException, InterruptedException {


        for (final Map.Entry<TransformInput, Collection<TransformOutput>> entry : inputs.entrySet()) {
            TransformInput transformInput = entry.getKey();
            if (transformInput.getFiles().size() != 1) {
                throw new RuntimeException("Multiple input folders detected : " +
                    Joiner.on(",").join(transformInput.getFiles()));
            }
            final File inputDir = transformInput.getFiles().iterator().next();
            if (inputDir.isFile()) {
                throw new RuntimeException(
                        "Expected a directory in non incremental, got a file " +
                                inputDir.getAbsolutePath());

            }
            final TransformOutput classesTwoOutput = getTransformOutput(entry.getValue(),
                    ScopedContent.ContentType.CLASSES);
            if (classesTwoOutput == null) {
                throw new RuntimeException(
                        "Cannot find TransformOutput for " + ScopedContent.ContentType.CLASSES);
            }
            final TransformOutput classesThreeOutput = getTransformOutput(entry.getValue(),
                    ScopedContent.ContentType.CLASSES_ENHANCED);
            if (classesThreeOutput == null) {
                throw new RuntimeException(
                        "Cannot find TransformOutput for " + ScopedContent.ContentType.CLASSES_ENHANCED);
            }

            if (isIncremental) {

                for (Map.Entry<File, TransformInput.FileStatus> changedEntry :
                        transformInput.getChangedFiles().entrySet()) {

                    File inputFile = changedEntry.getKey();
                    switch(changedEntry.getValue()) {
                        case REMOVED:
                            // remove the classes.2 and classes.3 files.
                            File classes_2 = getOutputFile(inputDir, inputFile,
                                    classesTwoOutput.getOutFile());
                            if (classes_2.exists() && !classes_2.delete()) {
                                // it's not a big deal if the file cannot be deleted, hopefully
                                // no code is still referencing it, yet we should notify.
                                LOGGER.warning("Cannot delete %1$s file", classes_2);
                            }
                            File classes_3 = getOutputPatchFile(inputDir, inputFile,
                                    classesThreeOutput.getOutFile());
                            if (classes_3.exists() && !classes_3.delete()) {
                                LOGGER.warning("Cannot delete %1$s file", classes_3);
                            }
                            break;
                        case ADDED:
                            // a new file was added, we only generate the classes.2 format
                            transformToClasses2Format(
                                    inputDir,
                                    inputFile,
                                    classesTwoOutput.getOutFile(),
                                    RecordingPolicy.RECORD);
                            break;
                        case CHANGED:
                            // an existing file was changed, we regenerate the classes.2 and
                            // classes.3 files at they are both needed to support restart and
                            // reload.
                            transformToClasses2Format(
                                    inputDir,
                                    inputFile,
                                    classesTwoOutput.getOutFile(), RecordingPolicy.RECORD);
                            transformToClasses3Format(
                                    inputDir,
                                    inputFile,
                                    classesThreeOutput.getOutFile());
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
                        // do not record the changes, everything should be packaged in the
                        // main APK.
                        transformToClasses2Format(
                                inputDir,
                                file,
                                classesTwoOutput.getOutFile(),
                                RecordingPolicy.DO_NOT_RECORD);
                    } catch (IOException e) {
                        throw new RuntimeException("Exception while preparing "
                                + file.getAbsolutePath());
                    }

                }
            }
            wrapUpOutputs(classesTwoOutput, classesThreeOutput);
        }
    }

    protected void wrapUpOutputs(TransformOutput classes2Output, TransformOutput classes3Output)
            throws IOException {

        // generate the patch file and add to the list of files to process next.
        File patchFile = writePatchFileContents(generatedClasses3Names.build(),
                classes3Output.getOutFile());
        generatedClasses3Files.add(patchFile.getAbsolutePath());

        writeIncrementalChanges(generatedClasses2Files.build(), classes2Output.getOutFile());
        writeIncrementalChanges(generatedClasses3Files.build(), classes3Output.getOutFile());
    }

    /**
     * Transform a single file into a format supporting class hot swap.
     *
     * @param inputDir the input directory containing the input file.
     * @param inputFile the input file within the input directory to transform.
     * @param outputDir the output directory where to place the transformed file.
     * @throws IOException if the transformation failed.
     */
    protected void transformToClasses2Format(
            @NonNull final File inputDir,
            @NonNull final File inputFile,
            @NonNull final File outputDir,
            @NonNull final RecordingPolicy recordingPolicy)
            throws IOException {

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

        if (recordingPolicy == RecordingPolicy.RECORD) {
            generatedClasses2Files.add(outputFile.getAbsolutePath());
        }
    }

    /**
     * Transform a single file into a {@link ScopedContent.ContentType#CLASSES_ENHANCED} format
     *
     * @param inputDir the input directory containing the input file.
     * @param inputFile the input file within the input directory to transform.
     * @param outputDir the output directory where to place the transformed file.
     * @throws IOException if the transformation failed.
     */
    protected void transformToClasses3Format(File inputDir, File inputFile, File outputDir)
            throws IOException {

        File outputFile = getOutputPatchFile(inputDir, inputFile, outputDir);
        Files.createParentDirs(outputFile.getParentFile());

        IncrementalVisitor.instrumentClass(inputFile, outputFile,
                new IncrementalVisitor.VisitorBuilder() {
                    @Override
                    public IncrementalVisitor build(@NonNull ClassNode classNode,
                            List<ClassNode> parentNodes,
                            ClassVisitor classVisitor) {
                        return new IncrementalChangeVisitor(classNode, parentNodes, classVisitor);
                    }

                    @Override
                    public boolean processParents() {
                        return true;
                    }
                });

        generatedClasses3Names.add(
                inputFile.getAbsolutePath().substring(
                    inputDir.getAbsolutePath().length() + 1,
                    inputFile.getAbsolutePath().length() - ".class".length())
                        .replace('/', '.'));
        generatedClasses3Files.add(outputFile.getAbsolutePath());
    }

    /**
     * Use asm to generate a concrete subclass of the AppPathLoaderImpl class.
     * It only implements one method :
     *      String[] getPatchedClasses();
     *
     * The method is supposed to return the list of classes that were patched in this iteration.
     * This will be used by the InstantRun runtime to load all patched classes and register them
     * as overrides on the original classes.2 class files.
     *
     * @param patchFileContents list of patched class names.
     * @param outputDir output directory where to generate the .class file in.
     * @return the generated .class files
     */
    private static File writePatchFileContents(
            ImmutableList<String> patchFileContents, File outputDir) {

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                "com/android/build/gradle/internal/incremental/AppPatchesLoaderImpl", null,
                "com/android/build/gradle/internal/incremental/AbstractPatchesLoaderImpl", null);

        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "com/android/build/gradle/internal/incremental/AbstractPatchesLoaderImpl",
                    "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC,
                    "getPatchedClasses", "()[Ljava/lang/String;", null, null);
            mv.visitCode();
            mv.visitIntInsn(Opcodes.BIPUSH, patchFileContents.size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
            for (int index=0; index < patchFileContents.size(); index++) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitIntInsn(Opcodes.BIPUSH, index);
                mv.visitLdcInsn(patchFileContents.get(index));
                mv.visitInsn(Opcodes.AASTORE);
            }
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(4, 1);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] classBytes = cw.toByteArray();
        File outputFile = new File(
                new File(outputDir, "com/android/build/gradle/internal/incremental/"),
                "AppPatchesLoaderImpl.class");
        try {
            Files.createParentDirs(outputFile);
            Files.write(classBytes, outputFile);
            // add the files to the list of files to be processed by subsequent tasks.
            return outputFile;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static void writeIncrementalChanges(List<String> changes, File outputDir) throws IOException {

        FileWriter fileWriter = new FileWriter(new File(outputDir, "incrementalChanges.txt"));
        try {
            for (String change : changes) {
                fileWriter.write(change);
                fileWriter.write("\n");
            }
        } finally {
            fileWriter.close();
        }
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

    /**
     * Return the expected output {@link File} for an input file located in the transform input
     * directory. The output file will have a similar relative path than the input file (to the
     * input dir) inside the output directory and will be formed to be consistent with the
     * {@link ScopedContent.ContentType#CLASSES_ENHANCED} format
     *
     * @param inputDir the input directory containing the input file
     * @param inputFile the input file within the input directory
     * @param outputDir the output directory
     * @return the output file within the output directory with the right relative path.
     * @throws IOException
     */
    protected File getOutputPatchFile(File inputDir, File inputFile, File outputDir)
            throws IOException {

        File usualOutputFile = getOutputFile(inputDir, inputFile, outputDir);

        File outputDirectory = usualOutputFile.getParentFile();
        String rootName = Files.getNameWithoutExtension(inputFile.getName());

        return new File(outputDirectory, rootName + "$override.class");
    }

    @Nullable
    private static TransformOutput getTransformOutput(
            Collection<TransformOutput> outputs, ScopedContent.ContentType contentType) {

        for (TransformOutput transformOutput : outputs) {
            if (transformOutput.getContentTypes().contains(contentType)) {
                return transformOutput;
            }
        }
        return null;
    }
}
