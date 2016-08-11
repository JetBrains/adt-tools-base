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

import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.COMPATIBLE;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.FIELD_ADDED;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.FIELD_REMOVED;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.FIELD_TYPE_CHANGE;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.IMPLEMENTED_INTERFACES_CHANGE;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.INSTANT_RUN_DISABLED;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.METHOD_ADDED;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.METHOD_ANNOTATION_CHANGE;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.METHOD_DELETED;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.PARENT_CLASS_CHANGED;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.REFLECTION_USED;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.R_CLASS_CHANGE;
import static com.android.build.gradle.internal.incremental.InstantRunVerifierStatus.STATIC_INITIALIZER_CHANGE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Instant Run Verifier responsible for checking that a class change (between two developers
 * iteration) can be safely hot swapped on the device or not.
 *
 * ThreadSafe
 */
public class InstantRunVerifier {

    private static final Comparator<MethodNode> METHOD_COMPARATOR = new MethodNodeComparator();

    @VisibleForTesting
    static final Comparator<AnnotationNode> ANNOTATION_COMPARATOR =
            new AnnotationNodeComparator();
    private static final Comparator<Object> OBJECT_COMPARATOR = Objects::equal;
    private static final Comparator<String> STRING_COMPARATOR = Objects::equal;

    private static final Comparator<Object> OBJECT_OR_ANNOTATION_NODE_COMPARATOR =
            (first, second) -> {
                if (first instanceof AnnotationNode && second instanceof AnnotationNode) {
                    return ANNOTATION_COMPARATOR
                            .areEqual((AnnotationNode) first, (AnnotationNode) second);
                }
                return OBJECT_COMPARATOR.areEqual(first, second);
    };

    public interface ClassBytesProvider {
        byte[] load() throws IOException;
    }

    public static class ClassBytesFileProvider implements ClassBytesProvider {

        private final File file;
        public ClassBytesFileProvider(File file) {
            this.file = file;
        }

        @Override
        public byte[] load() throws IOException {
            return Files.toByteArray(file);
        }

        @VisibleForTesting
        public File getFile() {
            return file;
        }
    }

    public static class ClassBytesJarEntryProvider implements ClassBytesProvider {

        private final JarFile jarFile;
        private final JarEntry jarEntry;

        public ClassBytesJarEntryProvider(JarFile jarFile, JarEntry jarEntry) {
            this.jarFile = jarFile;
            this.jarEntry = jarEntry;
        }

        @Override
        public byte[] load() throws IOException {
            InputStream is = jarFile.getInputStream(jarEntry);
            try {
                ByteStreams.toByteArray(is);
            } finally {
                Closeables.close(is, false /* swallowIOException */);
            }

            return new byte[0];
        }
    }

    /**
     * describe the difference between two collections of the same elements.
     */
    @VisibleForTesting
    enum Diff {
        /**
         * no change, the collections are equals
         */
        NONE,
        /**
         * an element was added to the first collection.
         */
        ADDITION,
        /**
         * an element was removed from the first collection.
         */
        REMOVAL,
        /**
         * an element was changed.
         */
        CHANGE
    }

    private InstantRunVerifier() {
    }


    public static InstantRunVerifierStatus run(File original, File updated) throws IOException {
        return run(new ClassBytesFileProvider(original), new ClassBytesFileProvider(updated));
    }

    // ASM API not generified.
    @SuppressWarnings("unchecked")
    @NonNull
    public static InstantRunVerifierStatus run(ClassBytesProvider original, ClassBytesProvider updated)
            throws IOException {

        ClassNode originalClass = loadClass(original);
        ClassNode updatedClass = loadClass(updated);

        if (!originalClass.superName.equals(updatedClass.superName)) {
            return PARENT_CLASS_CHANGED;
        }

        if (diffList(originalClass.interfaces, updatedClass.interfaces,
                STRING_COMPARATOR) != Diff.NONE) {
            return IMPLEMENTED_INTERFACES_CHANGE;
        }

        if (diffList(originalClass.visibleAnnotations, updatedClass.visibleAnnotations,
                ANNOTATION_COMPARATOR) != Diff.NONE) {
            return CLASS_ANNOTATION_CHANGE;
        }

        // check if the class is InstantRunDisabled.
        List<AnnotationNode> invisibleAnnotations = originalClass.invisibleAnnotations;
        if (invisibleAnnotations!=null) {
            for (AnnotationNode annotationNode : invisibleAnnotations) {

                if (annotationNode.desc.equals(
                        IncrementalVisitor.DISABLE_ANNOTATION_TYPE.getDescriptor())) {
                    // potentially, we could try to see if anything has really changed between
                    // the two classes but the fact that we got an updated class means so far that
                    // we have a new version and should restart.
                    return INSTANT_RUN_DISABLED;
                }
            }
        }

        InstantRunVerifierStatus fieldChange = verifyFields(originalClass, updatedClass);
        if (fieldChange != COMPATIBLE) {
            return fieldChange;
        }

        return verifyMethods(originalClass, updatedClass);
    }

