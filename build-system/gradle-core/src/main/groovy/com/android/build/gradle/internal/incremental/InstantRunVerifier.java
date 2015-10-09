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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;
import com.google.common.io.Files;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Instant Run Verifier responsible for checking that a class change (between two developers
 * iteration) can be safely hot swapped on the device or not.
 *
 * ThreadSafe
 */
public class InstantRunVerifier {

    private static final Comparator<MethodNode> METHOD_NODE_COMPARATOR =
            new MethodNodeComparator();
    private static final Comparator<AnnotationNode> ANNOTATION_NODE_COMPARATOR =
            new AnnotationNodeComparator();
    private static final Comparator<Object> OBJECT_COMPARATOR = new Comparator<Object>() {
        @Override
        public boolean areEqual(Object first, Object second) {
            return Objects.equal(first, second);
        }
    };


    public InstantRunVerifier() {
    }

    // ASM API not generified.
    @SuppressWarnings("unchecked")
    @Nullable
    public IncompatibleChange run(File originalClass, File updatedClass) throws IOException {

        ClassNode originalClassNode = loadClass(originalClass);
        ClassNode updatedClassNode = loadClass(updatedClass);

        if (!originalClassNode.superName.equals(updatedClassNode.superName)) {
            return IncompatibleChange.PARENT_CLASS_CHANGED;
        }

        if (!compareList(originalClassNode.interfaces, updatedClassNode.interfaces,
                OBJECT_COMPARATOR)) {
            return IncompatibleChange.IMPLEMENTED_INTERFACES_CHANGE;
        }

        // ASM API here and below.
        //noinspection unchecked
        if (!compareList(originalClassNode.visibleAnnotations,
                updatedClassNode.visibleAnnotations,
                ANNOTATION_NODE_COMPARATOR)) {
            return IncompatibleChange.CLASS_ANNOTATION_CHANGE;
        }

        List<MethodNode> nonVisitedMethodsOnUpdatedClass =
                new ArrayList<MethodNode>(updatedClassNode.methods);

        //noinspection unchecked
        for(MethodNode methodNode : (List<MethodNode>) originalClassNode.methods) {
            // although it's probably ok if a method got deleted since nobody should be calling it
            // anymore BUT the application might be using reflection to get the list of methods
            // and would still see the deleted methods. To be prudent, restart.
            MethodNode updatedMethod = findMethod(updatedClassNode, methodNode.name, methodNode.desc);
            if (updatedMethod==null) {
                return IncompatibleChange.METHOD_DELETED;
            }

            // remove the method from the visited ones on the updated class.
            nonVisitedMethodsOnUpdatedClass.remove(updatedMethod);

            IncompatibleChange change = verifyMethod(methodNode, updatedMethod);
            if (change!=null) {
                return change;
            }
        }

        if (!nonVisitedMethodsOnUpdatedClass.isEmpty()) {
            return IncompatibleChange.METHOD_ADDED;
        }
        return null;
    }

    @Nullable
    private static IncompatibleChange verifyMethod(
            MethodNode methodNode,
            MethodNode updatedMethod) {

        //noinspection unchecked
        if (!compareList(methodNode.visibleAnnotations,
                updatedMethod.visibleAnnotations,
                new AnnotationNodeComparator())) {
            return IncompatibleChange.METHOD_ANNOTATION_CHANGE;
        }
        return null;
    }

    @Nullable
    private static MethodNode findMethod(@NonNull ClassNode classNode,
            @NonNull  String name,
            @NonNull String desc) {

        //noinspection unchecked
        for (MethodNode methodNode : (List<MethodNode>) classNode.methods) {

            if (methodNode.name.equals(name) && methodNode.desc.equals(desc)) {
                return methodNode;
            }
        }
        return null;
    }

    private interface Comparator<T> {
        boolean areEqual(@Nullable T first, @Nullable T second);
    }

    private static class MethodNodeComparator implements Comparator<MethodNode> {

        @Override
        public boolean areEqual(@Nullable  MethodNode first, @Nullable MethodNode second) {
            return (first == null && second == null) || (first!=null && second!=null &&
                    first.name.equals(second.name) && first.desc.equals(second.desc));
        }
    }

    public static class AnnotationNodeComparator implements Comparator<AnnotationNode> {

        @Override
        public boolean areEqual(@Nullable AnnotationNode first, @Nullable  AnnotationNode second) {
            // probably deep compare for values...
            //noinspection unchecked
            return (first == null && second == null) || (first != null && second != null)
                && OBJECT_COMPARATOR.areEqual(first.desc, second.desc) &&
                    compareList(first.values, second.values, OBJECT_COMPARATOR);
        }
    }

    public static <T> boolean compareList(
            @Nullable List<T> one,
            @Nullable List<T> two,
            @NonNull  Comparator<T> comparator) {

        if (one == null && two == null) {
            return true;
        }
        if (one == null || two == null) {
            return false;
        }
        List<T> copyOfOne = new ArrayList<T>(one);
        for (T elementOfTwo : two) {
            T elementOfCopyOfOne = getElementOf(copyOfOne, elementOfTwo, comparator);
            if (elementOfCopyOfOne != null) {
                copyOfOne.remove(elementOfCopyOfOne);
            }
        }

        if (!copyOfOne.isEmpty()) {
            return false;
        }
        for (T elementOfOne : one) {
            T elementOfTwo = getElementOf(two, elementOfOne, comparator);
            if (elementOfTwo != null) {
                two.remove(elementOfTwo);
            }
        }
        return two.isEmpty();
    }

    @Nullable
    public static <T> T getElementOf(List<T> list, T element, Comparator<T> comparator) {
        for (T elementOfList : list) {
            if (comparator.areEqual(elementOfList, element)) {
                return elementOfList;
            }
        }
        return null;
    }

    static ClassNode loadClass(File classFile) throws IOException {
        byte[] classBytes;
        classBytes = Files.toByteArray(classFile);
        ClassReader classReader = new ClassReader(classBytes);

        org.objectweb.asm.tree.ClassNode classNode = new org.objectweb.asm.tree.ClassNode();
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
        return classNode;
    }
}
