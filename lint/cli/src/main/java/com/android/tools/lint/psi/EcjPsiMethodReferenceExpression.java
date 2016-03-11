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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;

import org.eclipse.jdt.internal.compiler.ast.ReferenceExpression;

class EcjPsiMethodReferenceExpression extends EcjPsiFunctionalExpression implements
        PsiMethodReferenceExpression {

    private final ReferenceExpression mReferenceExp;

    private PsiExpression mQualifier;

    private PsiIdentifier mNameIdentifier;

    private PsiReferenceParameterList mParameterList;

    EcjPsiMethodReferenceExpression(@NonNull EcjPsiManager manager,
            @NonNull ReferenceExpression expression) {
        super(manager, expression);
        mReferenceExp = expression;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitMethodReferenceExpression(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    void setQualifier(PsiExpression qualifier) {
        mQualifier = qualifier;
    }

    void setReferenceNameElement(PsiIdentifier identifier) {
        mNameIdentifier = identifier;
    }

    void setParameterList(PsiReferenceParameterList parameterList) {
        mParameterList = parameterList;
    }

    @Nullable
    @Override
    public PsiTypeElement getQualifierType() {
        PsiElement qualifier = getQualifier();
        return qualifier instanceof PsiTypeElement ? (PsiTypeElement)qualifier : null;
    }

    @Nullable
    @Override
    public PsiExpression getQualifierExpression() {
        return mQualifier;
    }

    @Override
    public boolean isExact() {
        return mReferenceExp.isExactMethodReference();
    }

    @Nullable
    @Override
    public PsiMember getPotentiallyApplicableMember() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean isConstructor() {
        return mReferenceExp.isConstructorReference();
    }

    @Nullable
    @Override
    public PsiElement getReferenceNameElement() {
        return mNameIdentifier;
    }

    @Nullable
    @Override
    public PsiReferenceParameterList getParameterList() {
        return mParameterList;
    }

    @NonNull
    @Override
    public PsiType[] getTypeParameters() {
        return mParameterList != null ? mParameterList.getTypeArguments() : PsiType.EMPTY_ARRAY;
    }

    @Override
    public boolean isQualified() {
        return mQualifier != null;
    }

    private String mQualifiedName;

    @Override
    public String getQualifiedName() {
        if (mQualifiedName == null) {
            if (mQualifier instanceof PsiReferenceExpression) {
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression) mQualifier;
                mQualifiedName = referenceExpression.getQualifiedName() + '.' + getName();
            } else {
                mQualifiedName = getName();
            }
        }
        return mQualifiedName;
    }

    @Nullable
    @Override
    public PsiElement getQualifier() {
        return mQualifier;
    }

    @Nullable
    @Override
    public String getReferenceName() {
        return mNameIdentifier.getText();
    }

    @Override
    public PsiElement getElement() {
        return mNameIdentifier;
    }

    @Override
    public TextRange getRangeInElement() {
        return mNameIdentifier.getTextRange();
    }

    @NonNull
    @Override
    public String getCanonicalText() {
        return getText();
    }

    @Override
    public boolean isSoft() {
        return false;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        return mManager.findMethod(mReferenceExp.binding);
    }
}