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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collections;
import java.util.List;

/** Detector looking for Toast.makeText() without a corresponding show() call */
public class ToastDetector extends Detector implements JavaPsiScanner {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ShowToast", //$NON-NLS-1$
            "Toast created but not shown",

            "`Toast.makeText()` creates a `Toast` but does *not* show it. You must call " +
            "`show()` on the resulting object to actually make the `Toast` appear.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    ToastDetector.class,
                    Scope.JAVA_FILE_SCOPE));


    /** Constructs a new {@link ToastDetector} check */
    public ToastDetector() {
    }

    // ---- Implements JavaScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("makeText"); //$NON-NLS-1$
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiMethodCallExpression call, @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.widget.Toast")) {
            return;
        }

        // Make sure you pass the right kind of duration: it's not a delay, it's
        //  LENGTH_SHORT or LENGTH_LONG
        // (see http://code.google.com/p/android/issues/detail?id=3655)
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 3) {
            PsiExpression duration = args[2];
            if (duration instanceof PsiLiteral) {
                context.report(ISSUE, duration, context.getLocation(duration),
                        "Expected duration `Toast.LENGTH_SHORT` or `Toast.LENGTH_LONG`, a custom " +
                        "duration value is not supported");
            }
        }

        PsiMethod surroundingMethod = PsiTreeUtil.getParentOfType(call, PsiMethod.class, true);
        if (surroundingMethod == null) {
            return;
        }

        ShowFinder finder = new ShowFinder(call);
        surroundingMethod.accept(finder);
        if (!finder.isShowCalled()) {
            context.report(ISSUE, call, context.getLocation(call.getMethodExpression()),
                    "Toast created but not shown: did you forget to call `show()` ?");
        }
    }

    private static class ShowFinder extends JavaRecursiveElementVisitor {
        /** The target makeText call */
        private final PsiMethodCallExpression mTarget;
        /** Whether we've found the show method */
        private boolean mFound;
        /** Whether we've seen the target makeText node yet */
        private boolean mSeenTarget;

        private ShowFinder(PsiMethodCallExpression target) {
            mTarget = target;
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression node) {
            super.visitMethodCallExpression(node);

            if (node == mTarget) {
                mSeenTarget = true;
            } else {
                PsiReferenceExpression methodExpression = node.getMethodExpression();
                if ((mSeenTarget || methodExpression.getQualifier() == mTarget)
                        && "show".equals(methodExpression.getReferenceName())) { //$NON-NLS-1$
                    // TODO: Do more flow analysis to see whether we're really calling show
                    // on the right type of object?
                    mFound = true;
                }
            }
        }

        @Override
        public void visitReturnStatement(PsiReturnStatement node) {
            super.visitReturnStatement(node);

            if (node.getReturnValue() == mTarget) {
                // If you just do "return Toast.makeText(...) don't warn
                mFound = true;
            }
        }

        boolean isShowCalled() {
            return mFound;
        }
    }
}
