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

    private MachineState state = MachineState.NORMAL;

    private enum MachineState {
        NORMAL, AFTER_NEW
    }

    public IncrementalChangeVisitor(ClassNode classNode, List<ClassNode> parentNodes, ClassVisitor classVisitor) {
        super(classNode, parentNodes, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        super.visit(version, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                name + "$override", signature, "java/lang/Object",
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
            // we remove the class init as it can reset static fields which we don't support right
            // now.
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

        // Do not carry on any access flags from the original method. For example synchronized
        // on the original method would translate into a static synchronized method here.
        access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        if (name.equals("<init>")) {
            MethodNode method = getMethodByNameInClass(name, desc, classNode);
            ConstructorDelegationDetector.Constructor constructor = ConstructorDelegationDetector.deconstruct(
                    visitedClassName, method);

            MethodVisitor original = super.visitMethod(access, constructor.args.name, constructor.args.desc, constructor.args.signature, exceptions);
            ISVisitor mv = new ISVisitor(Opcodes.ASM5, original, access, constructor.args.name, constructor.args.desc, isStatic);
            constructor.args.accept(mv);

            original = super.visitMethod(access, constructor.body.name, constructor.body.desc, constructor.body.signature, exceptions);
            mv = new ISVisitor(Opcodes.ASM5, original, access, constructor.body.name, newDesc, isStatic);
            constructor.body.accept(mv);

            // Make sure the redirection for the two new methods is created
            classNode.methods.add(constructor.args);
            classNode.methods.add(constructor.body);
            return null;
        } else {
            // TODO: change the method name as it can now collide with existing static methods with
            MethodVisitor original = super.visitMethod(access, name, newDesc, signature, exceptions);
            return new ISVisitor(Opcodes.ASM5, original, access, name, newDesc, isStatic);
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
            AccessRight accessRight;
            if (!owner.equals(visitedClassName)) {
                if (DEBUG) {
                    System.out.println(owner + ":" + name + " field access");
                }
                // we are accessing another object field, and at this point the visitor is not smart
                // enough to know if has seen this class before or not so we must assume the field
                // is *not* accessible from the $override class which lives in a different
                // hierarchy and package.
                accessRight = guessAccessRight(owner);
            } else {
                // check the field access bits.
                FieldNode fieldNode = getFieldByName(name);
                if (fieldNode == null) {
                    // this is an error, we should know of the fields we are visiting.
                    throw new RuntimeException("Unknown field access " + name);
                }
                accessRight = AccessRight.fromNodeAccess(fieldNode.access);
            }

            boolean handled = false;
            switch(opcode) {
                case Opcodes.PUTSTATIC:
                case Opcodes.GETSTATIC:
                    handled = visitStaticFieldAccess(opcode, owner, name, desc, accessRight);
                    break;
                case Opcodes.PUTFIELD:
                case Opcodes.GETFIELD:
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
            if (name.equals("<init>")) {
                return handleConstructor(owner, name, desc);
            }
            if (owner.equals(visitedSuperName)) {
                if (DEBUG) {
                    System.out.println(
                            "Super Method : " + name + ":" + desc + ":" + itf + ":" + isStatic);
                }
                int arr = boxParametersToNewLocalArray(Type.getArgumentTypes(desc));
                push(name + "." + desc);
                loadLocal(arr);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, visitedClassName, "access$super",
                        "(L" + visitedClassName
                                + ";Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                        false);
                handleReturnType(desc);

                return true;
            } else if (owner.equals(visitedClassName)) {
                if (DEBUG) {
                    System.out.println(
                            "Private Method : " + name + ":" + desc + ":" + itf + ":" + isStatic);
                }
                // private method dispatch, just invoke the $override class static method.
                String newDesc = "(L" + visitedClassName + ";" + desc.substring(1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner + "$override", name, newDesc, itf);
                return true;
            }
            return false;
        }

        private boolean handleVirtualOpcode(String owner, String name, String desc, boolean itf) {

            if (DEBUG) {
                System.out.println(
                        "Virtual Method : " + name + ":" + desc + ":" + itf + ":" + isStatic);

            }
            AccessRight accessRight = getMethodAccessRight(owner, name, desc);
            if (accessRight == AccessRight.PUBLIC) {
                return false;
            }

            // for anything else, private, protected and package private, we must go through
            // reflection.
            // Stack : <receiver>
            //      <param_1>
            //      <param_2>
            //      ...
            //      <param_n>
            pushMethodRedirectArgumentsOnStack(name, desc);

            // Stack : <receiver>
            //      <array of parameter_values>
            //      <array of parameter_types>
            //      <method_name>
            invokeStatic(RUNTIME_TYPE, Method.getMethod(
                    "Object invokeProtectedMethod(Object, Object[], Class[], String)"));
            // Stack : <return value or null if no return value>
            handleReturnType(desc);
            return true;
        }

        private boolean handleStaticOpcode(String owner, String name, String desc, boolean itf) {

            if (DEBUG) {
                System.out.println(
                        "Static Method : " + name + ":" + desc + ":" + itf + ":" + isStatic);

            }
            AccessRight accessRight = getMethodAccessRight(owner, name, desc);
            if (accessRight == AccessRight.PUBLIC) {
                return false;
            }

            // for anything else, private, protected and package private, we must go through
            // reflection.

            // stack: <param_1>
            //      <param_2>
            //      ...
            //      <param_n>
            pushMethodRedirectArgumentsOnStack(name, desc);

            // push the class implementing the original static method
            visitLdcInsn(Type.getType("L" + owner + ";"));

            // stack: <boxed method parameter>
            //      <target parameter types>
            //      <target method name>
            //      <target class name>
            invokeStatic(RUNTIME_TYPE, Method.getMethod(
                    "Object invokeProtectedStaticMethod(Object[], Class[], String, Class)"));
            // stack : method return value or null if the method was VOID.
            handleReturnType(desc);
            return true;
        }

        @Override
        public void visitTypeInsn(int opcode, String s) {
            if (opcode == Opcodes.NEW) {
                // state can only normal or dup_after new
                if (state == MachineState.AFTER_NEW) {
                    throw new RuntimeException("Panic, two NEW opcode without a DUP");
                }

                if (isInSamePackage(s)) {
                    // this is a new allocation in the same package, this could be protected or
                    // package private class, we must go through reflection, otherwise not.
                    // set our state so we swallow the next DUP we encounter.
                    state = MachineState.AFTER_NEW;

                    // swallow the NEW, we will also swallow the DUP associated with the new
                    return;
                }
            }
            super.visitTypeInsn(opcode, s);
        }

        @Override
        public void visitInsn(int opcode) {
            // check the last object allocation we encountered, if this is in the same package
            // we need to go through reflection and should therefore remove the DUP, otherwise
            // we leave it.
            if (opcode == Opcodes.DUP && state == MachineState.AFTER_NEW) {

                state = MachineState.NORMAL;
                return;
            }
            super.visitInsn(opcode);
        }

        private boolean handleConstructor(String owner, String name, String desc) {

            if (isInSamePackage(owner)) {

                Type expectedType = Type.getType("L" + owner + ";");
                pushMethodRedirectArgumentsOnStack(name, desc);

                // pop the name, we don't need it.
                pop();
                visitLdcInsn(expectedType);

                invokeStatic(RUNTIME_TYPE, Method.getMethod(
                        "Object newForClass(Object[], Class[], Class)"));

                checkCast(expectedType);
                unbox(expectedType);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void visitEnd() {
            if (DEBUG) {
                System.out.println("Method visit end");
            }
        }

        /**
         * Returns the actual method access right or a best guess if we don't have access to the
         * method definition.
         * @param owner the method owner class
         * @param name the method name
         * @param desc the method signature
         * @return the {@link AccessRight} for that method.
         */
        private AccessRight getMethodAccessRight(String owner, String name, String desc) {
            AccessRight accessRight;
            if (owner.equals(visitedClassName)) {
                MethodNode methodByName = getMethodByName(name, desc);
                if (methodByName == null) {
                    // we did not find the method invoked on ourselves, which mean that it really
                    // is a parent class method invocation and we just don't have access to it.
                    // the most restrictive access right in that case is protected.
                    return AccessRight.PROTECTED;
                }
                accessRight = AccessRight.fromNodeAccess(methodByName.access);
            } else {
                accessRight = guessAccessRight(owner);
            }
            return accessRight;
        }

        /**
         * Push arguments necessary to invoke one of the method redirect function :
         * <ul>{@link GenericInstantRuntime#invokeProtectedMethod(Object, Object[], Class[], String)}</ul>
         * <ul>{@link GenericInstantRuntime#invokeProtectedStaticMethod(Object[], Class[], String, Class)}</ul>
         *
         * This function will only push on the stack the three common arguments :
         *      Object[] the boxed parameter values
         *      Class[] the parameter types
         *      String the original method name
         *
         * Stack before :
         *          <param1>
         *          <param2>
         *          ...
         *          <paramN>
         * Stack After :
         *          <array of parameters>
         *          <array of parameter types>
         *          <method name>
         * @param name the original method name.
         * @param desc the original method signature.
         */
        private void pushMethodRedirectArgumentsOnStack(String name, String desc) {
            Type[] parameterTypes = Type.getArgumentTypes(desc);

            // stack : <parameters values>
            int parameters = boxParametersToNewLocalArray(parameterTypes);
            // push the parameter values as a Object[] on the stack.
            loadLocal(parameters);

            // push the parameter types as a Class[] on the stack
            pushParameterTypesOnStack(parameterTypes);

            push(name);
        }

        /**
         * Creates an array of {@link Class} objects with the same size of the array of the passed
         * parameter types. For each parameter type, stores its {@link Class} object into the
         * result array. For intrinsic types which are not present in the class constant pool, just
         * push the actual {@link Type} object on the stack and let ASM do the rest. For non
         * intrinsic type use a {@link MethodVisitor#visitLdcInsn(Object)} to ensure the
         * referenced class's presence in this class constant pool.
         *
         * Stack Before : nothing of interest
         * Stack After : <array of {@link Class}>
         *
         * @param parameterTypes a method list of parameters.
         */
        private void pushParameterTypesOnStack(Type[] parameterTypes) {
            push(parameterTypes.length);
            newArray(Type.getType(Class.class));

            for (int i = 0; i < parameterTypes.length; i++) {
                dup();
                push(i);
                switch(parameterTypes[i].getSort()) {
                    case Type.OBJECT:
                    case Type.ARRAY:
                        visitLdcInsn(parameterTypes[i]);
                        break;
                    case Type.BOOLEAN:
                    case Type.CHAR:
                    case Type.BYTE:
                    case Type.SHORT:
                    case Type.INT:
                    case Type.LONG:
                    case Type.FLOAT:
                    case Type.DOUBLE:
                        push(parameterTypes[i]);
                        break;
                    default:
                        throw new RuntimeException(
                                "Unexpected parameter type " + parameterTypes[i]);

                }
                arrayStore(Type.getType(Class.class));
            }
        }

        /**
         * Handle method return logic.
         * @param desc the method signature
         */
        private void handleReturnType(String desc) {
            Type ret = Type.getReturnType(desc);
            if (ret.getSort() == Type.VOID) {
                pop();
            } else {
                unbox(ret);
            }
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

        @SuppressWarnings("unchecked") List<MethodNode> methods = classNode.methods;
        for (MethodNode methodNode : methods) {
            if (methodNode.name.equals("<clinit>") || methodNode.name.equals("<init>")) {
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

    /**
     * A method or field in the passed owner class is accessed from the visited class.
     *
     * Returns the safest (meaning the most restrictive) {@link AccessRight} this field or method
     * can have considering the calling class.
     *
     * Since it is originally being called from the visited class, it cannot be a private field or
     * method.
     *
     * If the visited class name and the method or field owner class are not in the same package,
     * the method or field cannot be package private.
     *
     * If the method or field owner is not a super class of the visited class name, the method or
     * field cannot be protected.
     *
     * @param owner owner class of a field or method being accessed from the visited class
     * @return the most restrictive {@link AccessRight} the field or method can have.
     */
    private AccessRight guessAccessRight(String owner) {
        return !isInSamePackage(owner) && !isAnAncestor(owner)
                ? AccessRight.PUBLIC
                : AccessRight.PACKAGE_PRIVATE;
    }

    /**
     * Returns true if the passed class name is in the same package as the visited class.
     *
     * @param className a / separated class name.
     * @return true if className and visited class are in the same java package.
     */
    private boolean isInSamePackage(@NonNull String className) {
        return visitedClassName.substring(0, visitedClassName.lastIndexOf('/')).equals(
                className.substring(0, className.lastIndexOf('/')));
    }

    /**
     * Returns true if the passed class name is an ancestor of the visited class.
     *
     * @param className a / separated class name
     * @return true if it is an ancestor, false otherwise.
     */
    private boolean isAnAncestor(@NonNull String className) {
        for (ClassNode parentNode : parentNodes) {
            if (parentNode.name.equals(className)) {
                return true;
            }
        }
        return false;
    }
}
