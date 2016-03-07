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
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiThrowStatement;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements JavaPsiScanner {

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
    public void checkClass(@NonNull JavaContext context, @NonNull PsiClass declaration) {
        JavaEvaluator evaluator = context.getEvaluator();
        for (PsiMethod method : declaration.findMethodsByName("verify", false)) {
            if (evaluator.methodMatches(method, null, false,
                    TYPE_STRING, "javax.net.ssl.SSLSession")) {
                ComplexVisitor visitor = new ComplexVisitor(context);
                declaration.accept(visitor);
                if (visitor.isComplex()) {
                    return;
                }

                Location location = context.getNameLocation(method);
                String message = String.format("`%1$s` always returns `true`, which " +
                                "could cause insecure network traffic due to trusting "
                                + "TLS/SSL server certificates for wrong hostnames",
                        method.getName());
                context.report(ISSUE, location, message);
                break;
            }
        }
    }

    private static class ComplexVisitor extends JavaRecursiveElementVisitor {
        @SuppressWarnings("unused")
        private final JavaContext mContext;
        private boolean mComplex;

        public ComplexVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            mComplex = true;
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            // TODO: Ignore certain known safe methods, e.g. Logging etc
            mComplex = true;
        }

        @Override
        public void visitReturnStatement(PsiReturnStatement node) {
            PsiExpression argument = node.getReturnValue();
            if (argument != null) {
                // TODO: Only do this if certain that there isn't some intermediate
                // assignment, as exposed by the unit test
                //Object value = ConstantEvaluator.evaluate(mContext, argument);
                //if (Boolean.TRUE.equals(value)) {
                if (LintUtils.isTrueLiteral(argument)) {
                    mComplex = false;
                } else {
                    mComplex = true; // "return false" or some complicated logic
                }
            }
            super.visitReturnStatement(node);
        }

        public boolean isComplex() {
            return mComplex;
        }
    }
}
