/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;

import java.util.Arrays;
import java.util.List;

/**
 * Looks for usages of {@link Math} methods which can be replaced with
 * {@code android.util.FloatMath} methods to avoid casting.
 */
public class MathDetector extends Detector implements JavaPsiScanner {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "FloatMath", //$NON-NLS-1$
            "Using `FloatMath` instead of `Math`",

            "In older versions of Android, using `android.util.FloatMath` was recommended " +
            "for performance reasons when operating on floats. However, on modern hardware " +
            "doubles are just as fast as float (though they take more memory), and in " +
            "recent versions of Android, `FloatMath` is actually slower than using `java.lang.Math` " +
            "due to the way the JIT optimizes `java.lang.Math`. Therefore, you should use " +
            "`Math` instead of `FloatMath` if you are only targeting Froyo and above.",

            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    MathDetector.class,
                    Scope.JAVA_FILE_SCOPE))
            .addMoreInfo(
            "http://developer.android.com/guide/practices/design/performance.html#avoidfloat"); //$NON-NLS-1$

    /** Constructs a new {@link MathDetector} check */
    public MathDetector() {
    }

    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "sin",   //$NON-NLS-1$
                "cos",   //$NON-NLS-1$
                "ceil",  //$NON-NLS-1$
                "sqrt",  //$NON-NLS-1$
                "floor"  //$NON-NLS-1$
        );
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression call, @NonNull PsiMethod method) {
        if (context.getEvaluator().isMemberInClass(method, "android.util.FloatMath")
                    && context.getProject().getMinSdk() >= 8) {
            String message = String.format(
                    "Use `java.lang.Math#%1$s` instead of `android.util.FloatMath#%1$s()` " +
                            "since it is faster as of API 8", method.getName());
            PsiReferenceExpression expression = call.getMethodExpression();
            PsiElement operand = expression.getQualifier();
            Location location = operand != null && expression.getReferenceNameElement() != null
                    ? context.getRangeLocation(operand, 0, expression.getReferenceNameElement(), 0)
                    : context.getLocation(call);
            context.report(ISSUE, call, location, message);
        }
    }
}
