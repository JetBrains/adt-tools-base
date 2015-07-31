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
 *
 * To redirect the method call, we box all the parameters into a array of {@link Object} and invoke
 * the {@link IncrementalSupportRuntime#dispatch(Object, String, String, Object...)} method.
 *
 */
public class IncrementalSupportVisitor extends ClassVisitor {

    private String visitedClassName;

    public IncrementalSupportVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM5, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        System.out.println("Visiting " + name);
        visitedClassName = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        System.out.println("Visiting method " + name + " signature " + desc);
        MethodVisitor defaultVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        if (name.equals("<init>") || name.equals("<clinit>")) {
            return defaultVisitor;
        }
        return new ISMethodVisitor(Opcodes.ASM5, defaultVisitor, access, name, desc);
    }

    private class ISMethodVisitor extends GeneratorAdapter {

        private final MethodVisitor mv;
        private final String name;
        private final String signature;
        private final java.lang.reflect.Method dispatch;

        public ISMethodVisitor(int api, MethodVisitor mv, int access,  String name, String desc) {
            super(api, mv, access, name, desc);
            this.mv = mv;
            this.name = name;
            this.signature = desc;
            try {
                dispatch = IncrementalSupportRuntime.class.getDeclaredMethod("dispatch",
                        Object.class, String.class, String.class, Object[].class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void visitCode() {
            // code to check if a new implementation of the current class is available.
            Type isrType = Type.getType(IncrementalSupportRuntime.class);
            getStatic(isrType, "INSTANCE", isrType);
            push(visitedClassName);
            invokeVirtual(isrType, Method.getMethod("boolean isPatched(String)"));
            Label l0 = newLabel();
            ifZCmp(EQ, l0);

            // we will delegate the call to the dispatch method.
            // first put the "this" reference as the first parameter
            // then the method name, the method signature and box all parameters in an array of
            // objects.
            loadThis();
            push(name);
            push(signature);

            org.objectweb.asm.Type[] callingMethodArgs = org.objectweb.asm.Type
                    .getArgumentTypes(signature);

            // create an array to hold all the passed parameters.
            int parameterNumber = callingMethodArgs.length;
            push(parameterNumber);
            newArray(Type.getType(Object.class));

            // for each parameter, box it if necessary, and store it at the right index in the
            // array we just created.
            for (int index = 0; index < callingMethodArgs.length; index++) {
                Type callingParameter = callingMethodArgs[index];
                dup();
                push(index);
                mv.visitVarInsn(callingParameter.getOpcode(Opcodes.ILOAD), index + 1);
                box(callingParameter);
                arrayStore(Type.getType(Object.class));
            }
            // now invoke the generic dispatch method.
            invokeStatic(isrType, Method.getMethod(dispatch));

            // cast the return type, or unbox into a primitive type if necessary.
            org.objectweb.asm.Type returnType = Type.getReturnType(signature);
            if (returnType.getSort() != Type.VOID) {
                unbox(returnType);
            } else {
                pop();
            }
            returnValue();

            // jump label for classes without any new implementation, just invoke the original
            // method implementation.
            visitLabel(l0);
            super.visitCode();
        }
    }
}
