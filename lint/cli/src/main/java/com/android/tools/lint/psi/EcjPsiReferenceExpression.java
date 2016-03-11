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

import static com.android.tools.lint.psi.EcjPsiManager.getTypeName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;

class EcjPsiReferenceExpression extends EcjPsiExpression implements PsiReferenceExpression {

    private PsiExpression mQualifier;

    private String mQualifiedName;

    private PsiIdentifier mIdentifier;

    private Expression mTypeExpression;

    EcjPsiReferenceExpression(@NonNull EcjPsiManager manager,
            @Nullable Expression reference) {
        super(manager, reference);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitReferenceExpression(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setQualifier(PsiExpression qualifier) {
        mQualifier = qualifier;
    }

    void setNameElement(PsiIdentifier identifier) {
        mIdentifier = identifier;
    }

    void setTypeExpression(Expression typeExpression) {
        mTypeExpression = typeExpression;
    }

    @Nullable
    @Override
    public PsiExpression getQualifierExpression() {
        return mQualifier;
    }

    @Nullable
    @Override
    public PsiElement getQualifier() {
        return mQualifier;
    }

    @Nullable
    @Override
    public String getReferenceName() {
        return mIdentifier.getText();
    }

    @Override
    public boolean isQualified() {
        return mQualifier != null;
    }

    @Override
    public String getQualifiedName() {
        if (mQualifiedName == null) {
            PsiElement resolved = resolve();
            if (resolved instanceof PsiMember) {
                if (resolved instanceof PsiClass) {
                    mQualifiedName = ((PsiClass) resolved).getQualifiedName();
                } else {
                    PsiMember member = (PsiMember) resolved;
                    PsiClass containingClass = member.getContainingClass();
                    if (containingClass != null && containingClass.getQualifiedName() != null) {
                        mQualifiedName = containingClass.getQualifiedName() + '.' + member
                                .getName();
                    } else {
                        mQualifiedName = member.getName();
                    }
                }
            } else if (mNativeNode instanceof QualifiedNameReference) {
                mQualifiedName = getTypeName(((QualifiedNameReference)mNativeNode).tokens);
            } else {
                mQualifiedName = getReferenceName();
            }
        }
        return mQualifiedName;
    }

    @Nullable
    @Override
    public PsiElement getReferenceNameElement() {
        return mIdentifier;
    }

    @Nullable
    @Override
    public PsiReferenceParameterList getParameterList() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiType getType() {
        //noinspection VariableNotUsedInsideIf
        if (mNativeNode != null) {
            return super.getType();
        } else {
            if (mTypeExpression != null) {
                TypeBinding resolvedType = mTypeExpression.resolvedType;
                return mManager.findType(resolvedType);
            } else {
                return null;
            }
        }
    }

    @NonNull
    @Override
    public PsiType[] getTypeParameters() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public PsiElement getElement() {
        return this;
    }

    @Override
    public TextRange getRangeInElement() {
        return mIdentifier.getTextRange();
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        return mManager.findElement(mNativeNode);
    }

    @NonNull
    @Override
    public String getCanonicalText() {
        return getQualifiedName();
    }

    @Override
    public boolean isSoft() {
        return false;
    }
}
