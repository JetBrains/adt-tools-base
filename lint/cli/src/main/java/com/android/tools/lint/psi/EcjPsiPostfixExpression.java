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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.tree.IElementType;

import org.eclipse.jdt.internal.compiler.ast.Expression;

class EcjPsiPostfixExpression extends EcjPsiExpression implements PsiPostfixExpression {

    private IElementType mOperationType;

    private PsiExpression mOperand;

    EcjPsiPostfixExpression(@NonNull EcjPsiManager manager,
            @NonNull Expression expression) {
        super(manager, expression);
    }

    void setOperationType(IElementType operationType) {
        mOperationType = operationType;
    }

    void setOperand(PsiExpression operand) {
        mOperand = operand;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitPostfixExpression(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @NonNull
    @Override
    public PsiExpression getOperand() {
        return mOperand;
    }

    @NonNull
    @Override
    public IElementType getOperationTokenType() {
        return mOperationType;
    }

    @NonNull
    @Override
    public PsiJavaToken getOperationSign() {
        throw new UnimplementedLintPsiApiException();
    }
}