    @NonNull
    private static InstantRunVerifierStatus verifyFields(
            @NonNull ClassNode originalClass,
            @NonNull ClassNode updatedClass) {

        //noinspection unchecked
        Diff diff = diffList(originalClass.fields, updatedClass.fields, new Comparator<FieldNode>()
        {

            @Override
            public boolean areEqual(@Nullable FieldNode first, @Nullable FieldNode second) {
                if ((first == null) && (second == null)) {
                    return true;
                }
                if (first == null || second == null) {
                    return true;
                }
                return first.name.equals(second.name)
                        && first.desc.equals(second.desc)
                        && first.access == second.access
                        && Objects.equal(first.value, second.value);
            }
        });

        if (diff != Diff.NONE) {
            // Detect R$something classes, and report changes in them separately.
            String name = originalClass.name;
            int index = name.lastIndexOf('/');
            if (index != -1 &&
                    name.startsWith("R$", index + 1) &&
                    (originalClass.access & Opcodes.ACC_PUBLIC) != 0 &&
                    (originalClass.access & Opcodes.ACC_FINAL) != 0 &&
                    originalClass.outerClass == null &&
                    originalClass.interfaces.isEmpty() &&
                    originalClass.superName.equals("java/lang/Object") &&
                    name.length() > 3 && Character.isLowerCase(name.charAt(2))) {
                return R_CLASS_CHANGE;
            }
        }

        switch (diff) {
            case NONE:
                return COMPATIBLE;
            case ADDITION:
                return FIELD_ADDED;
            case REMOVAL:
                return FIELD_REMOVED;
            case CHANGE:
                return FIELD_TYPE_CHANGE;
            default:
                throw new RuntimeException("Unhandled action : " + diff);
        }
    }

    @NonNull
    private static InstantRunVerifierStatus verifyMethods(
            @NonNull ClassNode originalClass, @NonNull ClassNode updatedClass) {

        @SuppressWarnings("unchecked") // ASM API.
        List<MethodNode> nonVisitedMethodsOnUpdatedClass = new ArrayList<>(updatedClass.methods);

        //noinspection unchecked
        for(MethodNode methodNode : (List<MethodNode>) originalClass.methods) {

            MethodNode updatedMethod = findMethod(updatedClass, methodNode.name, methodNode.desc);
            if (updatedMethod == null) {
                // although it's probably ok if a method got deleted since nobody should be calling
                // it anymore BUT the application might be using reflection to get the list of
                // methods and would still see the deleted methods. To be prudent, restart.
                // However, if the class initializer got removed, it's always fine.
                return methodNode.name.equals(ByteCodeUtils.CLASS_INITIALIZER)
                        ? COMPATIBLE
                        : METHOD_DELETED;
            }

            // remove the method from the visited ones on the updated class.
            nonVisitedMethodsOnUpdatedClass.remove(updatedMethod);

            InstantRunVerifierStatus change = methodNode.name.equals(ByteCodeUtils.CLASS_INITIALIZER)
                    ? visitClassInitializer(methodNode, updatedMethod)
                    : verifyMethod(methodNode, updatedMethod);

            if (change != COMPATIBLE) {
                return change;
            }
        }

        if (!nonVisitedMethodsOnUpdatedClass.isEmpty()) {
            return METHOD_ADDED;
        }
        return COMPATIBLE;
    }

    @NonNull
    private static InstantRunVerifierStatus visitClassInitializer(MethodNode originalClassInitializer,
            MethodNode updateClassInitializer) {

        return METHOD_COMPARATOR.areEqual(originalClassInitializer, updateClassInitializer)
                ? COMPATIBLE
                : STATIC_INITIALIZER_CHANGE;
    }

