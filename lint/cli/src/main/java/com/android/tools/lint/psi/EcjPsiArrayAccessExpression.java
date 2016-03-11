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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;

import org.eclipse.jdt.internal.compiler.ast.Expression;

class EcjPsiArrayAccessExpression extends EcjPsiExpression implements
        PsiArrayAccessExpression {

    private PsiExpression mIndexExpression;

    private PsiExpression mArrayExpression;

    EcjPsiArrayAccessExpression(@NonNull EcjPsiManager manager,
            @NonNull Expression expression) {
        super(manager, expression);
    }


    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitArrayAccessExpression(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setIndexExpression(PsiExpression indexExpression) {
        mIndexExpression = indexExpression;
    }

    void setArrayExpression(PsiExpression arrayExpression) {
        mArrayExpression = arrayExpression;
    }

    @NonNull
    @Override
    public PsiExpression getArrayExpression() {
        return mArrayExpression;
    }

    @NonNull
    @Override
    public PsiExpression getIndexExpression() {
        return mIndexExpression;
    }
}
