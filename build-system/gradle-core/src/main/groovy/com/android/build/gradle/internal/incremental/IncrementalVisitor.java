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
import com.google.common.base.Joiner;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IncrementalVisitor extends ClassVisitor {

    protected static boolean TRACING_ENABLED = Boolean.getBoolean("FDR_TRACING");

    protected String visitedClassName;
    protected String visitedSuperName;
    @NonNull
    protected final ClassNode classNode;
    @NonNull
    protected final List<ClassNode> parentNodes;

    public IncrementalVisitor(@NonNull ClassNode classNode, List<ClassNode> parentNodes, ClassVisitor classVisitor) {
        super(Opcodes.ASM5, classVisitor);
        this.classNode = classNode;
        this.parentNodes = parentNodes;
    }

    @Nullable
    FieldNode getFieldByName(String fieldName) {
        FieldNode fieldNode = getFieldByNameInClass(fieldName, classNode);
        Iterator<ClassNode> iterator = parentNodes.iterator();
        while(fieldNode == null && iterator.hasNext()) {
            ClassNode parentNode = iterator.next();
            fieldNode = getFieldByNameInClass(fieldName, parentNode);
        }
        return fieldNode;
    }

    FieldNode getFieldByNameInClass(String fieldName, ClassNode classNode) {
        List<FieldNode> fields = classNode.fields;
        for (FieldNode field: fields) {
            if (field.name.equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    MethodNode getMethodByName(String methodName, String desc) {
        MethodNode methodNode = getMethodByNameInClass(methodName, desc, classNode);
        Iterator<ClassNode> iterator = parentNodes.iterator();
        while(methodNode == null && iterator.hasNext()) {
            ClassNode parentNode = iterator.next();
            methodNode = getMethodByNameInClass(methodName, desc, parentNode);
        }
        return methodNode;
    }

    MethodNode getMethodByNameInClass(String methodName, String desc, ClassNode classNode) {
        List<MethodNode> methods = classNode.methods;
        for (MethodNode method : methods) {
            if (method.name.equals(methodName) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    protected void trace(GeneratorAdapter mv, String s) {
        mv.push(s);
        mv.invokeStatic(Type.getType(IncrementalSupportRuntime.class),
                Method.getMethod("void trace(String)"));
    }

    protected void trace(GeneratorAdapter mv, String s1, String s2) {
        mv.push(s1);
        mv.push(s2);
        mv.invokeStatic(Type.getType(IncrementalSupportRuntime.class),
                Method.getMethod("void trace(String, String)"));
    }

    protected void trace(GeneratorAdapter mv, String s1, String s2, String s3) {
        mv.push(s1);
        mv.push(s2);
        mv.push(s3);
        mv.invokeStatic(Type.getType(IncrementalSupportRuntime.class),
                Method.getMethod("void trace(String, String, String)"));
    }

    protected void trace(GeneratorAdapter mv, int argsNumber) {
        StringBuilder methodSignture = new StringBuilder("void trace(String");
        for (int i=0 ; i < argsNumber-1; i++) {
            methodSignture.append(", String");
        }
        methodSignture.append(")");
        mv.invokeStatic(Type.getType(IncrementalSupportRuntime.class),
                Method.getMethod(methodSignture.toString()));
    }
}
