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

package com.android.build.gradle.tasks;

import com.android.build.gradle.internal.incremental.IncrementalChangeVisitor;
import com.android.build.gradle.internal.incremental.IncrementalSupportVisitor;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Task to prepare .class files for dynamic reloading.
 *
 * Each class from the input directory is processed through the {@link IncrementalSupportVisitor}
 * to inject bytecodes to allow code reloading.
 */
@ParallelizableTask
public class SourceCodeIncrementalSupport extends DefaultTask {

    File binaryFolder;
    File enhancedFolder;
    File patchedFolder;

    Project project;

    @OutputDirectory
    public File getEnhancedFolder() {
        return enhancedFolder;
    }

    @InputDirectory
    public File getBinaryFolder() {
        return binaryFolder;
    }

    @OutputDirectory
    public File getPatchedFolder() {
        return patchedFolder;
    }

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {

        System.out.println("generating in" + getEnhancedFolder().getAbsolutePath());

        if (!inputs.isIncremental()) {
            if (getEnhancedFolder().exists()) {
                project.delete(getEnhancedFolder().listFiles());
            }
            getEnhancedFolder().mkdirs();
            processDirectory(getBinaryFolder());
        }

        final ImmutableList.Builder<String> patchFileContents = ImmutableList.builder();
        inputs.outOfDate(new Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {

                processNewFile(inputFileDetails.getFile());
                if (inputFileDetails.isModified()) {
                    System.out.println("Incremental support change detected "
                            + inputFileDetails.getFile().getAbsolutePath());
                    patchFileContents.add(createPatchFile(inputFileDetails.getFile()));
                }
            }
        });

        writePatchFileContents(patchFileContents.build());

