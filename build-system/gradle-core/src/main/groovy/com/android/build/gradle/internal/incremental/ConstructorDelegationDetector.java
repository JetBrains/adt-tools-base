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

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Value;

/**
 * Utilities to detect and manipulate constructor methods.
 *
 * When instrumenting a constructor the original "super" or "this" call, cannot be
 * removed or manipulated in anyway. The same goes for the this object before the super
 * call: It cannot be send to any other method as the verifier sees it as
 * UNINITIALIZED_THIS and won't allow bytecode that "escapes it"
 *
 * A constructor of a non static inner class usually has the form:
 *
 * ALOAD_0              // push this to the stack
 * ...                  // Code to set up $this
 * ALOAD_0              // push this to the stack
 * ...                  // Code to set up the arguments for the delegation via super() or this()
 * ...                  // Note that here we can have INVOKESPECIALS for all the new calls here.
 * INVOKESPECIAL <init> // super() or this() call
 * ...                  // the "body" of the constructor goes here.
 *
 * When instrumenting with incremental support we only allow "swapping" the "body".
 *
 * This class has the utilities to detect which instruction is the right INVOKESPECIAL call before
 * the "body".
 */
public class ConstructorDelegationDetector {

    /**
     * A specialized value used to track the first local variable (this) on the
     * constructor.
     */
    public static class LocalValue extends BasicValue {
        public LocalValue(Type type) {
            super(type);
        }

        @Override
        public String toString() {
            return "*";
        }
    }

    /**
     * Returns the instruction node that represents the super() or this() call in a constructor.
     *
     * @param owner the owning class.
     * @param method the constructor method.
     */
    public static AbstractInsnNode detect(@NonNull String owner, @NonNull MethodNode method) {

        // Basic interpreter uses BasicValue.REFERENCE_VALUE for all object types. However
        // we need to distinguish one in particular. The value of the local variable 0, ie. the
        // uninitialized this. By doing it this way we ensure that whenever there is a ALOAD_0
        // a LocalValue instance will be on the stack.
        BasicInterpreter interpreter = new BasicInterpreter() {
            boolean done = false;
            @Override
            // newValue is called first to initialize the frame values of all the local variables
            // we intercept the first one to create our own special value.
            public BasicValue newValue(Type type) {
                if (type == null) {
                    return BasicValue.UNINITIALIZED_VALUE;
                } else if (type.getSort() == Type.VOID) {
                    return null;
                } else {
                    // If this is the first value created (i.e. the first local variable)
                    // we use a special marker.
                    BasicValue ret = done ? super.newValue(type) : new LocalValue(type);
                    done = true;
                    return ret;
                }
            }
        };

        Analyzer analyzer = new Analyzer(interpreter);
        AbstractInsnNode[] instructions = method.instructions.toArray();
        try {
            Frame[] frames = analyzer.analyze(owner, method);
            if (frames.length != instructions.length) {
                // Should never happen.
                throw new IllegalStateException(
                        "The number of frames is not equals to the number of instructions");
            }

            for (int i = 0; i < instructions.length; i++) {
                AbstractInsnNode insn = instructions[i];
                Frame frame = frames[i];
                if (insn instanceof MethodInsnNode) {
                    // TODO: Do we need to check that the stack is empty after this super call?
                    MethodInsnNode methodhInsn = (MethodInsnNode) insn;
                    Type[] types = Type.getArgumentTypes(methodhInsn.desc);
                    Value value = frame.getStack(frame.getStackSize() - types.length - 1);
                    if (value instanceof LocalValue && methodhInsn.name.equals("<init>")) {
                        return insn;
                    }
                }
            }
        } catch (AnalyzerException e) {
            // Ignore and return null.
        }

        return null;
    }

    /**
     * Returns a new method that contains only the "body" (see above) of the constructor.
     *
     * This is used to generate the patch for the constructor that only replaces "body" part.
     * All that is removed should still exist in the original implementation.
     */
    @NonNull
    public static MethodNode getConstructorBody(@NonNull String owner, @NonNull MethodNode method) {
        method = cloneMethod(method);
        AbstractInsnNode insn = detect(owner, method);
        // TODO: Add validation that there are no labels after insn being used. Exceptions?
        if (insn != null) {
            InsnList instructions = method.instructions;
            while (instructions.getFirst() != insn) {
                instructions.remove(instructions.getFirst());
            }
            instructions.remove(insn);
        }

        return method;
    }

    /**
     * Adds a new label to mark the redirection point. This label will be just before the "body"
     * of the constructor begins.
     */
    @Nullable
    public static Label addRedirectionPoint(@NonNull String owner, @NonNull MethodNode method) {
        AbstractInsnNode insn = detect(owner, method);
        if (insn == null) return null;

        Label label = new Label();
        method.instructions.insert(insn, new LabelNode(label));
        return label;
    }

    @NonNull
    private static MethodNode cloneMethod(@NonNull MethodNode method) {
        ClassNode clazz = new ClassNode();
        method.accept(clazz);
        return (MethodNode) clazz.methods.get(0);
    }
}