    @SuppressWarnings("unchecked") // ASM API
    @NonNull
    private static InstantRunVerifierStatus verifyMethod(
            MethodNode methodNode,
            MethodNode updatedMethod) {

        // check for annotations changes
        if (diffList(methodNode.visibleAnnotations, updatedMethod.visibleAnnotations,
                new AnnotationNodeComparator()) != Diff.NONE) {
            return METHOD_ANNOTATION_CHANGE;
        }

        // the method exist in both classes, check if the original method was disabled for
        // instantRun or contained calls to blacklisted APIs. If either of these conditions
        // is true, and the method implementation has changed, a restart is needed.
        boolean disabledMethod = false;
        List<AnnotationNode> invisibleAnnotations = methodNode.invisibleAnnotations;
        if (invisibleAnnotations != null) {
            for (AnnotationNode originalMethodAnnotation : invisibleAnnotations) {
                if (originalMethodAnnotation.desc.equals(
                        IncrementalVisitor.DISABLE_ANNOTATION_TYPE.getDescriptor())) {
                    disabledMethod = true;
                }
            }
        }

        boolean usingBlackListedAPIs =
                InstantRunMethodVerifier.verifyMethod(updatedMethod) != COMPATIBLE;

        // either disabled or using blacklisted APIs, let it through only if the method
        // implementation is unchanged.
        if ((disabledMethod || usingBlackListedAPIs) &&
                !METHOD_COMPARATOR.areEqual(methodNode, updatedMethod)) {

            return disabledMethod
                    ? INSTANT_RUN_DISABLED
                    : REFLECTION_USED;

        }
        return COMPATIBLE;
    }

