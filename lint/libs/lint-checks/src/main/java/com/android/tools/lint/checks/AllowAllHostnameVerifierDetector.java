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
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements JavaPsiScanner {

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
    public void visitConstructor(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiNewExpression node, @NonNull PsiMethod constructor) {
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
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression node, @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.methodMatches(method, null, false, "javax.net.ssl.HostnameVerifier")) {
            PsiExpression argument = node.getArgumentList().getExpressions()[0];
            PsiElement resolvedArgument = evaluator.resolve(argument);
            if (resolvedArgument instanceof PsiField) {
                PsiField field = (PsiField) resolvedArgument;
                if ("ALLOW_ALL_HOSTNAME_VERIFIER".equals(field.getName())) {
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
