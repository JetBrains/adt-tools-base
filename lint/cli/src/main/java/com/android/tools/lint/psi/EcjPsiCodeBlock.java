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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiStatement;

class EcjPsiCodeBlock extends EcjPsiSourceElement implements PsiCodeBlock {

    private PsiStatement[] mStatements;

    protected EcjPsiCodeBlock(@NonNull EcjPsiManager manager) {
        super(manager, null);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitCodeBlock(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    void setStatements(PsiStatement[] statements) {
        mStatements = statements;
    }

    @NonNull
    @Override
    public PsiStatement[] getStatements() {
        return mStatements;
    }

    @Nullable
    @Override
    public PsiElement getFirstBodyElement() {
        return mStatements.length > 0 ? mStatements[0] : null;
    }

    @Nullable
    @Override
    public PsiElement getLastBodyElement() {
        return mStatements.length > 0 ? mStatements[mStatements.length - 1] : null;
    }

    @Nullable
    @Override
    public PsiJavaToken getLBrace() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiJavaToken getRBrace() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean shouldChangeModificationCount(PsiElement psiElement) {
        return false;
    }
}
