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
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiStatement;

import org.eclipse.jdt.internal.compiler.ast.LabeledStatement;

class EcjPsiLabeledStatement extends EcjPsiStatement implements PsiLabeledStatement {
    private PsiIdentifier mIdentifier;
    private PsiStatement mStatement;

    EcjPsiLabeledStatement(@NonNull EcjPsiManager manager,
            @NonNull LabeledStatement statement) {
        super(manager, statement);
    }

    void setStatement(PsiStatement statement) {
        mStatement = statement;
    }

    void setIdentifier(PsiIdentifier identifier) {
        mIdentifier = identifier;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitLabeledStatement(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @NonNull
    @Override
    public PsiIdentifier getLabelIdentifier() {
        return mIdentifier;
    }

    @Nullable
    @Override
    public PsiStatement getStatement() {
        return mStatement;
    }

    @Nullable
    @Override
    public PsiElement getNameIdentifier() {
        return getLabelIdentifier();
    }
}
