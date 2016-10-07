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
import com.google.common.collect.Lists;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a constructor object separating all its components.
 *
 * A generic constructor has the following form:
 * <pre>{@code
 * ...                  // Code to set up this (specially for inner classes). This code
 * ...                  // Is arbitrary with the exception of sending the "this" object
 * ...                  // as a method argument is not allowed.
 * ...                  // Note that local variables can be declared here.
 * ...                  // This is considered the "prelude" section, and cannot be hot swapped.
 * ALOAD_0              // This is the last ALOAD_0 before the "super" call.
 * ...                  // Code to set up the arguments (aka "args") for the delegation
 * ...                  // via super() or this(). Note that here we can have INVOKESPECIALS
 * ...                  // for all the new calls here.
 * ...                  // Again in this section, "this" cannot escape, and local variables can
 * ...                  // be created.
 * INVOKESPECIAL <init> // super() or this() call
 * ...                  // the "body" of the constructor goes here.
 * }</pre>
 * This class has the utilities to detect which instruction is the right INVOKESPECIAL call before
 * the "body".
 */
public class ConstructorBuilder {

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
     * Deconstruct a constructor into its components and adds the necessary code to link the components
     * later. This code is not valid java, but it can be expressed in bytecode. In essence for this constructor:
     * <pre>{@code
     *   <init>(int x) {
     *     int a = 2;
     *     super(int b = 3, x = 1, expr2() ? 3 : a++)
     *     doSomething(x + a)
     *   }
     * }</pre>
     * it creates two parts:
     * <pre>{@code
     *   Object[] init$args(Clazz this, int x, Object[] locals) { // this is always null here
     *     int a = locals[0];
     *     int b = 3;
     *     Object[] args = new Object[3];
     *     args[0] = b;
     *     args[1] = (x = 1)
     *     args[2] = expr2() ? 3 : a++;
     *     locals = new Object[3]; // The arguments + the locals
     *     locals[0] = NULL;
     *     locals[1] = x;
     *     locals[2] = new Object[2];
     *     locals[2][0] = a;
     *     locals[2][1] = b;
     *     return new Object[] {locals, "myclass.<init>(I;I;)V", args};
     *   }
     *
     *   void init$body(int x, Object[] locals) {
     *     int a = locals[0];
     *     int b = locals[1];
     *     doSomething(x + a);
     *   }
     * }</pre>
     *
     * @param owner the owning class.
     * @param method the constructor method.
     */
    @NonNull
    public static Constructor build(@NonNull String owner, @NonNull MethodNode method) {
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
            int stackAtThis = -1;
            boolean poppedThis = false;
            int firstLocal = 1;
            for (Type type : Type.getArgumentTypes(method.desc)) {
                firstLocal += type.getSize();
            }

            LinkedHashSet<LocalVariable> variables = new LinkedHashSet<LocalVariable>();
            VarInsnNode lastThis = null;
            int localsAtLastThis = 0;
            // Records the most recent line number encountered. For javac, there should always be
            // a line number node before the call of interest to this(...) or super(...). For robustness,
            // -1 is recorded as a sentinel to indicate this assumption didn't hold. Upstream consumers
            // should check for -1 and recover in a reasonable way (for example, don't set the line
            // number in generated code).
            int recentLine = -1;
            for (int i = 0; i < instructions.length; i++) {
                AbstractInsnNode insn = instructions[i];
                Frame frame = frames[i];
                if (frame.getStackSize() < stackAtThis) {
                    poppedThis = true;
                }
                if (insn instanceof MethodInsnNode) {
                    // TODO: Do we need to check that the stack is empty after this super call?
                    MethodInsnNode methodhInsn = (MethodInsnNode) insn;
                    Type[] types = Type.getArgumentTypes(methodhInsn.desc);
                    Value value = frame.getStack(frame.getStackSize() - types.length - 1);
                    if (value instanceof LocalValue && methodhInsn.name.equals("<init>")) {
                        if (poppedThis) {
                            throw new IllegalStateException("Unexpected constructor structure.");
                        }
                        return split(owner, method, lastThis, methodhInsn, recentLine,
                                new ArrayList<LocalVariable>(variables), localsAtLastThis);
                    }
                } else if (insn instanceof VarInsnNode) {
                    VarInsnNode var = (VarInsnNode) insn;
                    if (var.var == 0) {
                        lastThis = var;
                        localsAtLastThis = variables.size();
                        stackAtThis = frame.getStackSize();
                        poppedThis = false;
                    }
                    Type type = ByteCodeUtils.getTypeForStoreOpcode(var.getOpcode());
                    if (type != null && var.var >= firstLocal) {
                        // Variables are equals based on their number, so they will be added
                        // to the set only if they are new, and in the order they are seen.
                        variables.add(new LocalVariable(type, var.var));
                    }
                } else if (insn instanceof LineNumberNode) {
                    // Record the most recent line number encountered so that call to this(...)
                    // or super(...) has line number information. Ultimately used to emit a line
                    // number in the generated code.
                    LineNumberNode lineNumberNode = (LineNumberNode) insn;
                    recentLine = lineNumberNode.line;
                }
            }
            throw new IllegalStateException("Unexpected constructor structure.");
        } catch (AnalyzerException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Splits the constructor in two methods, the "set up" and the "body" parts (see above).
     */
    @NonNull
    private static Constructor split(@NonNull String owner, @NonNull MethodNode method,
            @NonNull VarInsnNode loadThis, @NonNull MethodInsnNode delegation, int loadThisLine,
            @NonNull List<LocalVariable> variables, int localsAtLoadThis) {
        String[] exceptions = ((List<String>)method.exceptions).toArray(new String[method.exceptions.size()]);

        // Do not add the local array yet, as we treat it as a new variable.
        String newDesc = method.desc.replace(")V", ")Ljava/lang/Object;");
        newDesc = newDesc.replace("(", "([L" + owner + ";");

        Type[] argumentTypes = Type.getArgumentTypes(newDesc);

        // Store the non hotswappable part of the constructor
        List<AbstractInsnNode> fixed = Lists.newLinkedList();
        AbstractInsnNode insn = method.instructions.getFirst();
        while (insn != loadThis) {
            fixed.add(insn);
            insn = insn.getNext();
        }
        fixed.add(loadThis);

        MethodNode initArgs = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "init$args", newDesc, null, exceptions);
        GeneratorAdapter mv = new GeneratorAdapter(initArgs, initArgs.access, initArgs.name, initArgs.desc);
        int newArgument = mv.newLocal(Type.getType("[Ljava/lang/Object;"));

        mv.loadLocal(newArgument);
        ByteCodeUtils.restoreVariables(mv, variables.subList(0, localsAtLoadThis));

        // Now insert the original method
        insn = loadThis.getNext();
        while (insn != delegation) {
            insn.accept(mv);
            insn = insn.getNext();
        }
        LabelNode labelBefore = new LabelNode();
        labelBefore.accept(mv);

        // Create the args array with the local variables and the values to send to the delegated constructor
        Type[] returnTypes = Type.getArgumentTypes(delegation.desc);
        // The extra elements for the local variables and the qualified name of the constructor.
        mv.push(returnTypes.length + 2);
        mv.newArray(Type.getType(Object.class));
        int args = mv.newLocal(Type.getType("[Ljava/lang/Object;"));
        mv.storeLocal(args);
        for (int i = returnTypes.length - 1; i >= 0; i--) {
            Type type = returnTypes[i];
            mv.loadLocal(args);
            mv.swap(type, Type.getType(Object.class));
            mv.push(i + 2);
            mv.swap(type, Type.INT_TYPE);
            mv.box(type);
            mv.arrayStore(Type.getType(Object.class));
        }

        // Store the qualified name of the constructor in the second element of the array.
        mv.loadLocal(args);
        mv.push(1);
        mv.push(delegation.owner + "." + delegation.desc); // Name of the constructor to be called.
        mv.arrayStore(Type.getType(Object.class));

        // Create the locals array and place it in the first element of the return array
        mv.loadLocal(args);
        mv.push(0);
        mv.push(argumentTypes.length + 1);
        mv.newArray(Type.getType(Object.class));
        ByteCodeUtils.loadVariableArray(mv, ByteCodeUtils.toLocalVariables(Arrays.asList(argumentTypes)), 0);

        mv.dup();
        mv.push(argumentTypes.length);
        ByteCodeUtils.newVariableArray(mv, variables);
        mv.arrayStore(Type.getType(Object.class));

        mv.arrayStore(Type.getType(Object.class));

        mv.loadLocal(args);
        mv.returnValue();

        // Move the first variable up to be an argument
        initArgs.desc = initArgs.desc.replace(")", "[Ljava/lang/Object;)");

        newDesc =  method.desc.replace("(", "(L" + owner + ";");
        MethodNode body = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "init$body", newDesc, null, exceptions);
        mv = new GeneratorAdapter(body, body.access, body.name, body.desc);
        newArgument = mv.newLocal(Type.getType("[Ljava/lang/Object;"));