    @Nullable
    private static MethodNode findMethod(@NonNull ClassNode classNode,
            @NonNull  String name,
            @Nullable String desc) {

        //noinspection unchecked
        for (MethodNode methodNode : (List<MethodNode>) classNode.methods) {

            if (methodNode.name.equals(name) &&
                    ((desc == null && methodNode.desc == null) || (methodNode.desc.equals(desc)))) {
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
            if (first==null && second==null) {
                return true;
            }
            if (first==null || second==null) {
                return false;
            }
            if (!first.name.equals(second.name) || !first.desc.equals(second.desc)) {
                return false;
            }
            VerifierTextifier firstMethodTextifier = new VerifierTextifier();
            VerifierTextifier secondMethodTextifier = new VerifierTextifier();
            first.accept(new TraceMethodVisitor(firstMethodTextifier));
            second.accept(new TraceMethodVisitor(secondMethodTextifier));

            StringWriter firstText = new StringWriter();
            StringWriter secondText = new StringWriter();
            firstMethodTextifier.print(new PrintWriter(firstText));
            secondMethodTextifier.print(new PrintWriter(secondText));

            return firstText.toString().equals(secondText.toString());
        }
    }

    /**
     * Subclass of {@link Textifier} that will pretty print method bytecodes but will swallow the
     * line numbers notification as it is not pertinent for the InstantRun hot swapping.
     */
    private static class VerifierTextifier extends Textifier {

        protected VerifierTextifier() {
            super(Opcodes.ASM5);
        }

        @Override
        public void visitLineNumber(int i, Label label) {
            // don't care about line numbers
        }
    }

    public static class AnnotationNodeComparator implements Comparator<AnnotationNode> {

        @Override
        public boolean areEqual(@Nullable AnnotationNode first, @Nullable  AnnotationNode second) {
            // probably deep compare for values...
            //noinspection unchecked
            if (first == null || second == null) {
                return first == second;
            }

            if (!STRING_COMPARATOR.areEqual(first.desc, second.desc)) {
                return false;
            }

            List firstEntries = splitToEntries(first.values);
            List secondEntries = splitToEntries(second.values);

            return diffList(firstEntries, secondEntries, OBJECT_COMPARATOR) == Diff.NONE;
        }
    }

    @VisibleForTesting
    @NonNull
    static <T> Diff diffList(
            @Nullable List<T> one,
            @Nullable List<T> two,
            @NonNull Comparator<T> comparator) {

        if (one == null && two == null) {
            return Diff.NONE;
        }
        if (one == null) {
            return Diff.ADDITION;
        }
        if (two == null) {
            return Diff.REMOVAL;
        }
        List<T> copyOfOne = new ArrayList<T>(one);
        List<T> copyOfTwo = new ArrayList<T>(two);

        for (T elementOfTwo : two) {
            T commonElement = getElementOf(copyOfOne, elementOfTwo, comparator);
            if (commonElement != null) {
                copyOfOne.remove(commonElement);
            }
        }

        for (T elementOfOne : one) {
            T commonElement = getElementOf(copyOfTwo, elementOfOne, comparator);
            if (commonElement != null) {
                copyOfTwo.remove(commonElement);
            }
        }
        if ((!copyOfOne.isEmpty()) && (copyOfOne.size() == copyOfTwo.size())) {
            return Diff.CHANGE;
        }
        if (!copyOfOne.isEmpty()) {
            return Diff.REMOVAL;
        }
        return copyOfTwo.isEmpty() ? Diff.NONE : Diff.ADDITION;
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

    static ClassNode loadClass(ClassBytesProvider classFile) throws IOException {
        byte[] classBytes = classFile.load();
        ClassReader classReader = new ClassReader(classBytes);

        org.objectweb.asm.tree.ClassNode classNode = new org.objectweb.asm.tree.ClassNode();
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
        return classNode;
    }

    static class AnnotationEntryAndValue  {

        private final String name;

        private final Object value;

        AnnotationEntryAndValue(@NonNull String name, @Nullable Object value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof  AnnotationEntryAndValue)) {
                return false;
            }

            AnnotationEntryAndValue other = (AnnotationEntryAndValue) obj;
            if (!STRING_COMPARATOR.areEqual(name, other.name)) {
                return false;
            }

            //Asm incorrectly populates data into AnnotationNode so all array types processing is required:
            //http://forge.ow2.org/tracker/?func=detail&aid=317626&group_id=23&atid=100023
            //https://code.google.com/p/android/issues/detail?id=209051
            Object otherValue = other.value;
            if (value instanceof byte[] && otherValue instanceof byte[]) {
                return Arrays.equals((byte[]) value, (byte[]) otherValue);
            } else if (value instanceof boolean[] && otherValue instanceof boolean[]) {
                return Arrays.equals((boolean[]) value,  (boolean[]) otherValue);
            } else if (value instanceof short[] && otherValue instanceof short[]) {
                return Arrays.equals((short[]) value, (short[]) otherValue);
            } else if (value instanceof char[] && otherValue instanceof char[]) {
                return Arrays.equals((char[]) value, (char[]) otherValue);
            } else if (value instanceof int[] && otherValue instanceof int[]) {
                return Arrays.equals((int[]) value, (int[]) otherValue);
            } else if (value instanceof long[] && otherValue instanceof long[]) {
                return Arrays.equals((long[]) value, (long[]) otherValue);
            } else if (value instanceof float[] && otherValue instanceof float[]) {
                return Arrays.equals((float[]) value, (float[]) otherValue);
            } else if (value instanceof double[] && otherValue instanceof double[]) {
                return Arrays.equals((double[]) value, (double[]) otherValue);
            } else if (value instanceof String[] && otherValue instanceof String[]) {
                //Enum entry values are stored in String []
                //https://code.google.com/p/android/issues/detail?id=209047
                return Arrays.equals((String[]) value, (String[]) otherValue);
            }

            if (value instanceof List && otherValue instanceof List) {
                //properly compare arrays of annotations (OBJECT_OR_ANNOTATION_NODE_COMPARATOR)
                List list = (List) value;
                List otherList = (List) otherValue;
                if (list.size() != otherList.size()) {
                    return false;
                }

                Iterator iterator = list.iterator();
                Iterator otherIterator = otherList.iterator();
                while (iterator.hasNext() && otherIterator.hasNext()) {
                    if (!OBJECT_OR_ANNOTATION_NODE_COMPARATOR.areEqual(iterator.next(), otherIterator.next())) {
                        return false;
                    }
                }
                return true;
            }

            return OBJECT_OR_ANNOTATION_NODE_COMPARATOR.areEqual(value, otherValue);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    @NonNull
    private static List<AnnotationEntryAndValue> splitToEntries(@Nullable List values) {
        if (values == null) return Collections.emptyList();
        List<AnnotationEntryAndValue> result = new ArrayList<AnnotationEntryAndValue>();
        for (int i = 0; i < values.size(); i += 2) {
            String name = (String) values.get(i);
            Object value = values.get(i + 1);
            result.add(new AnnotationEntryAndValue(name, value));
        }
        return result;
    }
}
