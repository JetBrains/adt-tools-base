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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

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

    public IncrementalChangeVisitor(ClassNode classNode, List<ClassNode> parentNodes, ClassVisitor classVisitor) {
        super(classNode, parentNodes, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        super.visit(version, access, name + "$override", signature, "java/lang/Object",
                new String[]{"com/android/build/gradle/internal/incremental/IncrementalChange"});

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
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {

        System.out.println("Visiting method " + name + " signature " + desc);
        if (name.equals("<init>") || name.equals("<clinit>")) {
            return null;
        }
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        String newDesc = isStatic ? desc : "(L" + visitedClassName + ";" + desc.substring(1);
        System.out.println("new Desc is " + newDesc);

        // clear the private/protected bit if present, package private is 0.
        // change the method visibility to always be public and static.
        access  = access & ~Opcodes.ACC_PRIVATE;
        access =  access & ~Opcodes.ACC_PROTECTED;
        access = access | Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        return new ISVisitor(Opcodes.ASM5,
                super.visitMethod(access, name, newDesc, signature, exceptions),
                access,
                name, newDesc);
    }

    public static class AdapterVisitor extends MethodVisitor {

        private MethodVisitor ignored;

        public AdapterVisitor(int api, MethodVisitor mv) {
            super(api, mv);
        }

        public void setIgnore(boolean ignore) {
            if (ignore) {
                ignored = this.mv;
                this.mv = null;
            } else {
                this.mv = ignored;
                ignored = null;
            }
        }
    }

    public class PreCtrVisitor extends ISVisitor {

        String desc;

        AdapterVisitor adapter;

        public PreCtrVisitor(int api, MethodVisitor mv, int access, String name, String desc) {
            super(api, new AdapterVisitor(api, mv), access, name, desc);
            adapter = (AdapterVisitor) this.mv;
            this.desc = desc;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {
            if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>") && owner
                    .equals(visitedSuperName)) {
                // here all the arguments for the super call are in the stack.
                // Move them to an array and return it.
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
                // Here we should have the uninitialized object on the stack, which in this case is
                // the array of local variables. Update their value.
                args = Type.getArgumentTypes(this.desc);
                for (int i = 1; i < args.length; i++) {
                    dup();
                    push(i);
                    visitVarInsn(args[i].getOpcode(Opcodes.ILOAD), i);
                    box(args[i]);
                    arrayStore(Type.getType(Object.class));
                }
                // pop the array
                pop();

                visitVarInsn(Opcodes.ALOAD, arr);
                returnValue();
                visitMaxs(0, 0);
                adapter.setIgnore(true);
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }
    }

    public class PostCtrVisitor extends ISVisitor {

        AdapterVisitor adapter;

        public PostCtrVisitor(int api, MethodVisitor mv, int access, String name, String desc) {
            super(api, new AdapterVisitor(api, mv), access, name, desc);
            adapter = (AdapterVisitor) this.mv;
            adapter.setIgnore(true);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {
            if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>") && owner
                    .equals(visitedSuperName)) {
                adapter.setIgnore(false);
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }
    }

    public class ISVisitor extends GeneratorAdapter {

        public ISVisitor(int api, MethodVisitor mv, int access,  String name, String desc) {
            super(api, mv, access, name, desc);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            // if we are access another object's field, nothing needs to be done.
            if (!owner.equals(visitedClassName)) {
                super.visitFieldInsn(opcode, owner, name, desc);
                return;
            }
            // check the field access bits.
            FieldNode fieldNode = getFieldByName(name);
            if (fieldNode == null) {
                // this is an error, we should know of the fields we are visiting.
                throw new RuntimeException("Unknown field access " + name);
            }
            boolean isPrivate = (fieldNode.access & Opcodes.ACC_PRIVATE) != 0;
            boolean isProtected = (fieldNode.access & Opcodes.ACC_PROTECTED) != 0;
            boolean isPackagePrivate = fieldNode.access == 0;

            // we should make this more efficient, have a per field access type method
            // for getting and setting field values.
            if (isPrivate || isProtected || isPackagePrivate) {
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
            boolean opcodeHandled = false;
            if (opcode == Opcodes.INVOKESPECIAL) {
                opcodeHandled = handleSpecialOpcode(opcode, owner, name, desc, itf);
            } else if (opcode == Opcodes.INVOKEVIRTUAL) {
                opcodeHandled = handleVirtualOpcode(opcode, owner, name, desc, itf);
            } else if (opcode == Opcodes.INVOKESTATIC) {
                opcodeHandled = handleStaticOpcode(opcode, owner, name, desc, itf);
            }
            if (!opcodeHandled) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }

        private boolean handleSpecialOpcode(int opcode, String owner, String name, String desc,
                boolean itf) {
            if (owner.equals(visitedSuperName)) {
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
                return true;
            } else if (owner.equals(visitedClassName)) {
                // private method dispatch, just invoke the $override class static method.
                String newDesc = "(L" + visitedClassName + ";" + desc.substring(1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner + "$override", name, newDesc, itf);
                return true;
            }
            return false;
        }

        private boolean handleVirtualOpcode(int opcode, String owner, String name, String desc,
                boolean itf) {
            if (owner.equals(visitedClassName)) {

                MethodNode methodNode = getMethodByName(name, desc);
                boolean isPublic = methodNode != null
                        && ((methodNode.access & Opcodes.ACC_PUBLIC) != 0);

                // if this is a public method, just let the normal invoke virtual invoke the
                // original method implementation which in most case will just call back
                // into the enhanced code.
                if (isPublic) {
                    return false;
                }

                // for anything else, private, protected and package private, we must go through
                // reflection.
                Type[] parameterTypes = Type.getArgumentTypes(desc);

                push(parameterTypes.length);
                int parameters = newLocal(Type.getType(Object.class));
                newArray(Type.getType(Object.class));
                storeLocal(parameters);

                for (int i = parameterTypes.length - 1; i >= 0; i--) {
                    loadLocal(parameters);
                    swap();
                    push(i);
                    swap();
                    box(parameterTypes[i]);
                    arrayStore(Type.getType(Object.class));
                }

                push(name);
                push(parameterTypes.length);
                newArray(Type.getType(String.class));

                for (int i = 0; i < parameterTypes.length; i++) {
                    dup();
                    push(i);
                    push(parameterTypes[i].getClassName());
                    arrayStore(Type.getType(String.class));
                }

                loadLocal(parameters);

                invokeStatic(Type.getType(IncrementalSupportRuntime.class),
                        Method.getMethod(
                                "Object invokeProtectedMethod(Object, String, String[], Object[])"));
                unbox(Type.getReturnType(desc));
                return true;
            }
            return false;
        }

        // we must do something similar as for non static method,
        // which is to call back the ORIGINAL static method
        // using reflection when the called method is anything but public.
        // I thought for a while we could bypass and call directly the $override method
        // but we can't because the static method could be of the super class and we don't
        // necessarily have an enhanced class to call directly.
        private boolean handleStaticOpcode(int opcode, String owner, String name, String desc,
                boolean itf) {
            return false;
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

        if (TRACING_ENABLED) {
            mv.push("Redirecting ");
            mv.loadArg(0);
            trace(mv, 2);
        }

        List<MethodNode> methods = classNode.methods;
        List<MethodNode> constructors = new ArrayList<MethodNode>();
        for (MethodNode methodNode : methods) {
            if (methodNode.name.equals("<clinit>")) {
                continue;
            }
            String name = methodNode.name;
            if (name.equals("<init>")) {
                constructors.add(methodNode);
                name = "init$override";
            }


            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitLdcInsn(name + "." + methodNode.desc);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
                    "(Ljava/lang/Object;)Z", false);
            Label l0 = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, l0);
            // this should be abstracted somewhere.
            String newDesc = (methodNode.access & Opcodes.ACC_STATIC) != 0
                    ? methodNode.desc
                    : "(L" + visitedClassName + ";" + methodNode.desc.substring(1);

            if (TRACING_ENABLED) {
                trace(mv, "M: " + name + " P:" + newDesc);
            }
            Type[] args = Type.getArgumentTypes(newDesc);
            int argc = 0;
            for (Type t : args) {
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.push(argc);
                mv.visitInsn(Opcodes.AALOAD);
                mv.unbox(t);
                argc++;
            }
            // TODO: change the method name as it can now collide with existing static methods with
            // the same signature.
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, visitedClassName + "$override", name, newDesc,
                    false);
            Type ret = Type.getReturnType(methodNode.desc);
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

        m = new Method("access$ctr", "(Ljava/lang/String;[Ljava/lang/Object;)[Ljava/lang/Object;");
        visitor = super.visitMethod(access,
                m.getName(),
                m.getDescriptor(),
                null, null);

        mv = new GeneratorAdapter(access, m, visitor);
        for (MethodNode method : constructors) {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitLdcInsn(method.desc);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
                    "(Ljava/lang/Object;)Z", false);
            Label l0 = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, l0);
            String newDesc = "([Ljava/lang/Object;" + method.desc.substring(1);
            newDesc = newDesc.replace(")V", ")[Ljava/lang/Object;");

            mv.visitVarInsn(Opcodes.ALOAD, 2); // First argument, local variables

            Type[] args = Type.getArgumentTypes(method.desc);
            int argc = 0;
            for (Type t : args) {
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.push(argc + 1);
                mv.visitInsn(Opcodes.AALOAD);
                mv.unbox(t);
                argc++;
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, visitedClassName + "$override", "init$before",
                    newDesc, false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitLabel(l0);
        }
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        for (MethodNode method : constructors) {
            String newDesc = "([Ljava/lang/Object;" + method.desc.substring(1);
            newDesc = newDesc.replace(")V", ")[Ljava/lang/Object;");
            m = new Method("init$before", newDesc);
            visitor = super.visitMethod(access + Opcodes.ACC_STATIC,
                    m.getName(),
                    m.getDescriptor(),
                    null, null);

            mv = new PreCtrVisitor(Opcodes.ASM5, visitor, access + Opcodes.ACC_STATIC, m.getName(), newDesc);
            method.accept(mv);

            newDesc = "(L" + visitedClassName + ";" + method.desc.substring(1);
            m = new Method("init$override", newDesc);
            visitor = super.visitMethod(access + Opcodes.ACC_STATIC,
                    m.getName(),
                    m.getDescriptor(),
                    null, null);

            mv = new PostCtrVisitor(Opcodes.ASM5, visitor, access + Opcodes.ACC_STATIC, m.getName(), newDesc);
            method.accept(mv);
        }
        super.visitEnd();
    }
}