        LabelNode labelAfter = new LabelNode();
        labelAfter.accept(body);
        Set<LabelNode> bodyLabels = new HashSet<LabelNode>();

        mv.loadLocal(newArgument);
        ByteCodeUtils.restoreVariables(mv, variables);

        insn = delegation.getNext();
        while (insn != null) {
            if (insn instanceof LabelNode) {
                bodyLabels.add((LabelNode) insn);
            }
            insn.accept(mv);
            insn = insn.getNext();
        }

        // manually transfer the exception table from the existing constructor to the new
        // "init$body" method. The labels were transferred just above so we can reuse them.

        //noinspection unchecked
        for (TryCatchBlockNode tryCatch : (List<TryCatchBlockNode>) method.tryCatchBlocks) {
            tryCatch.accept(mv);
        }

        //noinspection unchecked
        for (LocalVariableNode variable : (List<LocalVariableNode>) method.localVariables) {
            boolean startsInBody = bodyLabels.contains(variable.start);
            boolean endsInBody = bodyLabels.contains(variable.end);
            if (!startsInBody && !endsInBody) {
                if (variable.index != 0) { // '#0' on init$args is not 'this'
                    variable.accept(initArgs);
                }
            } else if (startsInBody && endsInBody) {
                variable.accept(body);
            } else if (!startsInBody && endsInBody) {
                // The variable spans from the args to the end of the method, create two:
                if (variable.index != 0) { // '#0' on init$args is not 'this'
                    LocalVariableNode var0 = new LocalVariableNode(variable.name,
                            variable.desc, variable.signature,
                            variable.start, labelBefore, variable.index);
                    var0.accept(initArgs);
                }
                LocalVariableNode var1 = new LocalVariableNode(variable.name,
                        variable.desc, variable.signature,
                        labelAfter, variable.end, variable.index);
                var1.accept(body);
            } else {
                throw new IllegalStateException("Local variable starts after it ends.");
            }
        }
        // Move the first variable up to be an argument
        body.desc = body.desc.replace(")", "[Ljava/lang/Object;)");

        return new Constructor(owner, fixed, loadThis, loadThisLine, initArgs, delegation, body, variables, localsAtLoadThis);
    }

}
