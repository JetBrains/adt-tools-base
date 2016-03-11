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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiTypeElement;

import org.eclipse.jdt.internal.compiler.ast.Expression;

class EcjPsiInstanceOfExpression extends EcjPsiExpression implements
        PsiInstanceOfExpression {

    private PsiExpression mOperand;

    private PsiTypeElement mCheckType;

    EcjPsiInstanceOfExpression(@NonNull EcjPsiManager manager,
            @NonNull Expression expression) {
        super(manager, expression);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitInstanceOfExpression(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setOperand(PsiExpression operand) {
        mOperand = operand;
    }

    void setCheckType(PsiTypeElement checkType) {
        mCheckType = checkType;
    }

    @NonNull
    @Override
    public PsiExpression getOperand() {
        return mOperand;
    }

    @Nullable
    @Override
    public PsiTypeElement getCheckType() {
        return mCheckType;
    }
}
