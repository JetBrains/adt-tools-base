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
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Visitor for classes that will eventually be replaceable at runtime.
 *
 * Since classes cannot be replaced in an existing class loader, we use a delegation model to
 * redirect any method implementation to the {@link IncrementalSupportRuntime}.
 *
 * This redirection happens only when a new class implementation is available, so far we do a
 * hashtable lookup for updated implementation. In the future, we could generate a static field
 * during the class visit with this visitor and have that boolean field indicate the presence of an
 * updated version or not.
 */
public class IncrementalSupportVisitor extends IncrementalVisitor {


    private static final Type CHANGE_TYPE = Type.getType(IncrementalChange.class);

    protected String visitedClassName;
    protected String visitedSuperName;
    protected Set<Method> methods = new HashSet<Method>();

    public IncrementalSupportVisitor(ClassVisitor classVisitor) {
        super(classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        System.out.println("Visiting " + name);
        visitedClassName = name;
        visitedSuperName = superName;

        super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "$change", CHANGE_TYPE.getDescriptor(), null, null);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        System.out.println("Visiting method " + name + " desc " + desc);
        MethodVisitor defaultVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        if (name.equals("<init>") || name.equals("<clinit>")) {
            return defaultVisitor;
        }
        methods.add(new Method(name, desc));
        return new ISMethodVisitor(Opcodes.ASM5, defaultVisitor, access, name, desc);
    }

    private class ISMethodVisitor extends GeneratorAdapter {

        private final MethodVisitor mv;
        private final String name;
        private final String desc;
        private final int access;

        public ISMethodVisitor(int api, MethodVisitor mv, int access,  String name, String desc) {
            super(api, mv, access, name, desc);
            this.mv = mv;
            this.name = name;
            this.desc = desc;
            this.access = access;
        }

        @Override
        public void visitCode() {
            // code to check if a new implementation of the current class is available.
            visitFieldInsn(Opcodes.GETSTATIC, visitedClassName, "$change",
                    CHANGE_TYPE.getDescriptor());
            Label l0 = new Label();
            super.visitJumpInsn(Opcodes.IFNULL, l0);
            visitFieldInsn(Opcodes.GETSTATIC, visitedClassName, "$change",
                    CHANGE_TYPE.getDescriptor());
            push(name + "." + desc);

            List<Type> args = new ArrayList<Type>(Arrays.asList(Type.getArgumentTypes(desc)));
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            if (!isStatic) {
                args.add(0, Type.getType(Object.class));
            }
            push(args.size());
            newArray(Type.getType(Object.class));

            for (int index = 0; index < args.size(); index++) {
                Type arg = args.get(index);
                dup();
                push(index);
                // This will load "this" when it's not static function as the first element
                mv.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), index);
                box(arg);
                arrayStore(Type.getType(Object.class));
            }

            // now invoke the generic dispatch method.
            invokeInterface(CHANGE_TYPE, Method.getMethod("Object access$dispatch(String, Object[])"));
            Type ret = Type.getReturnType(desc);
            if (ret == Type.VOID_TYPE) {
                pop();
            } else {
                unbox(ret);
            }
            returnValue();

            // jump label for classes without any new implementation, just invoke the original
            // method implementation.
            visitLabel(l0);
            super.visitCode();
        }
    }

    @Override
    public void visitEnd() {
        int access = Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC | Opcodes.ACC_VARARGS;
        Method m = new Method("access$super", "(L" + visitedClassName + ";Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
        MethodVisitor visitor = super.visitMethod(access,
                        m.getName(),
                        m.getDescriptor(),
                        null, null);

        GeneratorAdapter mv = new GeneratorAdapter(access, m, visitor);

        for (Method method : methods) {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitLdcInsn(method.getName() + "." + method.getDescriptor());
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
            Label l0 = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, l0);
            mv.visitVarInsn(Opcodes.ALOAD, 0);

            Type[] args = method.getArgumentTypes();
            int argc = 0;
            for (Type t : args) {
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.push(argc);
                mv.visitInsn(Opcodes.AALOAD);
                mv.unbox(t);
                argc++;
            }

            // Call super on the other object, yup this works cos we are on the right place to call from.
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, visitedSuperName, method.getName(), method.getDescriptor(), false);
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
