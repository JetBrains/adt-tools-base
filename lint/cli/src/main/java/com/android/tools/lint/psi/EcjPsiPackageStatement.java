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
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackageStatement;

import org.eclipse.jdt.internal.compiler.ast.ImportReference;

class EcjPsiPackageStatement extends EcjPsiSourceElement implements PsiPackageStatement {

    private String mPackageName;

    private EcjPsiModifierList mModifierList;

    EcjPsiPackageStatement(@NonNull EcjPsiManager manager, @NonNull ImportReference node) {
        super(manager, node);
    }

    void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    void setModifierList(@NonNull EcjPsiModifierList modifierList) {
        mModifierList = modifierList;
    }

    @Override
    public PsiJavaCodeReferenceElement getPackageReference() {
        return null;
    }

    @Override
    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public PsiModifierList getAnnotationList() {
        return mModifierList;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitPackageStatement(this);
        } else {
            visitor.visitElement(this);
        }
    }
}
