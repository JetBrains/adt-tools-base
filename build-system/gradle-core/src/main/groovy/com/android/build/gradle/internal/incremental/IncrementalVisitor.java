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

package com.android.build.gradle.internal.incremental;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IncrementalVisitor extends ClassVisitor {

    private static final ILogger LOG = LoggerWrapper.getLogger(IncrementalVisitor.class);

    /**
     * Defines the output type from this visitor.
     */
    public enum OutputType {
        /**
         * provide instrumented classes that can be hot swapped at runtime with an override class.
         */
        INSTRUMENT,
        /**
         * provide override classes that be be used to hot swap an instrumented class.
         */
        OVERRIDE
    }

    public static final String PACKAGE = "com/android/tools/fd/runtime";
    public static final String ABSTRACT_PATCHES_LOADER_IMPL =
            PACKAGE + "/AbstractPatchesLoaderImpl";
    public static final String APP_PATCHES_LOADER_IMPL = PACKAGE + "/AppPatchesLoaderImpl";
    protected static final Type INSTANT_RELOAD_EXCEPTION =
            Type.getType(PACKAGE + "/InstantReloadException");
    protected static final Type RUNTIME_TYPE = Type.getType("L" + PACKAGE + "/AndroidInstantRuntime;");
    public static final Type DISABLE_ANNOTATION_TYPE =
            Type.getType("Lcom/android/tools/ir/api/DisableInstantRun;");

    protected static final boolean TRACING_ENABLED = Boolean.getBoolean("FDR_TRACING");

    public static final Type CHANGE_TYPE = Type.getType("L" + PACKAGE + "/IncrementalChange;");

    protected String visitedClassName;
    protected String visitedSuperName;
    @NonNull
    protected final ClassNode classNode;
    @NonNull
    protected final List<ClassNode> parentNodes;

    /**
     * Enumeration describing a method of field access rights.
     */
    protected enum AccessRight {
        PRIVATE, PACKAGE_PRIVATE, PROTECTED, PUBLIC;

        @NonNull
        static AccessRight fromNodeAccess(int nodeAccess) {
            if ((nodeAccess & Opcodes.ACC_PRIVATE) != 0) return PRIVATE;
            if ((nodeAccess & Opcodes.ACC_PROTECTED) != 0) return PROTECTED;
            if ((nodeAccess & Opcodes.ACC_PUBLIC) != 0) return PUBLIC;
            return PACKAGE_PRIVATE;
        }
    }

    public IncrementalVisitor(
            @NonNull ClassNode classNode,
            @NonNull List<ClassNode> parentNodes,
            @NonNull ClassVisitor classVisitor) {
        super(Opcodes.ASM5, classVisitor);
        this.classNode = classNode;
        this.parentNodes = parentNodes;
        LOG.info("%s: Visiting %s", getClass().getSimpleName(), classNode.name);
    }

    @NonNull
    protected static String getRuntimeTypeName(@NonNull Type type) {
        return "L" + type.getInternalName() + ";";
    }

    @Nullable
    FieldNode getFieldByName(@NonNull String fieldName) {
        FieldNode fieldNode = getFieldByNameInClass(fieldName, classNode);
        Iterator<ClassNode> iterator = parentNodes.iterator();
        while(fieldNode == null && iterator.hasNext()) {
            ClassNode parentNode = iterator.next();
            fieldNode = getFieldByNameInClass(fieldName, parentNode);
        }
        return fieldNode;
    }

    @Nullable
    protected static FieldNode getFieldByNameInClass(
            @NonNull String fieldName, @NonNull ClassNode classNode) {
        //noinspection unchecked ASM api.
        List<FieldNode> fields = classNode.fields;
        for (FieldNode field: fields) {
            if (field.name.equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    @Nullable
    protected MethodNode getMethodByName(String methodName, String desc) {
        MethodNode methodNode = getMethodByNameInClass(methodName, desc, classNode);
        Iterator<ClassNode> iterator = parentNodes.iterator();
        while(methodNode == null && iterator.hasNext()) {
            ClassNode parentNode = iterator.next();
            methodNode = getMethodByNameInClass(methodName, desc, parentNode);
        }
        return methodNode;
    }

    @Nullable
    protected static MethodNode getMethodByNameInClass(String methodName, String desc, ClassNode classNode) {
        //noinspection unchecked ASM API
        List<MethodNode> methods = classNode.methods;
        for (MethodNode method : methods) {
            if (method.name.equals(methodName) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    protected static void trace(@NonNull GeneratorAdapter mv, @Nullable String s) {
        mv.push(s);
        mv.invokeStatic(Type.getType(PACKAGE + ".AndroidInstantRuntime"),
                Method.getMethod("void trace(String)"));
    }

    protected static void trace(@NonNull GeneratorAdapter mv, @Nullable String s1,
            @Nullable String s2) {
        mv.push(s1);
        mv.push(s2);
        mv.invokeStatic(Type.getType(PACKAGE + ".AndroidInstantRuntime"),
                Method.getMethod("void trace(String, String)"));
    }

    protected static void trace(@NonNull GeneratorAdapter mv, @Nullable String s1,
            @Nullable String s2, @Nullable String s3) {
        mv.push(s1);
        mv.push(s2);
        mv.push(s3);
        mv.invokeStatic(Type.getType(PACKAGE + ".AndroidInstantRuntime"),
                Method.getMethod("void trace(String, String, String)"));
    }

    protected static void trace(@NonNull GeneratorAdapter mv, @Nullable String s1,
            @Nullable String s2, @Nullable String s3, @Nullable String s4) {
        mv.push(s1);
        mv.push(s2);
        mv.push(s3);
        mv.push(s4);
        mv.invokeStatic(Type.getType(PACKAGE + ".AndroidInstantRuntime"),
                Method.getMethod("void trace(String, String, String, String)"));
    }

    protected static void trace(@NonNull GeneratorAdapter mv, int argsNumber) {
        StringBuilder methodSignature = new StringBuilder("void trace(String");
        for (int i=0 ; i < argsNumber-1; i++) {
            methodSignature.append(", String");
        }
        methodSignature.append(")");
        mv.invokeStatic(Type.getType(PACKAGE + ".AndroidInstantRuntime"),
                Method.getMethod(methodSignature.toString()));
    }

    /**
     * Simple Builder interface for common methods between all byte code visitors.
     */
    public interface VisitorBuilder {
        @NonNull
        IncrementalVisitor build(@NonNull ClassNode classNode,
                @NonNull List<ClassNode> parentNodes, @NonNull ClassVisitor classVisitor);

        @NonNull
        String getMangledRelativeClassFilePath(@NonNull String originalClassFilePath);

        @NonNull
        OutputType getOutputType();
    }

    protected static void main(
            @NonNull String[] args,
            @NonNull VisitorBuilder visitorBuilder) throws IOException {

        if (args.length != 3) {
            throw new IllegalArgumentException("Needs to be given an input and output directory "
                    + "and a classpath");
        }

        File srcLocation = new File(args[0]);
        File baseInstrumentedCompileOutputFolder = new File(args[1]);
        FileUtils.emptyFolder(baseInstrumentedCompileOutputFolder);

        Iterable<String> classPathStrings = Splitter.on(File.pathSeparatorChar).split(args[2]);
        List<URL> classPath = Lists.newArrayList();
        for (String classPathString : classPathStrings) {
            File path = new File(classPathString);
            if (!path.exists()) {
                throw new IllegalArgumentException(
                        String.format("Invalid class path element %s", classPathString));
            }
            classPath.add(path.toURI().toURL());
        }
        classPath.add(srcLocation.toURI().toURL());
        URL[] classPathArray = Iterables.toArray(classPath, URL.class);

        ClassLoader classesToInstrumentLoader = new URLClassLoader(classPathArray, null) {
            @Override
            public URL getResource(String name) {
                // Never delegate to bootstrap classes.
                return findResource(name);
            }
        };

        ClassLoader originalThreadContextClassLoader = Thread.currentThread()
                .getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classesToInstrumentLoader);
            instrumentClasses(srcLocation,
                    baseInstrumentedCompileOutputFolder, visitorBuilder);
        } finally {
            Thread.currentThread().setContextClassLoader(originalThreadContextClassLoader);
        }
    }

    private static void instrumentClasses(
            @NonNull File rootLocation,
            @NonNull File outLocation,
            @NonNull VisitorBuilder visitorBuilder) throws IOException {

        Iterable<File> files =
                Files.fileTreeTraverser().preOrderTraversal(rootLocation).filter(Files.isFile());

        for (File inputFile : files) {
            instrumentClass(rootLocation, inputFile, outLocation, visitorBuilder);
        }
    }

    /**
     * Defines when a method access flags are compatible with InstantRun technology.
     *
     * - If the method is a bridge method, we do not enable it for instantReload.
     *   it is most likely only calling a twin method (same name, same parameters).
     * - if the method is abstract, we don't add a redirection.
     *
     * @param access the method access flags
     * @return true if the method should be InstantRun enabled, false otherwise.
     */
    protected static boolean  isAccessCompatibleWithInstantRun(int access) {
        return ((access & Opcodes.ACC_ABSTRACT) == 0) && ((access & Opcodes.ACC_BRIDGE) == 0);
    }

    @Nullable
    public static File instrumentClass(
            @NonNull File inputRootDirectory,
            @NonNull File inputFile,
            @NonNull File outputDirectory,
            @NonNull VisitorBuilder visitorBuilder) throws IOException {

        byte[] classBytes;
        String path = FileUtils.relativePath(inputFile, inputRootDirectory);
        if (!inputFile.getPath().endsWith(SdkConstants.DOT_CLASS)) {
            File outputFile = new File(outputDirectory, path);
            Files.createParentDirs(outputFile);
            Files.copy(inputFile, outputFile);
            return outputFile;
        }
        classBytes = Files.toByteArray(inputFile);
        ClassReader classReader = new ClassReader(classBytes);
        // override the getCommonSuperClass to use the thread context class loader instead of
        // the system classloader. This is useful as ASM needs to load classes from the project
        // which the system classloader does not have visibility upon.
        // TODO: investigate if there is not a simpler way than overriding.
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(final String type1, final String type2) {
                Class<?> c, d;
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                try {
                    c = Class.forName(type1.replace('/', '.'), false, classLoader);
                    d = Class.forName(type2.replace('/', '.'), false, classLoader);
                } catch (Exception e) {
                    throw new RuntimeException(e.toString());
                }
                if (c.isAssignableFrom(d)) {
                    return type1;
                }
                if (d.isAssignableFrom(c)) {
                    return type2;
                }
                if (c.isInterface() || d.isInterface()) {
                    return "java/lang/Object";
                } else {
                    do {
                        c = c.getSuperclass();
                    } while (!c.isAssignableFrom(d));
                    return c.getName().replace('.', '/');
                }
            }
        };

        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);

        // when dealing with interface, we just copy the inputFile over without any changes unless
        // this is a package private interface.
        AccessRight accessRight = AccessRight.fromNodeAccess(classNode.access);
        File outputFile = new File(outputDirectory, path);
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                // don't change the name of interfaces.
                Files.createParentDirs(outputFile);
                if (accessRight == AccessRight.PACKAGE_PRIVATE) {
                    classNode.access = classNode.access | Opcodes.ACC_PUBLIC;
                    classNode.accept(classWriter);
                    Files.write(classWriter.toByteArray(), outputFile);
                } else {
                    // just copy the input file over, no change.
                    Files.write(classBytes, outputFile);
                }
                return outputFile;
            } else {
                return null;
            }
        }

        if (isPackageInstantRunDisabled(inputFile, classNode)) {
            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                Files.createParentDirs(outputFile);
                Files.write(classBytes, outputFile);
                return outputFile;
            } else {
                return null;
            }
        }

        List<ClassNode> parentsNodes = parseParents(inputFile, classNode);
        outputFile = new File(outputDirectory, visitorBuilder.getMangledRelativeClassFilePath(path));
        Files.createParentDirs(outputFile);
        IncrementalVisitor visitor = visitorBuilder.build(classNode, parentsNodes, classWriter);
        classNode.accept(visitor);

        Files.write(classWriter.toByteArray(), outputFile);
        return outputFile;
    }

    @NonNull
    private static File getBinaryFolder(@NonNull File inputFile, @NonNull ClassNode classNode) {
        return new File(inputFile.getAbsolutePath().substring(0,
                inputFile.getAbsolutePath().length() - (classNode.name.length() + ".class".length())));
    }

    @NonNull
    private static List<ClassNode> parseParents(
            @NonNull File inputFile, @NonNull ClassNode classNode) throws IOException {
        File binaryFolder = getBinaryFolder(inputFile, classNode);
        List<ClassNode> parentNodes = new ArrayList<ClassNode>();
        String currentParentName = classNode.superName;

        while (currentParentName != null) {
            File parentFile = new File(binaryFolder, currentParentName + ".class");
            if (parentFile.exists()) {
                LOG.info("Parsing %s.", parentFile);
                InputStream parentFileClassReader = new BufferedInputStream(new FileInputStream(parentFile));
                ClassReader parentClassReader = new ClassReader(parentFileClassReader);
                ClassNode parentNode = new ClassNode();
                parentClassReader.accept(parentNode, ClassReader.EXPAND_FRAMES);
                parentNodes.add(parentNode);
                currentParentName = parentNode.superName;
            } else {
                // May need method information from outside of the current project. Thread local class reader
                // should be the one
                try {
                    ClassReader parentClassReader = new ClassReader(
                            Thread.currentThread().getContextClassLoader().getResourceAsStream(
                                    currentParentName + ".class"));
                    ClassNode parentNode = new ClassNode();
                    parentClassReader.accept(parentNode, ClassReader.EXPAND_FRAMES);
                    parentNodes.add(parentNode);
                    currentParentName = parentNode.superName;
                } catch (IOException e) {
                    // Could not locate parent class. This is as far as we can go locating parents.
                    LOG.error(e, "IncrementalVisitor parseParents could not locate class %s.\n", currentParentName);
                    currentParentName = null;
                }
            }
        }
        return parentNodes;
    }

    @Nullable
    private static ClassNode parsePackageInfo(
            @NonNull File inputFile, @NonNull ClassNode classNode) throws IOException {

        File packageFolder = inputFile.getParentFile();
        File packageInfoClass = new File(packageFolder, "package-info.class");
        if (packageInfoClass.exists()) {
            InputStream reader = new BufferedInputStream(new FileInputStream(packageInfoClass));
            ClassReader classReader = new ClassReader(reader);
            ClassNode packageInfo = new ClassNode();
            classReader.accept(packageInfo, ClassReader.EXPAND_FRAMES);
            return packageInfo;
        }
        return null;
    }

    private static boolean isPackageInstantRunDisabled(
            @NonNull File inputFile, @NonNull ClassNode classNode) throws IOException {

        ClassNode packageInfoClass = parsePackageInfo(inputFile, classNode);
        if (packageInfoClass != null) {
            //noinspection unchecked
            List<AnnotationNode> annotations = packageInfoClass.invisibleAnnotations;
            if (annotations == null) {
                return false;
            }
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(DISABLE_ANNOTATION_TYPE.getDescriptor())) {
                    return true;
                }
            }
        }
        return false;
    }
}
