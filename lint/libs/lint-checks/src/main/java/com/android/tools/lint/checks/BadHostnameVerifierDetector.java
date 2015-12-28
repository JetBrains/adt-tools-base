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

import static com.android.tools.lint.client.api.JavaParser.TYPE_STRING;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Detector.JavaScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.util.Collections;
import java.util.List;

import lombok.ast.BooleanLiteral;
import lombok.ast.ClassDeclaration;
import lombok.ast.Expression;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.MethodDeclaration;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.NormalTypeBody;
import lombok.ast.Return;
import lombok.ast.Throw;

public class BadHostnameVerifierDetector extends Detector implements JavaScanner {

    @SuppressWarnings("unchecked")
    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create("BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` " +
            "whose `verify` method always returns true (thus trusting any hostname) " +
            "which could result in insecure network traffic caused by trusting arbitrary " +
            "hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
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
                if ("verify".equals(declaration.astMethodName().astValue())
                        && declaration.astParameters().size() == 2) {
                    ResolvedNode resolved = context.resolve(declaration);
                    if (resolved instanceof ResolvedMethod) {
                        ResolvedMethod method = (ResolvedMethod) resolved;
                        if (method.getArgumentCount() == 2
                                && method.getArgumentType(0).matchesName(TYPE_STRING)
                                && method.getArgumentType(1).matchesName("javax.net.ssl.SSLSession")) {

                            ComplexVisitor visitor = new ComplexVisitor(context);
                            declaration.accept(visitor);
                            if (visitor.isComplex()) {
                                return;
                            }

                            Location location = context.getNameLocation(declaration);
                            String message = String.format("`%1$s` always returns `true`, which " +
                                    "could cause insecure network traffic due to trusting "
                                    + "TLS/SSL server certificates for wrong hostnames",
                                    method.getName());
                            context.report(ISSUE, location, message);
                            break;
                        }
                    }
                }
            }
        }
    }

    private static class ComplexVisitor extends ForwardingAstVisitor {
        @SuppressWarnings("unused")
        private final JavaContext mContext;
        private boolean mComplex;

        public ComplexVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitThrow(Throw node) {
            mComplex = true;
            return super.visitThrow(node);
        }

        @Override
        public boolean visitMethodInvocation(MethodInvocation node) {
            // TODO: Ignore certain known safe methods, e.g. Logging etc
            mComplex = true;
            return super.visitMethodInvocation(node);
        }

        @Override
        public boolean visitReturn(Return node) {
            Expression argument = node.astValue();
            if (argument != null) {
                // TODO: Only do this if certain that there isn't some intermediate
                // assignment, as exposed by the unit test
                //Object value = ConstantEvaluator.evaluate(mContext, argument);
                //if (Boolean.TRUE.equals(value)) {
                if (argument instanceof BooleanLiteral &&
                        Boolean.TRUE.equals(((BooleanLiteral)argument).astValue())) {
                    mComplex = false;
                } else {
                    mComplex = true; // "return false" or some complicated logic
                }
            }
            return super.visitReturn(node);
        }

        public boolean isComplex() {
            return mComplex;
        }
    }
}
