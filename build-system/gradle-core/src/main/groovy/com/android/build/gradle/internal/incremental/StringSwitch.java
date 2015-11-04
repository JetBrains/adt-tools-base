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

import com.android.utils.AsmUtils;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.RuntimeException;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Class for generating an efficient string switch in ByteCode.
 *
 * Current implementation looks like:
 *
 * switch(s.hashCode()) {
 *   case 192: visitCase(s);
 *   case 312: visitCase(s);
 *   case 1024:
 *     if (s.equals("collided_method1")) {
 *         visit(s);
 *     } else if (s.equals("collided_method2")) {
 *         visit(s);
 *     }
 *     visitDefault();
 *   default:
 *     visitDefault();
 * }
 *
 * In the most common case of no hash collisions, only the hashCode level if switching is needed.
 */
abstract class StringSwitch {
    private static final String PACKAGE =
            IncrementalVisitor.class.getPackage().getName().replace('.', '/');

    // Set this to a small positive number to force has hashcode collisions to exercise the
    // length and character checks.
    private static final Integer FORCE_HASH_COLLISION_MODULUS = 3;

    // Figure out some types and methods ahead of time.
    private static final Type OBJECT_TYPE = Type.getType(java.lang.Object.class);
    private static final Type STRING_TYPE = Type.getType(java.lang.String.class);
    private static final Type INSTANT_RELOAD_EXCEPTION_TYPE = Type.getType(PACKAGE + "/InstantReloadException");

    // Methods overridden by caller to implement the switch behavior
    abstract void visitString();
    abstract void visitCase(String string);
    abstract void visitDefault();

    /**
     * Emit code for a string if-else block.
     *
     *     if (s.equals("collided_method1")) {
     *         visit(s);
     *     } else if (s.equals("collided_method2")) {
     *         visit(s);
     *     }
     *
     * In the most common case of just one string, this degenerates to:
     *
     *   visit(s)
     *
     */
    private void visit(GeneratorAdapter mv, List<String> strings) {
        if (strings.size() == 1) {
            visitCase(strings.get(0));
            return;
        }

        Label labels[] = new Label[strings.size()];
        for (int i = 0; i < labels.length; ++i) {
            labels[i] = new Label();
        }

        for (int i = 0; i < labels.length; ++i) {
            visitString();
            mv.visitLdcInsn(strings.get(i));
            mv.invokeVirtual(STRING_TYPE, Method.getMethod("boolean equals(Object)"));
            mv.visitJumpInsn(Opcodes.IFEQ, labels[i]);
            visitCase(strings.get(i));
            mv.visitLabel(labels[i]);
        }

        visitDefault();
    }

    /**
     * Emit code for a string switch for the given string classifier.
     * switch(s.hashCode()) {
     *   case 192: visitCase(s);
     *   case 312: visitCase(s);
     *   case 1024:
     *     if (s.equals("collided_method1")) {
     *         visit(s);
     *     } else if (s.equals("collided_method2")) {
     *         visit(s);
     *     }
     *     visitDefault();
     *   default:
     *     visitDefault();
     * }
     **/
    private void visit(GeneratorAdapter mv, StringClassifier classifier) {
        visitString();
        mv.invokeVirtual(STRING_TYPE, Method.getMethod("int hashCode()"));
        if (FORCE_HASH_COLLISION_MODULUS != null) {
            mv.push(FORCE_HASH_COLLISION_MODULUS);
            mv.visitInsn(Opcodes.IREM); // Modulus operator
        }

        // Label for each hash and for default.
        Label labels[] = new Label[classifier.sortedHashes.length];
        Label defaultLabel = new Label();
        for (int i = 0; i < labels.length; ++i) {
            labels[i] = new Label();
        }

        // Create a switch that dispatches to each label from the hash code of
        mv.visitLookupSwitchInsn(defaultLabel, classifier.sortedHashes, labels);

        // Create the cases.
        for (int i = 0; i < classifier.sortedHashes.length; ++i) {
            mv.visitLabel(labels[i]);
            visit(mv, classifier.sortedCases[i]);
        }
        mv.visitLabel(defaultLabel);
        visitDefault();
    }

    /**
     * Generates a standard error exception with message similar to:
     *
     *    String switch could not find 'equals.(Ljava/lang/Object;)Z' with hashcode 0
     *    in com/example/basic/GrandChild
     *
     * @param mv The generator adaptor used to emit the lookup switch code.
     * @param visitedClassName The abstract string trie structure.
     */
    void writeMissingMessageWithHash(GeneratorAdapter mv, String visitedClassName) {
        mv.newInstance(INSTANT_RELOAD_EXCEPTION_TYPE);
        mv.dup();
        mv.push("String switch could not find '%s' with hashcode %s in %s");
        mv.push(3);
        mv.newArray(OBJECT_TYPE);
        mv.dup();
        mv.push(0);
        visitString();
        mv.arrayStore(OBJECT_TYPE);
        mv.dup();
        mv.push(1);
        visitString();
        mv.invokeVirtual(STRING_TYPE, Method.getMethod("int hashCode()"));
        if (FORCE_HASH_COLLISION_MODULUS != null) {
            mv.push(FORCE_HASH_COLLISION_MODULUS);
            mv.visitInsn(Opcodes.IREM);
        }
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/Integer",
                "valueOf",
                "(I)Ljava/lang/Integer;", false);
        mv.arrayStore(OBJECT_TYPE);
        mv.dup();
        mv.push(2);
        mv.push(visitedClassName);
        mv.arrayStore(OBJECT_TYPE);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/String",
                "format",
                "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false);
        mv.invokeConstructor(INSTANT_RELOAD_EXCEPTION_TYPE,
                Method.getMethod("void <init> (String)"));
        mv.throwException();
    }

    /**
     * Main entry point for creation of string switch
     *
     * @param mv The generator adaptor used to emit the lookup switch code.
     * @param strings The closed set of strings to generate a switch for.
     */
    void visit(GeneratorAdapter mv, Set<String> strings) {
        visit(mv, StringClassifier.of(strings, FORCE_HASH_COLLISION_MODULUS));
    }
}
