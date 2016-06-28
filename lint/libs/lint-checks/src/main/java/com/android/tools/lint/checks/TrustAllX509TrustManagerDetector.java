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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.detector.api.*;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Detector.JavaScanner;
import lombok.ast.*;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.ClassNode;
import org.jetbrains.org.objectweb.asm.tree.InsnList;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class TrustAllX509TrustManagerDetector extends Detector implements JavaScanner,
        ClassScanner {

    @SuppressWarnings("unchecked")
    private static final Implementation IMPLEMENTATION =
            new Implementation(TrustAllX509TrustManagerDetector.class,
                    EnumSet.of(Scope.JAVA_LIBRARIES, Scope.JAVA_FILE),
                    Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create("TrustAllX509TrustManager",
            "Insecure TLS/SSL trust manager",
            "This check looks for X509TrustManager implementations whose `checkServerTrusted` or " +
            "`checkClientTrusted` methods do nothing (thus trusting any certificate chain) " +
            "which could result in insecure network traffic caused by trusting arbitrary " +
            "TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    public TrustAllX509TrustManagerDetector() {
    }

    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.X509TrustManager");
    }

    @Override
    public void checkClass(@NonNull JavaContext context, @Nullable ClassDeclaration node,
            @NonNull Node declarationOrAnonymous, @NonNull ResolvedClass cls) {
        NormalTypeBody body;
        if (declarationOrAnonymous instanceof NormalTypeBody) {
            body = (NormalTypeBody) declarationOrAnonymous;
        } else if (node != null) {
            body = node.astBody();
        } else {
            return;
        }

        for (Node member : body.astMembers()) {
            if (member instanceof MethodDeclaration) {
                MethodDeclaration declaration = (MethodDeclaration)member;
                String methodName = declaration.astMethodName().astValue();
                if ("checkServerTrusted".equals(methodName)
                        || "checkClientTrusted".equals(methodName)) {

                    // For now very simple; only checks if nothing is done.
                    // Future work: Improve this check to be less sensitive to irrelevant
                    // instructions/statements/invocations (e.g. System.out.println) by
                    // looking for calls that could lead to a CertificateException being
                    // thrown, e.g. throw statement within the method itself or invocation
                    // of another method that may throw a CertificateException, and only
                    // reporting an issue if none of these calls are found. ControlFlowGraph
                    // may be useful here.

                    boolean complex = false;
                    for (Statement statement : declaration.astBody().astContents()) {
                        if (!(statement instanceof Return)) {
                            complex = true;
                            break;
                        }
                    }

                    if (!complex) {
                        Location location = context.getNameLocation(declaration);
                        String message = getErrorMessage(methodName);
                        context.report(ISSUE, declaration, location, message);
                    }
                }
            }
        }
    }

    @NonNull
    private static String getErrorMessage(String methodName) {
        return "`" + methodName + "` is empty, which could cause " +
                "insecure network traffic due to trusting arbitrary TLS/SSL " +
                "certificates presented by peers";
    }

    // ---- Implements ClassScanner ----
    // Only used for libraries where we have to analyze bytecode

    @Override
    @SuppressWarnings("rawtypes")
    public void checkClass(@NonNull final ClassContext context,
            @NonNull ClassNode classNode) {
        if (!context.isFromClassLibrary()) {
            // Non-library code checked at the AST level
            return;
        }
        if (!classNode.interfaces.contains("javax/net/ssl/X509TrustManager")) {
            return;
        }
        List methodList = classNode.methods;
        for (Object m : methodList) {
            MethodNode method = (MethodNode) m;
            if ("checkServerTrusted".equals(method.name) ||
                    "checkClientTrusted".equals(method.name)) {
                InsnList nodes = method.instructions;
                boolean emptyMethod = true; // Stays true if method doesn't perform any "real"
                                            // operations
                for (int i = 0, n = nodes.size(); i < n; i++) {
                    // Future work: Improve this check to be less sensitive to irrelevant
                    // instructions/statements/invocations (e.g. System.out.println) by
                    // looking for calls that could lead to a CertificateException being
                    // thrown, e.g. throw statement within the method itself or invocation
                    // of another method that may throw a CertificateException, and only
                    // reporting an issue if none of these calls are found. ControlFlowGraph
                    // may be useful here.
                    AbstractInsnNode instruction = nodes.get(i);
                    int type = instruction.getType();
                    if (type != AbstractInsnNode.LABEL && type != AbstractInsnNode.LINE &&
                            !(type == AbstractInsnNode.INSN &&
                                    instruction.getOpcode() == Opcodes.RETURN)) {
                        emptyMethod = false;
                        break;
                    }
                }
                if (emptyMethod) {
                    Location location = context.getLocation(method, classNode);
                    context.report(ISSUE, location, getErrorMessage(method.name));
                }
            }
        }
    }
}
