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
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;

import org.eclipse.jdt.internal.compiler.ast.ImportReference;

class EcjPsiImport extends EcjPsiSourceElement implements PsiImportStatement {

    private final String mQualifiedName;

    private final boolean mOnDemand;

    private PsiJavaCodeReferenceElement mReference;

    EcjPsiImport(@NonNull EcjPsiManager manager, @NonNull ImportReference importReference,
            String qualifiedName, boolean onDemand) {
        super(manager, null); // null native node: don't want to waste setRange call; handled below
        mNativeNode = importReference;
        setRange(importReference.declarationSourceStart,
                importReference.declarationSourceEnd + 1);

        mQualifiedName = qualifiedName;
        mOnDemand = onDemand;
    }

    void setReference(PsiJavaCodeReferenceElement reference) {
        mReference = reference;
    }

    @Override
    public String toString() {
        return super.toString() + ":" + mQualifiedName;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitImportStatement(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @Nullable
    @Override
    public String getQualifiedName() {
        return mQualifiedName;
    }

    @Override
    public boolean isOnDemand() {
        return mOnDemand;
    }

    @Override
    public boolean isForeignFileImport() {
        return false;
    }

    @Nullable
    @Override
    public PsiJavaCodeReferenceElement getImportReference() {
        return mReference;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        return mManager.findElement(mNativeNode);
    }
}
