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

import java.io.IOException;
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

    // todo : find a better way to specify logging and append to a log file.
    private static final boolean DEBUG = false;


    public IncrementalChangeVisitor(ClassNode classNode, List<ClassNode> parentNodes, ClassVisitor classVisitor) {
        super(classNode, parentNodes, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        super.visit(version, access, name + "$override", signature, "java/lang/Object",
                new String[]{CHANGE_TYPE.getInternalName()});

        if (DEBUG) {
            System.out.println(">>>>>>>> Processing " + name + "<<<<<<<<<<<<<");
        }

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

        if (name.equals("<clinit>")) {
            return null;
        }

        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        String newDesc = isStatic ? desc : "(L" + visitedClassName + ";" + desc.substring(1);

        if (DEBUG) {
            System.out.println(">>> Visiting method " + visitedClassName + ":" + name + ":" + desc);
            if (exceptions != null) {
                for (String exception : exceptions) {
                    System.out.println("> Exception thrown : " + exception);
                }
            }
        }
        if (DEBUG) {
            System.out.println("New Desc is " + newDesc + ":" + isStatic);
        }

        String newName = getOverridenName(name);
        // Do not carry on any access flags from the original method. For example synchronized
        // on the original method would translate into a static synchronized method here.
        access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        MethodVisitor original = super.visitMethod(access, newName, newDesc, signature, exceptions);
        if (name.equals("<init>")) {
            return new ConstructorVisitor(Opcodes.ASM5, original, access, newName, newDesc);
        } else {
            return new ISVisitor(Opcodes.ASM5, original, access, newName, newDesc, isStatic);
        }
    }

    /**
     * {@link MethodVisitor} implementation that is effectively swallowing all code until
     * {@link #stopIgnoring()} method is called.
     */
    public static class IgnoringMethodVisitorAdapter extends MethodVisitor {

        private MethodVisitor delegateVisitor;

        public IgnoringMethodVisitorAdapter(int api, MethodVisitor mv) {
            super(api, null);
            delegateVisitor = mv;
        }

        public void stopIgnoring() {
            super.mv = delegateVisitor;
        }
    }

    public class ConstructorVisitor extends ISVisitor {

        String desc;
        IgnoringMethodVisitorAdapter adapter;

        public ConstructorVisitor(int api, MethodVisitor mv, int access, String name, String desc) {
            super(api, new IgnoringMethodVisitorAdapter(api, mv), access, name, desc, false);
            adapter = (IgnoringMethodVisitorAdapter) this.mv;
            this.desc = desc;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")
                    && owner.equals(visitedSuperName)) {
                adapter.stopIgnoring();
            }
        }
    }

    /**
     * Enumeration describing a method of field access rights.
     */
    private enum AccessRight {
        PRIVATE, PACKAGE_PRIVATE, PROTECTED, PUBLIC;

        static AccessRight fromNodeAccess(int nodeAccess) {
            if ((nodeAccess & Opcodes.ACC_PRIVATE) != 0) return PRIVATE;
            if ((nodeAccess & Opcodes.ACC_PROTECTED) != 0) return PROTECTED;
            if ((nodeAccess & Opcodes.ACC_PUBLIC) != 0) return PUBLIC;
            return PACKAGE_PRIVATE;
        }
    }

    /**
     * Enumeration describing whether a method or field is static or not.
     */
    private enum AccessType {
        STATIC, INSTANCE;

        static AccessType fromNodeAccess(int nodeAccess) {
            return (nodeAccess & Opcodes.ACC_STATIC) != 0
                    ? STATIC
                    : INSTANCE;
        }
    }

    public class ISVisitor extends GeneratorAdapter {

        private final boolean isStatic;

        public ISVisitor(
                int api,
                MethodVisitor mv,
                int access,
                String name,
                String desc,
                boolean isStatic) {
            super(api, mv, access, name, desc);
            this.isStatic = isStatic;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (DEBUG) {
                System.out.println(
                        "Visit field access : " + owner + ":" + name + ":" + desc + ":" + isStatic);
            }
            // if we are access another object's field, nothing needs to be done.
            if (!owner.equals(visitedClassName)) {
                if (DEBUG) {
                    System.out.println("Not ours, unchanged field access");
                }
                // this is probably incorrect, what about if we access a package private field
                // of some other object, we need to go through reflection.
                super.visitFieldInsn(opcode, owner, name, desc);
                return;
            }

            // check the field access bits.
            FieldNode fieldNode = getFieldByName(name);
            if (fieldNode == null) {
                // this is an error, we should know of the fields we are visiting.
                throw new RuntimeException("Unknown field access " + name);
            }

            AccessType accessType = AccessType.fromNodeAccess(fieldNode.access);
            AccessRight accessRight = AccessRight.fromNodeAccess(fieldNode.access);

            boolean handled = false;
            switch(opcode) {
                case Opcodes.PUTSTATIC:
                case Opcodes.GETSTATIC:
                    assert accessType == AccessType.STATIC;
                    handled = visitStaticFieldAccess(opcode, owner, name, desc, accessRight);
                    break;
                case Opcodes.PUTFIELD:
                case Opcodes.GETFIELD:
                    assert accessType == AccessType.INSTANCE;
                    handled = visitFieldAccess(opcode, name, desc, accessRight);
                    break;
                default:
                    System.out.println("Unhandled field opcode " + opcode);
            }
            if (!handled) {
                super.visitFieldInsn(opcode, owner, name, desc);
            }
        }

        /**
         * Visits an instance field access. The field could be of the visited class or it could be
         * an accessible field from the class being visited (unless it's private).
         * @param opcode the field access opcode, can only be {@link Opcodes#PUTFIELD} or
         *               {@link Opcodes#GETFIELD}
         * @param name the field name
         * @param desc the field type
         * @param accessRight the {@link AccessRight} for the field.
         * @return true if the field access was handled or false otherwise.
         */
        private boolean visitFieldAccess(
                int opcode, String name, String desc, AccessRight accessRight) {

            // we should make this more efficient, have a per field access type method
            // for getting and setting field values.
            if (accessRight != AccessRight.PUBLIC) {
                switch (opcode) {
                    case Opcodes.GETFIELD:
                        if (DEBUG) {
                            System.out.println("Get field");
                        }
                        // the instance of the owner class we are getting the field value from
                        // is on top of the stack. It could be "this"
                        push(name);
                        // Stack :  <receiver>
                        //          <field_name>
                        invokeStatic(RUNTIME_TYPE,
                                Method.getMethod("Object getPrivateField(Object, String)"));
                        // Stack : <field_value>
                        unbox(Type.getType(desc));
                        break;
                    case Opcodes.PUTFIELD:
                        if (DEBUG) {
                            System.out.println("Set field");
                        }
                        // the instance of the owner class we are getting the field value from
                        // is second on the stack. It could be "this"
                        // top of the stack is the new value we are trying to set, box it.
                        box(Type.getType(desc));
                        // push the field name.
                        push(name);
                        // Stack :  <receiver>
                        //          <boxed_field_value>
                        //          <field_name>
                        invokeStatic(RUNTIME_TYPE,
                                Method.getMethod(
                                        "void setPrivateField(Object, Object, String)"));
                        break;
                    default:
                        throw new RuntimeException(
                                "VisitFieldAccess called with wrong opcode " + opcode);
                }
                return true;
            }
            // if this is a public field, no need to change anything we can access it from the
            // $override class.
            return false;
        }

        /**
         * Static field access visit.
         * So far we do not support class initializer "clinit" that would reset the static field
         * value in the class newer versions. Think about the case, where a static initializer
         * resets a static field value, we don't know if the current field value was set through
         * the initial class initializer or some code path, should we change the field value to the
         * new one ?
         *
         * @param opcode the field access opcode, can only be {@link Opcodes#PUTSTATIC} or
         *               {@link Opcodes#GETSTATIC}
         * @param name the field name
         * @param desc the field type
         * @param accessRight the {@link AccessRight} for the field.
         * @return true if the field access was handled or false
         */
        private boolean visitStaticFieldAccess(
                int opcode, String owner, String name, String desc, AccessRight accessRight) {

            if (accessRight != AccessRight.PUBLIC) {
                switch (opcode) {
                    case Opcodes.GETSTATIC:
                        if (DEBUG) {
                            System.out.println("Get static field " + name);
                        }
                        // nothing of interest is on the stack.
                        visitLdcInsn(Type.getType("L" + owner + ";"));
                        push(name);
                        // Stack : <target_class>
                        //         <field_name>
                        invokeStatic(RUNTIME_TYPE,
                                Method.getMethod("Object getStaticPrivateField(Class, String)"));
                        // Stack : <field_value>
                        unbox(Type.getType(desc));
                        return true;
                    case Opcodes.PUTSTATIC:
                        if (DEBUG) {
                            System.out.println("Set static field " + name);
                        }
                        // the new field value is on top of the stack.
                        // box it into an Object.
                        box(Type.getType(desc));
                        visitLdcInsn(Type.getType("L" + owner + ";"));
                        push(name);
                        // Stack :  <boxed_field_value>
                        //          <target_class>
                        //          <field_name>
                        invokeStatic(RUNTIME_TYPE,
                                Method.getMethod(
                                        "void setStaticPrivateField(Object, Class, String)"));
                        return true;
                    default:
                        throw new RuntimeException(
                                "VisitStaticFieldAccess called with wrong opcode " + opcode);
                }
            }
            return false;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {

            if (DEBUG) {
                System.out.println("Generic Method dispatch : " + opcode +
                        ":" + owner + ":" + name + ":" + desc + ":" + itf + ":" + isStatic);
            }
            boolean opcodeHandled = false;
            if (opcode == Opcodes.INVOKESPECIAL) {
                opcodeHandled = handleSpecialOpcode(owner, name, desc, itf);
            } else if (opcode == Opcodes.INVOKEVIRTUAL) {
                opcodeHandled = handleVirtualOpcode(owner, name, desc, itf);
            } else if (opcode == Opcodes.INVOKESTATIC) {
                opcodeHandled = handleStaticOpcode(owner, name, desc, itf);
            }
            if (DEBUG) {
                System.out.println("Opcode handled ? " + opcodeHandled);
            }
            if (!opcodeHandled) {
                mv.visitMethodInsn(opcode, owner, name, desc, itf);
            }
            if (DEBUG) {
                System.out.println("Done with generic method dispatch");
            }
        }

        private boolean handleSpecialOpcode(String owner, String name, String desc,
                boolean itf) {
            if (owner.equals(visitedSuperName)) {
                if (DEBUG) {
                    System.out.println(
                            "Super Method dispatch : " + name + ":" + desc + ":" + itf + ":"
                                    + isStatic);
                }
                int arr = boxParametersToNewLocalArray(Type.getArgumentTypes(desc));
                push(name + "." + desc);
                loadLocal(arr);
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
                if (DEBUG) {
                    System.out.println(
                            "Private Method dispatch : " + name + ":" + desc + ":" + itf + ":"
                                    + isStatic);
                }
                // private method dispatch, just invoke the $override class static method.
                String newDesc = "(L" + visitedClassName + ";" + desc.substring(1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner + "$override", name, newDesc, itf);
                return true;
            }
            return false;
        }

        private boolean handleVirtualOpcode(String owner, String name, String desc,
                boolean itf) {
            if (owner.equals(visitedClassName)) {

                if (DEBUG) {
                    System.out.println(
                            "Method dispatch : " + name + ":" + desc + ":" + itf + ":" + isStatic);
                }
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

                int parameters = boxParametersToNewLocalArray(parameterTypes);

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

                invokeStatic(RUNTIME_TYPE,
                        Method.getMethod(
                                "Object invokeProtectedMethod(Object, String, String[], Object[])"));
                Type ret = Type.getReturnType(desc);
                if (ret.getSort() == Type.VOID) {
                    pop();
                } else {
                    unbox(ret);
                }
                return true;
            }
            return false;
        }

        private int boxParametersToNewLocalArray(Type[] parameterTypes) {
            int parameters = newLocal(Type.getType("[Ljava/lang.Object;"));
            push(parameterTypes.length);
            newArray(Type.getType(Object.class));
            storeLocal(parameters);

            for (int i = parameterTypes.length - 1; i >= 0; i--) {
                loadLocal(parameters);
                swap(parameterTypes[i], Type.getType(Object.class));
                push(i);
                swap(parameterTypes[i], Type.INT_TYPE);
                box(parameterTypes[i]);
                arrayStore(Type.getType(Object.class));
            }
            return parameters;
        }

        // we must do something similar as for non static method,
        // which is to call back the ORIGINAL static method
        // using reflection when the called method is anything but public.
        // I thought for a while we could bypass and call directly the $override method
        // but we can't because the static method could be of the super class and we don't
        // necessarily have an enhanced class to call directly.
        private boolean handleStaticOpcode(String owner, String name, String desc,
                boolean itf) {
            return false;
        }

        @Override
        public void visitEnd() {
            if (DEBUG) {
                System.out.println("Method visit end");
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

        if (TRACING_ENABLED) {
            mv.push("Redirecting ");
            mv.loadArg(0);
            trace(mv, 2);
        }

        List<MethodNode> methods = classNode.methods;
        for (MethodNode methodNode : methods) {
            if (methodNode.name.equals("<clinit>")) {
                continue;
            }
            String name = methodNode.name;
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
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, visitedClassName + "$override", getOverridenName(name), newDesc,
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
        // this is an exception, we cannot find the method to dispatch, the verifier should have
        // flagged this and refused the hotswaping, generate an exception.
        // we could not find the method to invoke, prepare an exception to be thrown.
        mv.newInstance(Type.getType(StringBuilder.class));
        mv.dup();
        mv.invokeConstructor(Type.getType(StringBuilder.class), Method.getMethod("void <init>()V"));

        // TODO: have a common exception generation function.
        // create a meaningful message
        mv.push("Method not found ");
        mv.invokeVirtual(Type.getType(StringBuilder.class),
                Method.getMethod("StringBuilder append (String)"));
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.invokeVirtual(Type.getType(StringBuilder.class),
                Method.getMethod("StringBuilder append (String)"));
        mv.push("in " + visitedClassName + "$dispatch implementation, restart the application");
        mv.invokeVirtual(Type.getType(StringBuilder.class),
                Method.getMethod("StringBuilder append (String)"));

        mv.invokeVirtual(Type.getType(StringBuilder.class),
                Method.getMethod("String toString()"));

        // create the exception with the message
        mv.newInstance(INSTANT_RELOAD_EXCEPTION);
        mv.dupX1();
        mv.swap();
        mv.invokeConstructor(INSTANT_RELOAD_EXCEPTION,
                Method.getMethod("void <init> (String)"));
        // and throw.
        mv.throwException();

        mv.visitMaxs(0, 0);
        mv.visitEnd();

        super.visitEnd();
    }

    private static String getOverridenName(String methodName) {
        // TODO: change the method name as it can now collide with existing static methods with
        // the same signature.
        if (methodName.equals("<init>")) {
            return "init$override";
        }
        return methodName;
    }

    /**
     * Command line invocation entry point. Expects 2 parameters, first is the source directory
     * with .class files as produced by the Java compiler, second is the output directory where to
     * store the bytecode enhanced version.
     * @param args the command line arguments.
     * @throws IOException if some files cannot be read or written.
     */
    public static void main(String[] args) throws IOException {

        IncrementalVisitor.main(args, new VisitorBuilder() {
            @Override
            public IncrementalVisitor build(@NonNull ClassNode classNode,
                    List<ClassNode> parentNodes,
                    ClassVisitor classVisitor) {
                return new IncrementalChangeVisitor(classNode, parentNodes, classVisitor);
            }

            @Override
            public boolean processParents() {
                return true;
            }
        });
    }
}
