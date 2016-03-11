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
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiSynchronizedStatement;

import org.eclipse.jdt.internal.compiler.ast.Statement;

class EcjPsiSynchronizedStatement extends EcjPsiStatement implements
        PsiSynchronizedStatement {

    private PsiCodeBlock mBody;

    private PsiExpression mLockExpression;

    EcjPsiSynchronizedStatement(@NonNull EcjPsiManager manager,
            @Nullable Statement statement) {
        super(manager, statement);
    }

    void setBody(PsiCodeBlock body) {
        mBody = body;
    }

    void setLockExpression(PsiExpression lockExpression) {
        mLockExpression = lockExpression;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitSynchronizedStatement(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @Nullable
    @Override
    public PsiExpression getLockExpression() {
        return mLockExpression;
    }

    @Nullable
    @Override
    public PsiCodeBlock getBody() {
        return mBody;
    }
}
