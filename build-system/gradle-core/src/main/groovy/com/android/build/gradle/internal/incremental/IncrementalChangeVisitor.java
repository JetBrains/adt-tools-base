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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Visitor for classes that have been changed since the initial push.
 *
 * This will generate a new class which name is the original class name + Support. This class will
 * have a static method for each method found in the updated class.
 *
 * The static method will be invoked from the generated access$dispatch method
 * following a delegation request issued by the original method implementation (through the bytecode
 * injection done in {@link IncrementalSupportVisitor}.
 *
 * So far the static method implementation do not require any change since the "this" parameter
 * is passed as the first parameter and is available in register 0.
 */
public class IncrementalChangeVisitor extends IncrementalVisitor {

    protected String visitedClassName;
    protected String visitedSuperName;
    protected Set<Method> methods = new HashSet<Method>();
    Map<String, String> privateFields = new HashMap<String, String>();

    public IncrementalChangeVisitor(ClassVisitor classVisitor) {
        super(classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        super.visit(version, access, name + "$override", signature, "java/lang/Object", new String[] {"com/android/build/gradle/internal/incremental/IncrementalChange"});

        System.out.println("Visiting " + name);
        visitedClassName = name;
        visitedSuperName = superName;

        // Create empty constructor
        MethodVisitor mv = super
                .visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V",
                false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
            Object value) {
        boolean isPrivate=(access & Opcodes.ACC_PRIVATE) != 0;
        boolean isProtected=(access & Opcodes.ACC_PROTECTED) != 0;
        if (isPrivate || isProtected) {
            privateFields.put(name, desc);
        }
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {

        System.out.println("Visiting method " + name + " signature " + desc);
        if (name.equals("<init>") || name.equals("<clinit>")) {
            return null;
        }
        methods.add(new Method(name, desc));
        String newDesc = "(L" + visitedClassName + ";" + desc.substring(1);
        System.out.println("new Desc is " + newDesc);
        return new ISVisitor(Opcodes.ASM5,
                super.visitMethod(access + Opcodes.ACC_STATIC, name, newDesc, signature, exceptions),
                access + Opcodes.ACC_STATIC,
                name, desc);
    }

    public class ISVisitor extends GeneratorAdapter {

        public ISVisitor(int api, MethodVisitor mv, int access,  String name, String desc) {
            super(api, mv, access, name, desc);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (privateFields.containsKey(name) && owner.equals(visitedClassName)) {
                if (opcode == Opcodes.GETFIELD) {
                    push(name);
                    invokeStatic(Type.getType(IncrementalSupportRuntime.class),
                            Method.getMethod("Object getPrivateField(Object, String)"));
                    unbox(Type.getType(desc));
                }
                if (opcode == Opcodes.PUTFIELD) {
                    push(name);
                    swap();
                    box(Type.getType(desc));
                    invokeStatic(Type.getType(IncrementalSupportRuntime.class),
                            Method.getMethod(
                                    "void setPrivateField(Object, String, Object)"));
                }
            } else {
                super.visitFieldInsn(opcode, owner, name, desc);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {
            if (opcode == Opcodes.INVOKESPECIAL && owner.equals(visitedSuperName)) {
                int arr = newLocal(Type.getType("[Ljava/lang.Object;"));
                Type[] args = Type.getArgumentTypes(desc);
                push(args.length);
                visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
                visitVarInsn(Opcodes.ASTORE, arr);
                for (int i = args.length - 1; i >= 0; i--) {
                    visitVarInsn(Opcodes.ALOAD, arr);
                    swap();
                    push(i);
                    swap();
                    box(args[i]);
                    visitInsn(Opcodes.AASTORE);
                }
                push(name + "." + desc);
                visitVarInsn(Opcodes.ALOAD, arr);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, visitedClassName, "access$super",
                        "(L" + visitedClassName
                                + ";Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                        false);
                Type ret = Type.getReturnType(desc);
                if (ret.getSort() == Type.VOID) {
                    pop();
                } else {
                    unbox(ret);
                }
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }
    }

    @Override
    public void visitEnd() {
        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_VARARGS;
        Method m = new Method("access$dispatch", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
        MethodVisitor visitor = super.visitMethod(access,
                m.getName(),
                m.getDescriptor(),
                null, null);

        GeneratorAdapter mv = new GeneratorAdapter(access, m, visitor);

        for (Method method : methods) {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitLdcInsn(method.getName() + "." + method.getDescriptor());
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
                    "(Ljava/lang/Object;)Z", false);
            Label l0 = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, l0);
            String newDesc = "(L" + visitedClassName + ";" + method.getDescriptor().substring(1);

            Type[] args = Type.getArgumentTypes(newDesc);
            int argc = 0;
            for (Type t : args) {
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.push(argc);
                mv.visitInsn(Opcodes.AALOAD);
                mv.unbox(t);
                argc++;
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, visitedClassName + "$override", method.getName(), newDesc, false);
            Type ret = method.getReturnType();
            if (ret.getSort() == Type.VOID) {
                mv.visitInsn(Opcodes.ACONST_NULL);
            } else {
                mv.box(ret);
            }
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitLabel(l0);
        }


        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        super.visitEnd();
    }
}
