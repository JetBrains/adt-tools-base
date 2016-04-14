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
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.JavaParser.ResolvedField;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.ConstructorInvocation;
import lombok.ast.Expression;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.Identifier;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;

public class AllowAllHostnameVerifierDetector extends Detector implements JavaScanner {

    @SuppressWarnings("unchecked")
    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create("AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations " +
            "whose `verify` method always returns true (thus trusting any hostname) " +
            "which could result in insecure network traffic caused by trusting arbitrary " +
            "hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    // ---- Implements JavaScanner ----

    @Override
    @Nullable @SuppressWarnings("javadoc")
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("org.apache.http.conn.ssl.AllowAllHostnameVerifier");
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @Nullable AstVisitor visitor,
            @NonNull ConstructorInvocation node,
            @NonNull ResolvedMethod constructor) {
        Location location = context.getLocation(node);
        context.report(ISSUE, node, location,
                "Using the AllowAllHostnameVerifier HostnameVerifier is unsafe " +
                        "because it always returns true, which could cause insecure network " +
                        "traffic due to trusting TLS/SSL server certificates for wrong " +
                        "hostnames");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull MethodInvocation node) {
        ResolvedNode resolved = context.resolve(node);
        if (resolved instanceof ResolvedMethod) {
            ResolvedMethod method = (ResolvedMethod) resolved;
            if (method.getArgumentCount() == 1 &&
                    node.astArguments().size() == 1 &&
                    method.getArgumentType(0).matchesName("javax.net.ssl.HostnameVerifier")) {
                Expression argument = node.astArguments().first();
                ResolvedNode resolvedArgument = context.resolve(argument);
                if (resolvedArgument instanceof ResolvedField) {
                    ResolvedField field = (ResolvedField) resolvedArgument;
                    if (field.getName().equals("ALLOW_ALL_HOSTNAME_VERIFIER")) {
                        Location location = context.getLocation(argument);
                        String message = "Using the ALLOW_ALL_HOSTNAME_VERIFIER HostnameVerifier "
                                + "is unsafe because it always returns true, which could cause "
                                + "insecure network traffic due to trusting TLS/SSL server "
                                + "certificates for wrong hostnames";
                        context.report(ISSUE, argument, location, message);
                    }
                }
            }
        }
    }
}
