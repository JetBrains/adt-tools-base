/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Optional;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Bytecode generation utilities to work around some ASM / Dex issues.
 */
public class ByteCodeUtils {

    public static final String CONSTRUCTOR = "<init>";
    public static final String CLASS_INITIALIZER = "<clinit>";
    private static final Type NUMBER_TYPE = Type.getObjectType("java/lang/Number");
    private static final Method SHORT_VALUE = Method.getMethod("short shortValue()");
    private static final Method BYTE_VALUE = Method.getMethod("byte byteValue()");

    /**
     * Generates unboxing bytecode for the passed type. An {@link Object} is expected to be on the
     * stack when these bytecodes are inserted.
     *
     * ASM takes a short cut when dealing with short/byte types and convert them into int rather
     * than short/byte types. This is not an issue on the jvm nor Android's ART but it is an issue
     * on Dalvik.
     *
     * @param mv the {@link GeneratorAdapter} generating a method implementation.
     * @param type the expected un-boxed type.
     */
    public static void unbox(GeneratorAdapter mv, Type type) {
        if (type.equals(Type.SHORT_TYPE)) {
            mv.checkCast(NUMBER_TYPE);
            mv.invokeVirtual(NUMBER_TYPE, SHORT_VALUE);
        } else if (type.equals(Type.BYTE_TYPE)) {
            mv.checkCast(NUMBER_TYPE);
            mv.invokeVirtual(NUMBER_TYPE, BYTE_VALUE);
        } else {
            mv.unbox(type);
        }
    }

    /**
     * Converts the given method to a String.
     */
    public static String textify(@NonNull MethodNode method) {
        Textifier textifier = new Textifier();
        TraceMethodVisitor trace = new TraceMethodVisitor(textifier);
        method.accept(trace);
        String ret = "";
        for (Object line : textifier.getText()) {
            ret += line;
        }
        return ret;
    }

    /**
     * Pushes an array on the stack that contains the value of all the given variables.
     */
    static void newVariableArray(
            @NonNull GeneratorAdapter mv,
            @NonNull List<LocalVariable> variables) {
        mv.push(variables.size());
        mv.newArray(Type.getType(Object.class));
        loadVariableArray(mv, variables, 0);
    }

    /**
     * Given an array on the stack, it loads it with the values of the given variables stating at
     * offset.
     */
    static void loadVariableArray(
            @NonNull GeneratorAdapter mv,
            @NonNull List<LocalVariable> variables, int offset) {
        // we need to maintain the stack index when loading parameters from, as for long and double
        // values, it uses 2 stack elements, all others use only 1 stack element.
        for (int i = offset; i < variables.size(); i++) {
            LocalVariable variable = variables.get(i);
            // duplicate the array of objects reference, it will be used to store the value in.
            mv.dup();
            // index in the array of objects to store the boxed parameter.
            mv.push(i);
            // Pushes the appropriate local variable on the stack
            mv.visitVarInsn(variable.type.getOpcode(Opcodes.ILOAD), variable.var);
            // potentially box up intrinsic types.
            mv.box(variable.type);
            // store it in the array
            mv.arrayStore(Type.getType(Object.class));
        }
    }

    /**
     * Given an array with values at the top of the stack, the values are unboxed and stored
     * on the given variables. The array is popped from the stack.
     */
    static void restoreVariables(
            @NonNull GeneratorAdapter mv,
            @NonNull List<LocalVariable> variables) {
        for (int i = 0; i < variables.size(); i++) {
            LocalVariable variable = variables.get(i);
            // Duplicates the array on the stack;
            mv.dup();
            // Sets up the index
            mv.push(i);
            // Gets the Object value
            mv.arrayLoad(Type.getType(Object.class));
            // Unboxes to the type of the local variable
            mv.unbox(variable.type);
            // Restores the local variable
            mv.visitVarInsn(variable.type.getOpcode(Opcodes.ISTORE), variable.var);
        }
        // Pops the array from the stack.
        mv.pop();
    }

    /**
     * Converts Types to LocalVariables, assuming they start from variable 0.
     */
    static List<LocalVariable> toLocalVariables(@NonNull List<Type> types) {
        List<LocalVariable> variables = Lists.newArrayList();
        int stack = 0;
        for (int i = 0; i < types.size(); i++) {
            Type type = types.get(i);
            variables.add(new LocalVariable(type, stack));
            stack += type.getSize();
        }
        return variables;
    }

    /**
     * Given a *STORE opcode, it returns the type associated to the variable, or null if
     * not a valid opcode.
     */
    static Type getTypeForStoreOpcode(int opcode) {
        switch (opcode) {
            case Opcodes.ISTORE:
                return Type.INT_TYPE;
            case Opcodes.LSTORE:
                return Type.LONG_TYPE;
            case Opcodes.FSTORE:
                return Type.FLOAT_TYPE;
            case Opcodes.DSTORE:
                return Type.DOUBLE_TYPE;
            case Opcodes.ASTORE:
                return Type.getType(Object.class);
        }
        return null;
    }

    /**
     * Converts a class name from the Java language naming convention (foo.bar.baz) to the JVM
     * internal naming convention (foo/bar/baz).
     */
    @NonNull
    public static String toInternalName(@NonNull String className) {
        return className.replace('.', '/');
    }

    /**
     * Gets the class name from a class member internal name, like {@code com/foo/Bar.baz:(I)V}.
     */
    @NonNull
    public static String getClassName(@NonNull String memberName) {
        Preconditions.checkArgument(memberName.contains(":"), "Class name passed as argument.");
        return memberName.substring(0, memberName.indexOf('.'));
    }

    /**
     * Returns the package name, based on the internal class name. For example, given 'com/foo/Bar'
     * return 'com.foo'.
     *
     * <p>Returns {@link Optional#empty()} for classes in the anonymous package.
     */
    @NonNull
    public static Optional<String> getPackageName(@NonNull String internalName) {
        List<String> parts = Splitter.on('/').splitToList(internalName);
        if (parts.size() == 1) {
            return Optional.empty();
        }

        return Optional.of(Joiner.on('.').join(parts.subList(0, parts.size() - 1)));
    }
}
