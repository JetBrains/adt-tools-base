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

import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.CLASS_VIEWGROUP;
import static com.android.tools.lint.client.api.JavaParser.TYPE_INT;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.Lists;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collections;
import java.util.List;

/**
 * Looks for ListView scrolling performance: should use view holder pattern
 */
public class ViewHolderDetector extends Detector implements JavaPsiScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            ViewHolderDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Using a view inflater unconditionally in an AdapterView */
    public static final Issue ISSUE = Issue.create(
            "ViewHolder", //$NON-NLS-1$
            "View Holder Candidates",

            "When implementing a view Adapter, you should avoid unconditionally inflating a " +
            "new layout; if an available item is passed in for reuse, you should try to " +
            "use that one instead. This helps make for example ListView scrolling much " +
            "smoother.",

            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            IMPLEMENTATION)
            .addMoreInfo(
            "http://developer.android.com/training/improving-layouts/smooth-scrolling.html#ViewHolder");

    private static final String GET_VIEW = "getView";  //$NON-NLS-1$
    static final String INFLATE = "inflate";           //$NON-NLS-1$

    /** Constructs a new {@link ViewHolderDetector} check */
    public ViewHolderDetector() {
    }

    // ---- Implements JavaScanner ----

    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Collections.<Class<? extends PsiElement>>singletonList(PsiMethod.class);
    }

    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
        return new ViewAdapterVisitor(context);
    }


    private static class ViewAdapterVisitor extends JavaElementVisitor {
        private final JavaContext mContext;

        public ViewAdapterVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitMethod(PsiMethod method) {
            if (isViewAdapterMethod(mContext, method)) {
                InflationVisitor visitor = new InflationVisitor(mContext);
                method.accept(visitor);
                visitor.finish();
            }
        }

        /**
         * Returns true if this method looks like it's overriding android.widget.Adapter's getView
         * method: getView(int position, View convertView, ViewGroup parent)
         */
        private static boolean isViewAdapterMethod(JavaContext context, PsiMethod node) {
            JavaEvaluator evaluator = context.getEvaluator();
            return GET_VIEW.equals(node.getName()) && evaluator.parametersMatch(node,
                    TYPE_INT, CLASS_VIEW, CLASS_VIEWGROUP);
        }
    }

    private static class InflationVisitor extends JavaRecursiveElementVisitor {
        private final JavaContext mContext;
        private List<PsiElement> mNodes;
        private boolean mHaveConditional;

        public InflationVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression node) {
            super.visitMethodCallExpression(node);

            PsiReferenceExpression expression = node.getMethodExpression();
            if (expression.getQualifierExpression() != null) {
                String methodName = expression.getReferenceName();
                if (INFLATE.equals(methodName)
                        && node.getArgumentList().getExpressions().length >= 1) {
                    // See if we're inside a conditional
                    boolean insideIf = false;
                    //noinspection unchecked
                    if (PsiTreeUtil.getParentOfType(node, PsiConditionalExpression.class,
                            PsiIfStatement.class, PsiSwitchStatement.class) != null) {
                        insideIf = true;
                        mHaveConditional = true;
                    }
                    if (!insideIf) {
                        // Rather than reporting immediately, we only report if we didn't
                        // find any conditionally executed inflate statements in the method.
                        // This is because there are cases where getView method is complicated
                        // and inflates not just the top level layout but also children
                        // of the view, and we don't want to flag these. (To be more accurate
                        // should perform flow analysis and only report unconditional inflation
                        // of layouts that wind up flowing to the return value; that requires
                        // more work, and this simple heuristic is good enough for nearly all test
                        // cases I've come across.
                        if (mNodes == null) {
                            mNodes = Lists.newArrayList();
                        }
                        mNodes.add(node);
                    }
                }
            }
        }

        public void finish() {
            if (!mHaveConditional && mNodes != null) {
                for (PsiElement node : mNodes) {
                    String message = "Unconditional layout inflation from view adapter: "
                            + "Should use View Holder pattern (use recycled view passed "
                            + "into this method as the second parameter) for smoother "
                            + "scrolling";
                    mContext.report(ISSUE, node, mContext.getLocation(node), message);
                }
            }
        }
    }
}
