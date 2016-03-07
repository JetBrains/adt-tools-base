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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.Lists;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collections;
import java.util.List;

/**
 * Checks related to RecyclerView usage.
 * // https://code.google.com/p/android/issues/detail?id=172335
 */
public class RecyclerViewDetector extends Detector implements JavaPsiScanner {
    public static final Issue ISSUE = Issue.create(
            "RecyclerView", //$NON-NLS-1$
            "RecyclerView Problems",
            "`RecyclerView` will *not* call `onBindViewHolder` again when the position of " +
            "the item changes in the data set unless the item itself is " +
            "invalidated or the new position cannot be determined.\n" +
            "\n" +
            "For this reason, you should *only* use the position parameter " +
            "while acquiring the related data item inside this method, and " +
            "should *not* keep a copy of it.\n" +
            "\n" +
            "If you need the position of an item later on (e.g. in a click " +
            "listener), use `getAdapterPosition()` which will have the updated " +
            "adapter position.",
            Category.CORRECTNESS,
            8,
            Severity.WARNING,
            new Implementation(
                    RecyclerViewDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    private static final String VIEW_ADAPTER = "android.support.v7.widget.RecyclerView.Adapter"; //$NON-NLS-1$
    private static final String ON_BIND_VIEW_HOLDER = "onBindViewHolder"; //$NON-NLS-1$

    // ---- Implements JavaScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(VIEW_ADAPTER);
    }

    @Override
    public void checkClass(@NonNull JavaContext context, @NonNull PsiClass declaration) {
        JavaEvaluator evaluator = context.getEvaluator();
        for (PsiMethod method : declaration.findMethodsByName(ON_BIND_VIEW_HOLDER, false)) {
            int size = evaluator.getParameterCount(method);
            if (size == 2 || size == 3) {
                checkMethod(context, method, declaration);
            }
        }
    }

    private static void checkMethod(@NonNull JavaContext context,
            @NonNull PsiMethod declaration, @NonNull PsiClass cls) {
        PsiParameter[] parameters = declaration.getParameterList().getParameters();
        PsiParameter viewHolder = parameters[0];
        PsiParameter parameter = parameters[1];

        ParameterEscapesVisitor visitor = new ParameterEscapesVisitor(context, cls, parameter);
        declaration.accept(visitor);
        if (visitor.variableEscapes()) {
            reportError(context, viewHolder, parameter);
        }
    }

    private static void reportError(@NonNull JavaContext context, PsiParameter viewHolder,
            PsiParameter parameter) {
        String variablePrefix = viewHolder.getName();
        if (variablePrefix == null) {
            variablePrefix = "ViewHolder";
        }
        String message = String.format("Do not treat position as fixed; only use immediately "
                + "and call `%1$s.getAdapterPosition()` to look it up later",
                variablePrefix);
        context.report(ISSUE, parameter, context.getLocation(parameter),
                message);
    }

    /**
     * Determines whether a given variable "escapes" either to a field or to a nested
     * runnable. (We deliberately ignore variables that escape via method calls.)
     */
    private static class ParameterEscapesVisitor extends JavaRecursiveElementVisitor {
        protected final JavaContext mContext;
        protected final List<PsiVariable> mVariables;
        private final PsiClass mBindClass;
        private boolean mEscapes;
        private boolean mFoundInnerClass;

        public ParameterEscapesVisitor(JavaContext context,
                @NonNull PsiClass bindClass,
                @NonNull PsiParameter variable) {
            mContext = context;
            mVariables = Lists.<PsiVariable>newArrayList(variable);
            mBindClass = bindClass;
        }

        public boolean variableEscapes() {
            return mEscapes;
        }

        @Override
        public void visitLocalVariable(PsiLocalVariable variable) {
            PsiExpression initializer = variable.getInitializer();
            if (initializer instanceof PsiReference) {
                PsiElement resolved = ((PsiReference) initializer).resolve();
                //noinspection SuspiciousMethodCalls
                if (resolved != null && mVariables.contains(resolved)) {
                    if (resolved instanceof PsiLocalVariable) {
                        mVariables.add(variable);
                    } else if (resolved instanceof PsiField) {
                        mEscapes = true;
                    }
                }
            }

            super.visitLocalVariable(variable);
        }

        @Override
        public void visitAssignmentExpression(PsiAssignmentExpression node) {
            PsiExpression rhs = node.getRExpression();
            boolean clearLhs = true;
            if (rhs instanceof PsiReferenceExpression) {
                PsiElement resolved = ((PsiReferenceExpression)rhs).resolve();
                //noinspection SuspiciousMethodCalls
                if (resolved != null && mVariables.contains(resolved)) {
                    clearLhs = false;
                    PsiElement resolvedLhs = mContext.getEvaluator().resolve(node.getLExpression());
                    if (resolvedLhs instanceof PsiLocalVariable) {
                        PsiLocalVariable variable = (PsiLocalVariable) resolvedLhs;
                        mVariables.add(variable);
                    } else if (resolvedLhs instanceof PsiField) {
                        mEscapes = true;
                    }
                }
            }
            if (clearLhs) {
                // If we reassign one of the variables, clear it out
                PsiElement resolved = mContext.getEvaluator().resolve(node.getLExpression());
                //noinspection SuspiciousMethodCalls
                if (resolved != null && mVariables.contains(resolved)) {
                    //noinspection SuspiciousMethodCalls
                    mVariables.remove(resolved);
                }
            }

            super.visitAssignmentExpression(node);
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression node) {
            if (mFoundInnerClass) {
                // Check to see if this reference is inside the same class as the original
                // onBind (e.g. is this a reference from an inner class, or a reference
                // to a variable assigned from there)
                PsiElement resolved = mContext.getEvaluator().resolve(node);
                //noinspection SuspiciousMethodCalls
                if (resolved != null && mVariables.contains(resolved)) {
                    PsiClass outer = PsiTreeUtil.getParentOfType(node, PsiClass.class, true);
                    if (!mBindClass.equals(outer)) {
                        mEscapes = true;
                    }
                }
            }

            super.visitReferenceExpression(node);
        }

        @Override
        public void visitNewExpression(PsiNewExpression expression) {
            if (expression.getAnonymousClass() != null) {
                mFoundInnerClass = true;
            }

            super.visitNewExpression(expression);
        }
    }
}
