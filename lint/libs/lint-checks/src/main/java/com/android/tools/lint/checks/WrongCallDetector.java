/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.tools.lint.checks.JavaPerformanceDetector.ON_DRAW;
import static com.android.tools.lint.checks.JavaPerformanceDetector.ON_LAYOUT;
import static com.android.tools.lint.checks.JavaPerformanceDetector.ON_MEASURE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Arrays;
import java.util.List;

/**
 * Checks for cases where the wrong call is being made
 */
public class WrongCallDetector extends Detector implements JavaPsiScanner {
    /** Calling the wrong method */
    public static final Issue ISSUE = Issue.create(
            "WrongCall", //$NON-NLS-1$
            "Using wrong draw/layout method",

            "Custom views typically need to call `measure()` on their children, not `onMeasure`. " +
            "Ditto for onDraw, onLayout, etc.",

            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    WrongCallDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    /** Constructs a new {@link WrongCallDetector} */
    public WrongCallDetector() {
    }

    // ---- Implements JavaScanner ----

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                ON_DRAW,
                ON_MEASURE,
                ON_LAYOUT
        );
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression node, @NonNull PsiMethod calledMethod) {
        // Call is only allowed if it is both only called on the super class (invoke special)
        // as well as within the same overriding method (e.g. you can't call super.onLayout
        // from the onMeasure method)
        PsiElement operand = node.getMethodExpression().getQualifier();
        if (!(operand instanceof PsiSuperExpression)) {
            report(context, node, calledMethod);
            return;
        }

        PsiMethod method = PsiTreeUtil.getParentOfType(node, PsiMethod.class, true);
        if (method != null) {
            String callName = node.getMethodExpression().getReferenceName();
            if (callName != null && !callName.equals(method.getName())) {
                report(context, node, calledMethod);
            }
        }
    }

    private static void report(
            @NonNull JavaContext context,
            @NonNull PsiMethodCallExpression node,
            @NonNull PsiMethod method) {
        // Make sure the call is on a view
        if (!context.getEvaluator().isMemberInSubClassOf(method, CLASS_VIEW, false)) {
            return;
        }

        String name = method.getName();
        String suggestion = Character.toLowerCase(name.charAt(2)) + name.substring(3);
        String message = String.format(
                // Keep in sync with {@link #getOldValue} and {@link #getNewValue} below!
                "Suspicious method call; should probably call \"`%1$s`\" rather than \"`%2$s`\"",
                suggestion, name);
        context.report(ISSUE, node, context.getNameLocation(node), message);
    }

    /**
     * Given an error message produced by this lint detector for the given issue type,
     * returns the old value to be replaced in the source code.
     * <p>
     * Intended for IDE quickfix implementations.
     *
     * @param errorMessage the error message associated with the error
     * @param format the format of the error message
     * @return the corresponding old value, or null if not recognized
     */
    @Nullable
    public static String getOldValue(@NonNull String errorMessage, @NonNull TextFormat format) {
        errorMessage = format.toText(errorMessage);
        return LintUtils.findSubstring(errorMessage, "than \"", "\"");
    }

    /**
     * Given an error message produced by this lint detector for the given issue type,
     * returns the new value to be put into the source code.
     * <p>
     * Intended for IDE quickfix implementations.
     *
     * @param errorMessage the error message associated with the error
     * @param format the format of the error message
     * @return the corresponding new value, or null if not recognized
     */
    @Nullable
    public static String getNewValue(@NonNull String errorMessage, @NonNull TextFormat format) {
        errorMessage = format.toText(errorMessage);
        return LintUtils.findSubstring(errorMessage, "call \"", "\"");
    }
}
