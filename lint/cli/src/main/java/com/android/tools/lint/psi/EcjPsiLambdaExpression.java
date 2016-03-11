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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;

import org.eclipse.jdt.internal.compiler.ast.LambdaExpression;

class EcjPsiLambdaExpression extends EcjPsiFunctionalExpression implements
        PsiLambdaExpression {

    private PsiParameterList mParameterList;

    private PsiElement mBody;

    EcjPsiLambdaExpression(@NonNull EcjPsiManager manager,
            @NonNull LambdaExpression expression) {
        super(manager, expression);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitLambdaExpression(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    void setParameterList(PsiParameterList parameterList) {
        mParameterList = parameterList;
    }

    void setBody(PsiElement body) {
        mBody = body;
    }

    @NonNull
    @Override
    public PsiParameterList getParameterList() {
        return mParameterList;
    }

    @Nullable
    @Override
    public PsiElement getBody() {
        return mBody;
    }

    @Override
    public boolean isVoidCompatible() {
        return ((LambdaExpression)mNativeNode).isVoidCompatible();
    }

    @Override
    public boolean isValueCompatible() {
        return ((LambdaExpression)mNativeNode).isValueCompatible();
    }

    @Override
    public boolean hasFormalParameterTypes() {
        PsiParameter[] parameters = getParameterList().getParameters();
        for (PsiParameter parameter : parameters) {
            if (parameter.getTypeElement() == null) {
                return false;
            }
        }
        return true;
    }
}
