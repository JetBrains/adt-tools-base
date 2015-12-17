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

package com.android.builder.shrinker;

import com.android.annotations.Nullable;
import com.android.utils.AsmUtils;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.Set;

/**
 * {@link ClassVisitor} that finds all dependencies that should be added to the shrinker graph.
 *
 * <p>Subclasses should implement the {@link #handleDependency(Object, Object, DependencyType)}
 * method.
 */
public abstract class DependencyFinderVisitor<T> extends ClassVisitor {
    private final ShrinkerGraph<T> mGraph;
    private final Set<T> mVirtualMethods;
    private final Set<T> mMultipleInheritance;
    private final Set<Shrinker.UnresolvedReference<T>> mUnresolvedReferences;

    private String mClassName;
    private boolean mIsAnnotation;
    private T mKlass;

    public DependencyFinderVisitor(
            ShrinkerGraph<T> graph,
            ClassVisitor cv,
            Set<T> virtualMethods,
            Set<Shrinker.UnresolvedReference<T>> unresolvedReferences,
            Set<T> multipleInheritance) {
        super(Opcodes.ASM5, cv);
        mGraph = graph;
        mVirtualMethods = virtualMethods;
        mUnresolvedReferences = unresolvedReferences;
        mMultipleInheritance = multipleInheritance;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        if (interfaces == null) {
            interfaces = new String[0];
        }

        mKlass = mGraph.getClassReference(name);
        if (!superName.equals("java/lang/Object")) {
            // Don't create graph edges for obvious things.
            handleDependency(mKlass, mGraph.getClassReference(superName), DependencyType.REQUIRED);
        }

        // We don't create edges for interfaces, because interfaces can be removed by the shrinker,
        // if they are not used.

        if (interfaces.length > 0 && !mGraph.isLibraryClass(mGraph.getClassReference(superName))) {
            // It's possible the superclass is implementing a method from the interface, we may need
            // to add more edges to the graph to represent this.
            mMultipleInheritance.add(mKlass);
        }


        mClassName = name;
        mIsAnnotation = (access & Opcodes.ACC_ANNOTATION) != 0;

        if (signature != null) {
            handleClassSignature(mKlass, signature);
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        T method = mGraph.getMemberReference(mClassName, name, desc);

        if ((access & Opcodes.ACC_STATIC) == 0
                && !name.equals(AsmUtils.CONSTRUCTOR)
                && mVirtualMethods != null) {
            mVirtualMethods.add(method);
        }

        Type methodType = Type.getMethodType(desc);
        handleDeclarationType(method, methodType.getReturnType());
        for (Type argType : methodType.getArgumentTypes()) {
            handleDeclarationType(method, argType);
        }

        if (name.equals(AsmUtils.CLASS_INITIALIZER)) {
            handleDependency(mKlass, method, DependencyType.REQUIRED);
        }

        if (mIsAnnotation) {
            // TODO: Strip annotation members.
            handleDependency(mKlass, method, DependencyType.REQUIRED);
        }

        if (signature != null) {
            handleClassSignature(method, signature);
        }

        return new DependencyFinderMethodVisitor(
                method,
                super.visitMethod(access, name, desc, signature, exceptions));
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
            Object value) {
        T field = mGraph.getMemberReference(mClassName, name, desc);
        Type fieldType = Type.getType(desc);
        handleDeclarationType(field, fieldType);

        if (signature != null) {
            SignatureReader reader = new SignatureReader(signature);
            SignatureVisitor visitor = new DependencyFinderSignatureVisitor(field);
            reader.acceptType(visitor);
        }

        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (!visible) {
            return super.visitAnnotation(desc, false);
        } else {
            Type type = Type.getType(desc);
            handleDeclarationType(mKlass, type);
            return new DependencyFinderAnnotationVisitor(
                    type.getInternalName(),
                    mKlass,
                    super.visitAnnotation(desc, true));
        }
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (mClassName.equals(name) && outerName != null) {
            // I'm the inner class, keep the outer class, as ProGuard does.
            // TODO: What if I'm the enclosing class? What if the inner class is not used?
            handleDependency(mKlass, mGraph.getClassReference(outerName), DependencyType.REQUIRED);
        }
        super.visitInnerClass(name, outerName, innerName, access);
    }

    private T handleDeclarationType(T member, Type type) {
        String className = getClassName(type);
        if (className != null) {
            T classReference = mGraph.getClassReference(className);
            handleDependency(member, classReference, DependencyType.REQUIRED);
            return classReference;
        }
        return null;
    }

    private void handleClassSignature(T source, String signature) {
        SignatureReader reader = new SignatureReader(signature);
        SignatureVisitor visitor = new DependencyFinderSignatureVisitor(source);
        reader.accept(visitor);
    }

    @Nullable
    private static String getClassName(String desc) {
        return getClassName(Type.getType(desc));
    }

    @Nullable
    private static String getClassName(Type type) {
        switch (type.getSort()) {
            case Type.VOID:
            case Type.METHOD:
            case Type.SHORT:
            case Type.INT:
            case Type.LONG:
            case Type.FLOAT:
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.DOUBLE:
            case Type.CHAR:
                return null;
            case Type.ARRAY:
                return getClassName(type.getElementType());
            case Type.OBJECT:
                return type.getInternalName();
            default:
                throw new IllegalStateException();
        }
    }

    protected abstract void handleDependency(T source, T target, DependencyType type);

    private class DependencyFinderMethodVisitor extends MethodVisitor {

        private final T mMethod;

        public DependencyFinderMethodVisitor(T method, MethodVisitor mv) {
            super(Opcodes.ASM5, mv);
            this.mMethod = method;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (!visible) {
                return super.visitAnnotation(desc, false);
            } else {
                Type type = Type.getType(desc);
                handleDeclarationType(mMethod, type);
                return new DependencyFinderAnnotationVisitor(
                        type.getInternalName(),
                        mMethod,
                        super.visitAnnotation(desc, true));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            return new DependencyFinderAnnotationVisitor(null, mMethod, super.visitAnnotationDefault());
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            T classReference = mGraph.getClassReference(getClassName(Type.getObjectType(type)));
            if (classReference != null) {
                handleDependency(mMethod, classReference, DependencyType.REQUIRED);
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            handleDependency(mMethod, mGraph.getClassReference(owner), DependencyType.REQUIRED);
            T target = mGraph.getMemberReference(owner, name, desc);
            mUnresolvedReferences.add(
                    new Shrinker.UnresolvedReference<T>(mMethod, target, opcode));
            super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            if (cst instanceof Type) {
                Type type = Type.getObjectType(((Type) cst).getInternalName());
                T classReference = mGraph.getClassReference(getClassName(type));
                if (classReference != null) {
                    handleDependency(mMethod, classReference, DependencyType.REQUIRED);
                }
            }
            super.visitLdcInsn(cst);
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String desc, boolean itf) {
            // This can be the case when calling "clone" on arrays, which is done in Enum classes.
            // Just ignore it, we know arrays not declared in the program, so there's no point in
            // creating the dependency.
            Type type = Type.getType(owner);
            if (type.getSort() != Type.ARRAY
                    // TODO: Add a flag to disable these checks?
                    && !owner.startsWith("java/")
                    && !owner.startsWith("android/os/")
                    && !owner.startsWith("android/view/")
                    && !owner.startsWith("android/content/")
                    && !owner.startsWith("android/graphics/")
                    && !owner.startsWith("android/widget/")) {
                handleDependency(mMethod, mGraph.getClassReference(owner), DependencyType.REQUIRED);

                T target = mGraph.getMemberReference(owner, name, desc);

                if (opcode == Opcodes.INVOKESPECIAL
                        && (name.equals(AsmUtils.CONSTRUCTOR) || owner.equals(mClassName))) {
                    // The "invokenonvirtual" semantics of invokespecial, for calling constructors
                    // and private methods.
                    handleDependency(mMethod, target, DependencyType.REQUIRED);
                } else {
                    // In all other cases we have to go through resolution stage (including fields,
                    // static methods etc).
                    mUnresolvedReferences.add(
                            new Shrinker.UnresolvedReference<T>(mMethod, target, opcode));
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            String className = getClassName(desc);
            if (className != null) {
                handleDependency(mMethod, mGraph.getClassReference(className), DependencyType.REQUIRED);
            }
            super.visitMultiANewArrayInsn(desc, dims);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            if (type != null) {
                handleDependency(mMethod, mGraph.getClassReference(type), DependencyType.REQUIRED);
            }
            super.visitTryCatchBlock(start, end, handler, type);
        }
    }

    private class DependencyFinderAnnotationVisitor extends AnnotationVisitor {
        private final String mAnnotationName;
        private final T mSource;

        public DependencyFinderAnnotationVisitor(String annotationName, T source, AnnotationVisitor av) {
            super(Opcodes.ASM5, av);
            mAnnotationName = annotationName;
            mSource = source;
        }

        @Override
        public void visit(String name, Object value) {
            if (value instanceof Type) {
                handleDeclarationType(mSource, (Type) value);
            }
            super.visit(name, value);
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
            String internalName = getClassName(desc);

            handleDependency(
                    mSource,
                    mGraph.getClassReference(internalName),
                    DependencyType.REQUIRED);
            handleDependency(
                    mSource,
                    mGraph.getMemberReference(internalName, value, desc),
                    DependencyType.REQUIRED);

            super.visitEnum(name, desc, value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            String internalName = getClassName(desc);
            handleDependency(mSource, mGraph.getClassReference(internalName), DependencyType.REQUIRED);
            return new DependencyFinderAnnotationVisitor(mAnnotationName, mSource, super.visitAnnotation(name, desc));
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return new DependencyFinderAnnotationVisitor(mAnnotationName, mSource, super.visitArray(name));
        }
    }

    private class DependencyFinderSignatureVisitor extends SignatureVisitor {

        private final T mSource;

        public DependencyFinderSignatureVisitor(T source) {
            super(Opcodes.ASM5);
            mSource = source;
        }

        @Override
        public void visitClassType(String name) {
            if (!name.equals("java/lang/Object")) {
                handleDependency(mSource, mGraph.getClassReference(name), DependencyType.REQUIRED);
            }
            super.visitClassType(name);
        }

        @Override
        public void visitInnerClassType(String name) {
            // TODO: support inner classes.
            super.visitInnerClassType(name);
        }
    }
}
