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

package com.android.build.gradle.shrinker;

import com.android.annotations.NonNull;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Collection;
import java.util.Set;

/**
 * Visitor for handling modified classes in an incremental run.
 */
class IncrementalRunVisitor<T> extends DependencyFinderVisitor<T> {

    private final ShrinkerGraph<T> mGraph;

    private final Collection<T> mClassesToWrite;

    private final Collection<PostProcessingData.UnresolvedReference<T>> mUnresolvedReferences;

    private String mClassName;

    private Set<T> mMethods;

    private Set<T> mFields;

    private Set<String> mAnnotations;

    public IncrementalRunVisitor(
            @NonNull ShrinkerGraph<T> graph,
            @NonNull Collection<T> classesToWrite,
            @NonNull Collection<PostProcessingData.UnresolvedReference<T>> unresolvedReferences) {
        super(graph, null);
        mGraph = graph;
        mClassesToWrite = classesToWrite;
        mUnresolvedReferences = unresolvedReferences;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        T klass = mGraph.getClassReference(name);
        mClassName = name;

        checkSuperclass(klass, superName);
        checkInterfaces(klass, interfaces);
        checkModifiers(klass, access);

        mMethods = mGraph.getMethods(klass);
        mFields = mGraph.getFields(klass);
        mAnnotations = Sets.newHashSet(mGraph.getAnnotations(klass));
        mClassesToWrite.add(klass);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    private void checkModifiers(T klass, int modifiers) {
        int oldModifiers = mGraph.getClassModifiers(klass);
        if (oldModifiers != modifiers) {
            throw new IncrementalShrinker.IncrementalRunImpossibleException(
                    String.format(
                            "%s modifiers changed.",
                            mClassName));
        }
    }

    private void checkInterfaces(T klass, String[] interfaceNames) {
        try {
            Set<String> oldNames = ImmutableSet.copyOf(interfaceNames);
            T[] interfaces = mGraph.getInterfaces(klass);
            Set<String> newNames = Sets.newHashSet();
            for (T iface : interfaces) {
                newNames.add(mGraph.getClassName(iface));
            }

            if (!oldNames.equals(newNames)) {
                throw new IncrementalShrinker.IncrementalRunImpossibleException(
                        String.format(
                                "%s interfaces changed.",
                                mClassName));
            }
        } catch (ClassLookupException e) {
            throw new IncrementalShrinker.IncrementalRunImpossibleException(
                    String.format("Can't find info for class %s.", mClassName));
        }
    }

    private void checkSuperclass(T klass, String superName) {
        try {
            T superclass = mGraph.getSuperclass(klass);
            Verify.verifyNotNull(superclass);
            if (!mGraph.getClassName(superclass).equals(superName)) {
                throw new IncrementalShrinker.IncrementalRunImpossibleException(
                        String.format(
                                "%s superclass changed.",
                                mClassName));
            }
        } catch (ClassLookupException e) {
            throw new IncrementalShrinker.IncrementalRunImpossibleException(
                    String.format("Can't find info for class %s.", mClassName));
        }
    }

    @Override
    public FieldVisitor visitField(int access, final String name, String desc, String signature,
            Object value) {
        T field = mGraph.getMemberReference(mClassName, name, desc);
        if (!mFields.remove(field)) {
            throw new IncrementalShrinker.IncrementalRunImpossibleException(
                    String.format(
                            "Field %s.%s:%s added.",
                            mClassName,
                            name,
                            desc));
        }

        if (mGraph.getMemberModifiers(field) != access) {
            throw new IncrementalShrinker.IncrementalRunImpossibleException(
                    String.format(
                            "Field %s.%s:%s modifiers changed.",
                            mClassName,
                            name,
                            desc));
        }

        final Set<String> memberAnnotations = Sets.newHashSet(mGraph.getAnnotations(field));
        FieldVisitor superVisitor = super.visitField(access, name, desc, signature, value);
        return new FieldVisitor(Opcodes.ASM5, superVisitor) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                checkForAddedAnnotation(desc, memberAnnotations, mClassName + "." + name);
                return super.visitAnnotation(desc, visible);
            }

            @Override
            public void visitEnd() {
                checkForRemovedAnnotation(memberAnnotations, mClassName + "." + name);
                super.visitEnd();
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, final String name, String desc, String signature,
            String[] exceptions) {
        final T method = mGraph.getMemberReference(mClassName, name, desc);

        if (!mMethods.remove(method)) {
            throw new IncrementalShrinker.IncrementalRunImpossibleException(
                    String.format(
                            "Method %s.%s:%s added.",
                            mClassName,
                            name,
                            desc));
        }

        if (mGraph.getMemberModifiers(method) != access) {
            throw new IncrementalShrinker.IncrementalRunImpossibleException(
                    String.format(
                            "Method %s.%s:%s modifiers changed.",
                            mClassName,
                            name,
                            desc));
        }

        final Set<String> memberAnnotations = Sets.newHashSet(mGraph.getAnnotations(method));
        MethodVisitor superVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        return new MethodVisitor(Opcodes.ASM5, superVisitor) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                checkForAddedAnnotation(desc, memberAnnotations, mClassName + "." + name);
                return super.visitAnnotation(desc, visible);
            }

            @Override
            public void visitEnd() {
                checkForRemovedAnnotation(memberAnnotations, mClassName + "." + name);
                super.visitEnd();
            }
        };
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        checkForAddedAnnotation(desc, mAnnotations, mClassName);
        return super.visitAnnotation(desc, visible);
    }

    @Override
    protected void handleDependency(T source, T target, DependencyType type) {
        if (type == DependencyType.REQUIRED_CODE_REFERENCE
                || type == DependencyType.REQUIRED_CODE_REFERENCE_REFLECTION) {
            mGraph.addDependency(source, target, type);
        }
    }

    @Override
    protected void handleMultipleInheritance(T klass) {}

    @Override
    protected void handleVirtualMethod(T method) {}

    @Override
    protected void handleInterfaceInheritance(T klass) {}

    @Override
    protected void handleUnresolvedReference(PostProcessingData.UnresolvedReference<T> reference) {
        mUnresolvedReferences.add(reference);
    }

    @Override
    public void visitEnd() {
        T field = Iterables.getFirst(mFields, null);
        if (field != null) {
            throw new IncrementalShrinker.IncrementalRunImpossibleException(
                    String.format(
                            "Field %s.%s:%s removed.",
                            mClassName,
                            mGraph.getFieldName(field),
                            mGraph.getFieldDesc(field)));
        }

        for (T method : mMethods) {
            if (mGraph.getMemberName(method).contains(FullRunShrinker.SHRINKER_FAKE_MARKER)) {
                continue;
            }
            throw new IncrementalShrinker.IncrementalRunImpossibleException(
                    String.format(
                            "Method %s.%s removed.",
                            mClassName,
                            mGraph.getMethodNameAndDesc(method)));
        }

        checkForRemovedAnnotation(mAnnotations, mClassName);
    }

    private static void checkForAddedAnnotation(String desc, Set<String> annotations, String target) {
        String name = Type.getType(desc).getInternalName();
        if (!annotations.remove(name)) {
            throw new IncrementalShrinker.IncrementalRunImpossibleException(
                    String.format(
                            "Annotation %s on %s added.",
                            name,
                            target));
        }
    }

    private static void checkForRemovedAnnotation(Set<String> annotations, String target) {
        String annotation = Iterables.getFirst(annotations, null);
        if (annotation != null) {
            throw new IncrementalShrinker.IncrementalRunImpossibleException(
                    String.format(
                            "Annotation %s on %s removed.",
                            annotation,
                            target));
        }
    }
}
