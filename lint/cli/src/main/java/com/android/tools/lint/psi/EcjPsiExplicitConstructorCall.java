/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint.psi;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.Statement;

/**
 * Explicit constructor calls aren't a separate PsiType; in fact
 * {@link EcjPsiSuperExpression} and {@link EcjPsiThisExpression} are
 * explicitly <b>not</b> being used for constructor calls, ONLY for
 * other member accesses. Instead, a {@link PsiMethodCallExpression}
 * is used where the reference is a simple keyword identifier (e.g. this
 * or super.)
 * <p>
 * We cannot use {@link EcjPsiMethodCallExpression} to represent these
 * calls because those wrap expressions, and the underlying
 * {@link ExplicitConstructorCall} is a {@link Statement}, not an {@link Expression}.
 */
class EcjPsiExplicitConstructorCall extends EcjPsiSourceElement implements
        PsiMethodCallExpression {

    private PsiReferenceExpression mMethodExpression;

    private PsiExpressionList mArgumentList;

    private PsiReferenceParameterList mTypeArgumentList;

    EcjPsiExplicitConstructorCall(@NonNull EcjPsiManager manager,
            @Nullable ExplicitConstructorCall call) {
        super(manager, call);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitMethodCallExpression(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setMethodExpression(PsiReferenceExpression methodExpression) {
        mMethodExpression = methodExpression;
    }

    void setArgumentList(PsiExpressionList argumentList) {
        mArgumentList = argumentList;
    }

    void setTypeArgumentList(PsiReferenceParameterList typeArgumentList) {
        mTypeArgumentList = typeArgumentList;
    }

    @NonNull
    @Override
    public PsiReferenceParameterList getTypeArgumentList() {
        return mTypeArgumentList;
    }

    @NonNull
    @Override
    public PsiExpressionList getArgumentList() {
        return mArgumentList;
    }

    @NonNull
    @Override
    public PsiType[] getTypeArguments() {
        return getTypeArgumentList().getTypeArguments();
    }

    @Nullable
    @Override
    public PsiMethod resolveMethod() {
        return (PsiMethod)mManager.findElement(((ExplicitConstructorCall)mNativeNode).binding);
    }

    @NonNull
    @Override
    public JavaResolveResult resolveMethodGenerics() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiReferenceExpression getMethodExpression() {
        return mMethodExpression;
    }

    @Nullable
    @Override
    public PsiType getType() {
        return PsiType.VOID;
    }
}
