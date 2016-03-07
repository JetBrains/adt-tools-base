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
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;

import org.eclipse.jdt.internal.compiler.ast.AbstractVariableDeclaration;

class EcjPsiDeclarationStatement extends EcjPsiStatement implements PsiDeclarationStatement {

    private PsiElement[] mDeclaredElements;

    EcjPsiDeclarationStatement(@NonNull EcjPsiManager manager,
            @Nullable AbstractVariableDeclaration declaration) {
        super(manager, null);
        if (declaration != null) {
            mNativeNode = declaration;
            setRange(declaration.declarationSourceStart, declaration.declarationSourceEnd + 1);
        }
    }

    void setDeclaredElements(@NonNull PsiElement[] declaredElements) {
        mDeclaredElements = declaredElements;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitDeclarationStatement(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @NonNull
    @Override
    public PsiElement[] getDeclaredElements() {
        return mDeclaredElements;
    }
}
