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
import com.android.utils.AsmUtils;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Visitor for classes that will eventually be replaceable at runtime.
 *
 * Since classes cannot be replaced in an existing class loader, we use a delegation model to
 * redirect any method implementation to the AndroidInstantRuntime.
 *
 * This redirection happens only when a new class implementation is available. A new version
 * will register itself in a static synthetic field called $change. Each method will be enhanced
 * with a piece of code to check if a new version is available by looking at the $change field
 * and redirect if necessary.
 *
 * Redirection will be achieved by calling a
 * {@link IncrementalChange#access$dispatch(String, Object...)} method.
 */
public class IncrementalSupportVisitor extends IncrementalVisitor {

    private boolean disableRedirectionForClass = false;

    private static final class VisitorBuilder implements IncrementalVisitor.VisitorBuilder {

        private VisitorBuilder() {
        }

        @NonNull
        @Override
        public IncrementalVisitor build(
                @NonNull ClassNode classNode,
                @NonNull List<ClassNode> parentNodes,
                @NonNull ClassVisitor classVisitor) {
            return new IncrementalSupportVisitor(classNode, parentNodes, classVisitor);
        }

        @Override
        @NonNull
        public String getMangledRelativeClassFilePath(@NonNull String originalClassFilePath) {
            return originalClassFilePath;
        }

        @NonNull
        @Override
        public OutputType getOutputType() {
            return OutputType.INSTRUMENT;
        }
    }

    public static final IncrementalVisitor.VisitorBuilder VISITOR_BUILDER =
            new VisitorBuilder();

    public IncrementalSupportVisitor(
            @NonNull ClassNode classNode,
            @NonNull List<ClassNode> parentNodes,
            @NonNull ClassVisitor classVisitor) {
        super(classNode, parentNodes, classVisitor);
    }

    /**
     * Ensures that the class contains a $change field used for referencing the
     * IncrementalChange dispatcher.
     * <p/>
     * Also updates package_private visiblity to public so we can call into this class from
     * outside the package.
     */
    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        visitedClassName = name;
        visitedSuperName = superName;

        super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
            "$change", getRuntimeTypeName(CHANGE_TYPE), null, null);
        AccessRight accessRight = AccessRight.fromNodeAccess(access);
        access = accessRight == AccessRight.PACKAGE_PRIVATE ? access | Opcodes.ACC_PUBLIC : access;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (desc.equals(DISABLE_ANNOTATION_TYPE.getDescriptor())) {
            disableRedirectionForClass = true;
        }
        return super.visitAnnotation(desc, visible);
    }

    /**
     * Insert Constructor specific logic({@link ConstructorArgsRedirection} and
     * {@link ConstructorDelegationDetector}) for constructor redirecting or
     * normal method redirecting ({@link MethodRedirection}) for other methods.
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {

        MethodVisitor defaultVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        MethodNode method = getMethodByNameInClass(name, desc, classNode);
        if (disableRedirectionForClass || !isAccessCompatibleWithInstantRun(access)
                || name.equals(AsmUtils.CLASS_INITIALIZER)) {
            return defaultVisitor;
        } else {
            ISMethodVisitor mv = new ISMethodVisitor(defaultVisitor, access, name, desc);
            if (name.equals(AsmUtils.CONSTRUCTOR)) {

                ConstructorDelegationDetector.Constructor constructor =
                        ConstructorDelegationDetector.deconstruct(visitedClassName, method);
                Label start = new Label();
                Label after = new Label();
                method.instructions.insert(constructor.loadThis, new LabelNode(start));
                method.instructions.insert(constructor.delegation, new LabelNode(after));

                mv.addRedirection(
                        start,
                        new ConstructorArgsRedirection(
                            visitedClassName,
                            constructor.args.name + "." + constructor.args.desc,
                            after,
                            Type.getArgumentTypes(constructor.delegation.desc)));

                mv.addRedirection(after, new MethodRedirection(constructor.body.name + "."
                        + constructor.body.desc, Type.getReturnType(desc)));
                method.accept(mv);
                return null;
            } else {
                mv.addRedirection(mv.getStartLabel(), new MethodRedirection(name + "."
                        + desc, Type.getReturnType(desc)));
                visit(method, mv, defaultVisitor);
                return null;
            }
        }
    }

    /**
     * Creates a recording {@link MethodNode} that will record all the events passed to it and
     * delegates to {@link InstantRunMethodVerifier.VerifierMethodVisitor} to check for any use
     * of a blacklisted API.
     *
     * If the method is using a blacklisted API, the default visitor will be invoked to replay
     * all the recorded method events, otherwise, the instrumentation visitor will be invoked
     *
     * This will in effect prevent or allow a method to be hot swap able.
     *
     * @param method the method to check for any blacklisted API use.
     * @param instrumentationVisitor the instrumentation visitor which will add redirection, etc.
     * @param defaultVisitor the default visitor that basically copies the method unchanged.
     */
    public void visit(final MethodNode method,
            final MethodVisitor instrumentationVisitor,
            final MethodVisitor defaultVisitor) {

        MethodNode methodNode = new InstantRunMethodVerifier.VerifierMethodVisitor(method) {
            @Override
            public void visitEnd()
            {
                if (incompatibleChange.isPresent()) {
                    System.out.println("in " + classNode.name + ", method " + method.name + " "
                            + method.desc + " is disabled " + incompatibleChange);
                    accept(defaultVisitor);
                } else {
                    accept(instrumentationVisitor);
                }
                super.visitEnd();
            }
        };
        method.accept(methodNode);
    }

    private class ISMethodVisitor extends GeneratorAdapter {

        private boolean disableRedirection = false;
        private int change;
        private final List<Type> args;
        private final Map<Label, Redirection> redirections;
        private final Label start;

        public ISMethodVisitor(MethodVisitor mv, int access,  String name, String desc) {
            super(Opcodes.ASM5, mv, access, name, desc);
            this.change = -1;
            this.redirections = new HashMap<Label, Redirection>();
            this.args = new ArrayList<Type>(Arrays.asList(Type.getArgumentTypes(desc)));
            this.start = new Label();
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            // if this is not a static, we add a fictional first parameter what will contain the "this"
            // reference which can be loaded with ILOAD_0 bytecode.
            if (!isStatic) {
                args.add(0, Type.getType(Object.class));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.equals(DISABLE_ANNOTATION_TYPE.getDescriptor())) {
                disableRedirection = true;
            }
            return super.visitAnnotation(desc, visible);
        }

        /**
         * inserts a new local '$change' in each method that contains a reference to the type's
         * IncrementalChange dispatcher, this is done to avoid threading issues.
         * <p/>
         * Pseudo code:
         * <code>
         *   $package/IncrementalChange $local1 = $className$.$change;
         * </code>
         */
        @Override
        public void visitCode() {
            if (!disableRedirection) {
                super.visitLabel(start);
                // A special line number to mark this area of code.
                super.visitLineNumber(0, start);
                change = newLocal(CHANGE_TYPE);
                visitFieldInsn(Opcodes.GETSTATIC, visitedClassName, "$change",
                        getRuntimeTypeName(CHANGE_TYPE));
                storeLocal(change);

                redirectAt(start);
            }
            super.visitCode();
        }

        @Override
        public void visitLabel(Label label) {
            super.visitLabel(label);
            redirectAt(label);
        }

        private void redirectAt(Label label) {
            if (disableRedirection) return;
            Redirection redirection = redirections.get(label);
            if (redirection != null) {
                redirection.redirect(this, change, args);
            }
        }

        public void addRedirection(@NonNull Label at, @NonNull Redirection redirection) {
            redirections.put(at, redirection);
        }


        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start,
                Label end, int index) {
            // In dex format, the argument names are separated from the local variable names. It
            // seems to be needed to declare the local argument variables from the beginning of
            // the methods for dex to pick that up. By inserting code before the first label we
            // break that. In Java this is fine, and the debugger shows the right thing. However
            // if we don't readjust the local variables, we just don't see the arguments.
            if (!disableRedirection && index < args.size()) {
                start = this.start;
            }
            super.visitLocalVariable(name, desc, signature, start, end, index);
        }

        public Label getStartLabel() {
            return start;
        }
    }

    /***
     * Inserts a trampoline to this class so that the updated methods can make calls to super
     * class methods.
     * <p/>
     * Pseudo code for this trampoline:
     * <code>
     *   Object access$super($classType instance, String name, object[] args) {
     *      if (name.equals(
     *          "firstMethod.(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;")) {
     *        return super~instance.firstMethod((String)arg[0], arg[1]);
     *      }
     *      if (name.equals("secondMethod.(Ljava/lang/String;I)V")) {
     *        super~instance.secondMethod((String)arg[0], (int)arg[1]);
     *        return;
     *      }
     *      ...
     *      StringBuilder $local1 = new StringBuilder();
     *      $local1.append("Method not found ");
     *      $local1.append(name);
     *      $local1.append(" in " $classType $super implementation");
     *      throw new $package/InstantReloadException($local1.toString());
     *   }
     * </code>
     */
    private void createAccessSuper() {
        int access = Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_VARARGS;
        Method m = new Method("access$super", "(L" + visitedClassName
                + ";Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
        MethodVisitor visitor = super.visitMethod(access,
                m.getName(),
                m.getDescriptor(),
                null, null);

        GeneratorAdapter mv = new GeneratorAdapter(access, m, visitor);

        // Gather all methods from itself and its superclasses to generate a giant access$super
        // implementation.
        // This will work fine as long as we don't support adding methods to a class.
        Map<String, MethodNode> uniqueMethods = new HashMap<String, MethodNode>();
        if (parentNodes.isEmpty()) {
            // if we cannot determine the parents for this class, let's blindly add all the
            // method of the current class as a gateway to a possible parent version.
            addAllNewMethods(uniqueMethods, classNode);
        } else {
            // otherwise, use the parent list.
            for (ClassNode parentNode : parentNodes) {
                addAllNewMethods(uniqueMethods, parentNode);
            }
        }
        for (MethodNode methodNode : uniqueMethods.values()) {
            if (methodNode.name.equals(AsmUtils.CONSTRUCTOR) || methodNode.name.equals("<clinit>")) {
                continue;
            }
            if (TRACING_ENABLED) {
                trace(mv, "testing super for ", methodNode.name);
            }
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitLdcInsn(methodNode.name + "." + methodNode.desc);
            if (TRACING_ENABLED) {
                mv.push(methodNode.name + "." + methodNode.desc);
                mv.push("==");
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                trace(mv, 3);
            }
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
                    "(Ljava/lang/Object;)Z", false);
            Label l0 = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, l0);
            mv.visitVarInsn(Opcodes.ALOAD, 0);

            Type[] args = Type.getArgumentTypes(methodNode.desc);
            int argc = 0;
            for (Type t : args) {
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.push(argc);
                mv.visitInsn(Opcodes.AALOAD);
                mv.unbox(t);
                argc++;
            }

            if (TRACING_ENABLED) {
                trace(mv, "super selected ", methodNode.name, methodNode.desc);
            }
            // Call super on the other object, yup this works cos we are on the right place to call from.
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, visitedSuperName, methodNode.name,
                    methodNode.desc, false);

            Type ret = Type.getReturnType(methodNode.desc);
            if (ret.getSort() == Type.VOID) {
                mv.visitInsn(Opcodes.ACONST_NULL);
            } else {
                mv.box(ret);
            }
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitLabel(l0);
        }


        // we could not find the method to invoke, prepare an exception to be thrown.
        mv.newInstance(Type.getType(StringBuilder.class));
        mv.dup();
        mv.invokeConstructor(Type.getType(StringBuilder.class), Method.getMethod("void <init>()V"));

        // create a meaningful message
        mv.push("Method not found ");
        mv.invokeVirtual(Type.getType(StringBuilder.class),
                Method.getMethod("StringBuilder append (String)"));
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.invokeVirtual(Type.getType(StringBuilder.class),
                Method.getMethod("StringBuilder append (String)"));
        mv.push(" in " + visitedClassName + "$super implementation");
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
    }

    /***
     * Inserts a trampoline to this class so that the updated methods can make calls to
     * constructors.
     *
     * <p/>
     * Pseudo code for this trampoline:
     * <code>
     *   ClassName(Object[] args, Marker unused) {
     *      String name = (String) args[0];
     *      if (name.equals(
     *          "java/lang/ClassName.(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;")) {
     *        this((String)arg[1], arg[2]);
     *        return
     *      }
     *      if (name.equals("SuperClassName.(Ljava/lang/String;I)V")) {
     *        super((String)arg[1], (int)arg[2]);
     *        return;
     *      }
     *      ...
     *      StringBuilder $local1 = new StringBuilder();
     *      $local1.append("Method not found ");
     *      $local1.append(name);
     *      $local1.append(" in " $classType $super implementation");
     *      throw new $package/InstantReloadException($local1.toString());
     *   }
     * </code>
     */
    private void createDispatchingThis() {
        // Gather all methods from itself and its superclasses to generate a giant constructor
        // implementation.
        // This will work fine as long as we don't support adding constructors to classes.
        Map<String, MethodNode> uniqueMethods = new HashMap<String, MethodNode>();

        addAllNewConstructors(uniqueMethods, classNode);
        for (ClassNode parentNode : parentNodes) {
            addAllNewConstructors(uniqueMethods, parentNode);
        }

        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;

        Method m = new Method(AsmUtils.CONSTRUCTOR, ConstructorArgsRedirection.DISPATCHING_THIS_SIGNATURE);
        MethodVisitor visitor = super.visitMethod(0, m.getName(), m.getDescriptor(), null, null);
        GeneratorAdapter mv = new GeneratorAdapter(access, m, visitor);

        mv.visitCode();

        // Get and store the constructor canonical name.
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.push(0);
        mv.visitInsn(Opcodes.AALOAD);
        mv.unbox(Type.getType("Ljava/lang/String;"));
        int constructorCanonicalName = mv.newLocal(Type.getType("Ljava/lang/String;"));
        mv.storeLocal(constructorCanonicalName);

        mv.visitVarInsn(Opcodes.ALOAD, 0);

        for (String canonicalName : uniqueMethods.keySet()) {
            MethodNode methodNode = uniqueMethods.get(canonicalName);
            String[] parts = canonicalName.split("\\.");
            String owner = parts[0];
            if (!owner.equals(visitedClassName) && !owner.equals(visitedSuperName)) {
                continue;
            }

            mv.loadLocal(constructorCanonicalName);
            mv.push(canonicalName);

            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
                    "(Ljava/lang/Object;)Z", false);
            Label l0 = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, l0);
            mv.visitVarInsn(Opcodes.ALOAD, 0);

            // Parse method arguments and
            Type[] args = Type.getArgumentTypes(methodNode.desc);
            int argc = 0;
            for (Type t : args) {
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.push(argc + 1);
                mv.visitInsn(Opcodes.AALOAD);
                mv.unbox(t);
                argc++;
            }

            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, AsmUtils.CONSTRUCTOR,
                    methodNode.desc, false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitLabel(l0);
        }

        // we could not find the method to invoke, prepare an exception to be thrown.
        mv.newInstance(Type.getType(StringBuilder.class));
        mv.dup();
        mv.invokeConstructor(Type.getType(StringBuilder.class), Method.getMethod("void <init>()V"));

        // create a meaningful message
        mv.push("Constructor not found ");
        mv.invokeVirtual(Type.getType(StringBuilder.class),
                Method.getMethod("StringBuilder append (String)"));
        mv.loadLocal(constructorCanonicalName);
        mv.invokeVirtual(Type.getType(StringBuilder.class),
                Method.getMethod("StringBuilder append (String)"));
        mv.push(" in " + visitedClassName + " or super class implementation");
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

        mv.visitMaxs(1, 3);
        mv.visitEnd();
    }

    @Override
    public void visitEnd() {
        createAccessSuper();
        createDispatchingThis();
        super.visitEnd();
    }

    /**
     * Add all unseen methods from the passed ClassNode's methods. {@see ClassNode#methods}
     * @param methods the methods already encountered in the ClassNode hierarchy
     * @param classNode the class to save all new methods from.
     */
    private static void addAllNewMethods(Map<String, MethodNode> methods, ClassNode classNode) {
        //noinspection unchecked
        for (MethodNode method : (List<MethodNode>) classNode.methods) {
            if (isAccessCompatibleWithInstantRun(method.access)
                    && !methods.containsKey(method.name + method.desc) &&
                    (method.access & Opcodes.ACC_STATIC) == 0 &&
                    (method.access & Opcodes.ACC_PRIVATE) == 0) {
                methods.put(method.name + method.desc, method);
            }
        }
    }

    /**
     * Add all constructors from the passed ClassNode's methods. {@see ClassNode#methods}
     * @param methods the constructors already encountered in the ClassNode hierarchy
     * @param classNode the class to save all new methods from.
     */
    private static void addAllNewConstructors(Map<String, MethodNode> methods, ClassNode classNode) {
        //noinspection unchecked
        for (MethodNode method : (List<MethodNode>) classNode.methods) {
            if (!method.name.equals(AsmUtils.CONSTRUCTOR)) {
                continue;
            }

            if (!isAccessCompatibleWithInstantRun(method.access)) {
                continue;
            }
            String key = classNode.name + "." + method.desc;
            if (methods.containsKey(key)) {
                continue;
            }
            methods.put(key, method);
        }
    }



    /**
     * Command line invocation entry point. Expects 2 parameters, first is the source directory
     * with .class files as produced by the Java compiler, second is the output directory where to
     * store the bytecode enhanced version.
     * @param args the command line arguments.
     * @throws IOException if some files cannot be read or written.
     */
    public static void main(String[] args) throws IOException {
        IncrementalVisitor.main(args, VISITOR_BUILDER);
    }
}
