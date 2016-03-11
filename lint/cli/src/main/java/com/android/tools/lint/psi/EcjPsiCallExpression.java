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
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.ArrayAllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;

abstract class EcjPsiCallExpression extends EcjPsiExpression implements PsiCallExpression {

    private PsiExpressionList mArgumentList;

    private PsiReferenceParameterList mTypeArgumentList;

    EcjPsiCallExpression(@NonNull EcjPsiManager manager,
            @NonNull Expression expression) {
        super(manager, expression);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitCallExpression(this);
        } else {
            visitor.visitElement(this);
        }
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

    @Nullable
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
        if (mNativeNode instanceof MessageSend) {
            return mManager.findMethod(((MessageSend) mNativeNode).binding);
        } else if (mNativeNode instanceof AllocationExpression) {
            return mManager.findMethod(((AllocationExpression) mNativeNode).binding);
        } else if (mNativeNode instanceof ArrayAllocationExpression) {
            return null;
        }
        throw new IllegalArgumentException(mNativeNode.getClass().getName());
    }

    @NonNull
    @Override
    public JavaResolveResult resolveMethodGenerics() {
        throw new UnimplementedLintPsiApiException();
    }
}
