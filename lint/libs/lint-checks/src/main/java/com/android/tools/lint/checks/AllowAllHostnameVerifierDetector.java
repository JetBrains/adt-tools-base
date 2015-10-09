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

package com.android.tools.lint.checks;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Detector.JavaScanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;

import lombok.ast.AstVisitor;
import lombok.ast.ConstructorInvocation;
import lombok.ast.Expression;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.Identifier;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.StrictListAccessor;

public class AllowAllHostnameVerifierDetector extends Detector
        implements ClassScanner, JavaScanner {
    public static final Issue ISSUE = Issue.create("AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for declaration or use of HostnameVerifier implementations " +
            "whose `verify` method always returns true (thus trusting any hostname) " +
            "which could result in insecure network traffic caused by trusting arbitrary " +
            "hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class,
                    EnumSet.of(Scope.ALL_CLASS_FILES, Scope.ALL_JAVA_FILES)));

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.SLOW;
    }

    // ---- Implements ClassScanner ----

    @SuppressWarnings("rawtypes")
    @Override
    public void checkClass(@NonNull final ClassContext context,
            @NonNull ClassNode classNode) {
        if (!classNode.interfaces.contains("javax/net/ssl/HostnameVerifier")) {
            return;
        }
        List methodList = classNode.methods;
        for (Object m : methodList) {
            MethodNode method = (MethodNode) m;
            if ("verify".equals(method.name)) {
                InsnList nodes = method.instructions;
                boolean emptyMethod = true; // Stays true if method has no instructions
                                            // other than ICONST_1 or IRETURN.
                boolean containsIconst1 = false;

                for (int i = 0, n = nodes.size(); i < n; i++) {
                    // Future work: Improve this check to be less sensitive to irrelevant
                    // instructions/statements/invocations (e.g. System.out.println).
                    AbstractInsnNode instruction = nodes.get(i);
                    int type = instruction.getType();
                    if (type != AbstractInsnNode.LABEL && type != AbstractInsnNode.LINE &&
                            !(type == AbstractInsnNode.INSN &&
                                    (instruction.getOpcode() == Opcodes.ICONST_1 ||
                                     instruction.getOpcode() == Opcodes.IRETURN))) {
                        emptyMethod = false;
                        break;
                    } else if (type == AbstractInsnNode.INSN &&
                            instruction.getOpcode() == Opcodes.ICONST_1) {
                        containsIconst1 = true;
                    }
                }
                if (containsIconst1 && emptyMethod) {
                    Location location = context.getLocation(method, classNode);
                    context.report(ISSUE, location, method.name + " always returns true, which " +
                            "could cause insecure network traffic due to trusting TLS/SSL " +
                            "server certificates for wrong hostnames");
                }
            }
        }
    }

    // ---- Implements JavaScanner ----
    //
    // Checks for calls to "new AllowAllHostnameVerifier()"
    // and use of ALLOW_ALL_HOSTNAME_VERIFIER

    @Override
    public List<Class<? extends Node>> getApplicableNodeTypes() {
        return Arrays.<Class<? extends Node>>asList(
                ConstructorInvocation.class,
                Identifier.class);
    }

    @Override
    public AstVisitor createJavaVisitor(@NonNull JavaContext context) {
        return new IdentifierVisitor(context);
    }

    private static class IdentifierVisitor extends ForwardingAstVisitor {
        private final JavaContext mContext;

        public IdentifierVisitor(JavaContext context) {
            super();
            mContext = context;
        }

        @Override
        public boolean visitConstructorInvocation(ConstructorInvocation node) {
            if ("AllowAllHostnameVerifier".equals(node.astTypeReference().getTypeName())) {
                Location location = mContext.getLocation(node);
                mContext.report(ISSUE, node, location,
                        "Using the AllowAllHostnameVerifier HostnameVerifier is unsafe " +
                        "because it always returns true, which could cause insecure network " +
                        "traffic due to trusting TLS/SSL server certificates for wrong " +
                        "hostnames");

            }
            return super.visitConstructorInvocation(node);
        }

        @Override
        public boolean visitIdentifier(Identifier node) {
            if ("ALLOW_ALL_HOSTNAME_VERIFIER".equals(node.astValue())) { //$NON-NLS-1$
                Location location = mContext.getLocation(node);
                mContext.report(ISSUE, node, location,
                        "Using the ALLOW_ALL_HOSTNAME_VERIFIER HostnameVerifier is unsafe " +
                        "because it always returns true, which could cause insecure network " +
                        "traffic due to trusting TLS/SSL server certificates for wrong " +
                        "hostnames");
                return true;
            }
            return false;
        }
    }
}
