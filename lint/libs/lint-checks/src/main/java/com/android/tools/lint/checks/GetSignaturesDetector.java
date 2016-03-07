/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;

import java.util.Collections;
import java.util.List;

public class GetSignaturesDetector extends Detector implements JavaPsiScanner  {
    public static final Issue ISSUE = Issue.create(
            "PackageManagerGetSignatures", //$NON-NLS-1$
            "Potential Multiple Certificate Exploit",
            "Improper validation of app signatures could lead to issues where a malicious app " +
            "submits itself to the Play Store with both its real certificate and a fake " +
            "certificate and gains access to functionality or information it shouldn't " +
            "have due to another application only checking for the fake certificate and " +
            "ignoring the rest. Please make sure to validate all signatures returned " +
            "by this method.",
            Category.SECURITY,
            8,
            Severity.INFORMATIONAL,
            new Implementation(
                    GetSignaturesDetector.class,
                    Scope.JAVA_FILE_SCOPE))
            .addMoreInfo("https://bluebox.com/technical/android-fake-id-vulnerability/");

    private static final String PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"; //$NON-NLS-1$
    private static final String GET_PACKAGE_INFO = "getPackageInfo"; //$NON-NLS-1$
    private static final int GET_SIGNATURES_FLAG = 0x00000040; //$NON-NLS-1$

    // ---- Implements JavaScanner ----

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(GET_PACKAGE_INFO);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression node, @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.methodMatches(method, PACKAGE_MANAGER_CLASS, true,
                JavaParser.TYPE_STRING,
                JavaParser.TYPE_INT)) {
            return;
        }

        PsiExpression[] arguments = node.getArgumentList().getExpressions();
        if (arguments.length == 2) {
            PsiExpression second = arguments[1];
            Object number = ConstantEvaluator.evaluate(context, second);
            if (number instanceof Number) {
                int flagValue = ((Number)number).intValue();
                maybeReportIssue(flagValue, context, node, second);
            }
        }
    }

    private static void maybeReportIssue(
            int flagValue, JavaContext context, PsiMethodCallExpression node,
            PsiExpression last) {
        if ((flagValue & GET_SIGNATURES_FLAG) != 0) {
            context.report(ISSUE, node, context.getLocation(last),
                "Reading app signatures from getPackageInfo: The app signatures "
                    + "could be exploited if not validated properly; "
                    + "see issue explanation for details.");
        }
    }
}