        inputs.removed(new Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails inputFileDetails) {
                System.out.println("Incremental support removed "
                        + inputFileDetails.getFile().getAbsolutePath());
                project.relativePath(inputFileDetails);
                new File(getEnhancedFolder(), project.relativePath(inputFileDetails)).delete();
            }
        });
    }

    private void writePatchFileContents(ImmutableList<String> patchFileContents) {

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                "com/android/build/gradle/internal/incremental/AppPatchesLoaderImpl", null,
                "com/android/build/gradle/internal/incremental/AbstractPatchesLoaderImpl", null);

        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/android/build/gradle/internal/incremental/AbstractPatchesLoaderImpl", "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getPatchedClasses", "()[Ljava/lang/String;", null, null);
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
        File outputDir = new File(getPatchedFolder(), "com/android/build/gradle/internal/incremental/");
        outputDir.mkdirs();
        try {
            Files.write(classBytes, new File(outputDir, "AppPatchesLoaderImpl.class"));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private String createPatchFile(File inputFile) {
        InputStream classFileReader = null;
        try {
            classFileReader = new FileInputStream(inputFile);
            ClassReader classReader = new ClassReader(classFileReader);
            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);
            String rootName = inputFile.getName()
                    .substring(0, inputFile.getName().length() - ".class".length());
            if (isRuntimeLibraryClass(inputFile)) {
                System.out.println("Skipping runtime library class " + inputFile);
                classReader.accept(classWriter, ClassReader.EXPAND_FRAMES);
            } else {
                ClassNode classNode = new ClassNode();
                classReader.accept(classNode, ClassReader.EXPAND_FRAMES | ClassReader.SKIP_CODE);
                // get all type hierarchy.
                // for now we assume they are co-located which is obviously not always true.
                List<ClassNode> parentNodes = parseParents(inputFile, classNode);
                IncrementalChangeVisitor visitor = new IncrementalChangeVisitor(classNode, parentNodes, classWriter);
                classReader.accept(visitor, ClassReader.EXPAND_FRAMES);
            }
            // write the modified class.
            String relativeFilePath = inputFile.getAbsolutePath().substring(
                    getBinaryFolder().getAbsolutePath().length());
            File outputDirectory = new File(getPatchedFolder(), relativeFilePath).getParentFile();
            outputDirectory.mkdirs();
            File outFile = new File(outputDirectory,
                    inputFile.getName().substring(0, inputFile.getName().length() - ".class".length())
                        + "$override.class");
            FileOutputStream stream = new FileOutputStream(outFile.getAbsolutePath());
            try {
                stream.write(classWriter.toByteArray());
            } finally {
                stream.close();
            }
            return relativeFilePath.substring(1,
                    relativeFilePath.lastIndexOf('/')).replaceAll("/", ".") + "." + rootName;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            if (classFileReader != null) {
                try {
                    classFileReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private List<ClassNode> parseParents(File inputFile, ClassNode classNode) throws IOException {
        File binaryFolder = new File(inputFile.getAbsolutePath().substring(0,
                inputFile.getAbsolutePath().length() - (classNode.name.length() + ".class".length())));
        List<ClassNode> parentNodes = new ArrayList<ClassNode>();
        String currentParentName = classNode.superName;
        while (!currentParentName.equals(Type.getType(Object.class).getInternalName())) {
            File parentFile = new File(binaryFolder, currentParentName + ".class");
            System.out.println("parsing " + parentFile);
            if (parentFile.exists()) {
                InputStream parentFileClassReader = new BufferedInputStream(new FileInputStream(parentFile));
                ClassReader parentClassReader = new ClassReader(parentFileClassReader);
                ClassNode parentNode = new ClassNode();
                parentClassReader.accept(parentNode, ClassReader.EXPAND_FRAMES | ClassReader.SKIP_CODE);
                parentNodes.add(parentNode);
                currentParentName = parentNode.superName;
            } else {
                return parentNodes;
            }
        }
        return parentNodes;
    }

    private void processDirectory(File inputDir) {
        for (File inputFile : inputDir.listFiles()) {
            if (inputFile.isDirectory()) {
                processDirectory(inputFile);
                return;
            }
            processNewFile(inputFile);
        }
    }

    private void processNewFile(File inputFile) {
        InputStream classFileReader = null;
        try {
            classFileReader = new FileInputStream(inputFile);
            ClassReader classReader = new ClassReader(classFileReader);
            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);
            if (isRuntimeLibraryClass(inputFile)) {
                System.out.println("Skipping runtime library class " + inputFile);
                classReader.accept(classWriter, ClassReader.EXPAND_FRAMES);
            } else {
                ClassNode classNode = new ClassNode();
                IncrementalSupportVisitor visitor = new IncrementalSupportVisitor(classNode, Collections.<ClassNode>emptyList(), classWriter);
                classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
                classNode.accept(visitor);
            }

            // write the modified class.
            String relativeFilePath = inputFile.getAbsolutePath().substring(
                    getBinaryFolder().getAbsolutePath().length());
            File outFile = new File(getEnhancedFolder(), relativeFilePath);
            outFile.getParentFile().mkdirs();
            FileOutputStream stream = new FileOutputStream(outFile.getAbsolutePath());
            try {
                stream.write(classWriter.toByteArray());
            } finally {
                stream.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (classFileReader != null) {
                try {
                    classFileReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // HACKY implementation used to blacklist runtime library classes from BCI.
    // Longer term we should have just a single runtime package, and we should be performing
    // this check inside the bytecode visitor such that we can accurately check the
    // class owner to filter by package rather than doing file path checks like this.
    private static boolean isRuntimeLibraryClass(File inputFile) {
        File parentFile = inputFile.getParentFile();
        if (parentFile != null && parentFile.getName().equals("runtime")) {
            parentFile = parentFile.getParentFile();
            if (parentFile != null && parentFile.getName().equals("fd")) {
                // com.android.tools.fd.runtime
                return true;
            }
        } else if (parentFile != null && parentFile.getName().equals("incremental")) {
            parentFile = parentFile.getParentFile();
            if (parentFile != null && parentFile.getName().equals("internal")) {
                // com.android.build.gradle.internal.incremental
                return true;
            }

        }
        return false;
    }

    public static class ConfigAction implements TaskConfigAction<SourceCodeIncrementalSupport> {

        private final VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @Override
        public String getName() {
            return scope.getTaskName("incremental", "SourceCodeSupport");
        }

        @Override
        public Class<SourceCodeIncrementalSupport> getType() {
            return SourceCodeIncrementalSupport.class;
        }

        @Override
        public void execute(SourceCodeIncrementalSupport sourceCodeIncrementalSupport) {
            ConventionMappingHelper.map(sourceCodeIncrementalSupport, "binaryFolder", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return scope.getJavaOutputDir();
                }
            });
            ConventionMappingHelper.map(sourceCodeIncrementalSupport, "enhancedFolder", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return scope.getInitialIncrementalSupportJavaOutputDir();
                }
            });
            ConventionMappingHelper.map(sourceCodeIncrementalSupport, "patchedFolder", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return scope.getIncrementalSupportJavaOutputDir();
                }
            });
            sourceCodeIncrementalSupport.project = scope.getGlobalScope().getProject();
        }
    }
}
