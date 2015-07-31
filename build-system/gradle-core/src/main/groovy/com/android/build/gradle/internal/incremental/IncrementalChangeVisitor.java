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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.HashMap;
import java.util.Map;

/**
 * Visitor for classes that have been changed since the initial push.
 *
 * This will generate a new class which name is the original class name + Support. This class will
 * have a static method for each method found in the updated class.
 *
 * The static method will be invoked from the generic
 * {@link IncrementalSupportRuntime#dispatch(Object, String, String, Object...)} implementation
 * following a delegation request issued by the original method implementation (through the bytecode
 * injection done in {@link IncrementalSupportVisitor}.
 *
 * So far the static method implementation do not require any change since the "this" parameter
 * is passed as the first parameter and is available in register 0.
 */
public class IncrementalChangeVisitor extends ClassVisitor {

    String className;
    String superName;
    Map<String, String> privateFields = new HashMap<String, String>();

    public IncrementalChangeVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM5, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        className = name;
        this.superName = superName;
        super.visit(version, access, name + "ISSupport", signature, "java/lang/Object", interfaces);
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
        String newDesc = "(L" + className + ";" + desc.substring(1);
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
            if (privateFields.containsKey(name) && owner.equals(className)) {
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
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }
}
