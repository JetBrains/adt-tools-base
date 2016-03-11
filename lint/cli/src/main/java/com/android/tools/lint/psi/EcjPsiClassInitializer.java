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
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;

class EcjPsiClassInitializer extends EcjPsiMember implements PsiClassInitializer {

    private PsiCodeBlock mBody;

    private PsiModifierList mModifierList;

    EcjPsiClassInitializer(@NonNull EcjPsiManager manager,
            @NonNull EcjPsiClass containingClass,
            @NonNull ASTNode node) {
        super(manager, containingClass, node);
    }

    void setBody(@NonNull PsiCodeBlock body) {
        mBody = body;
    }

    void setModifierList(@NonNull PsiModifierList modifierList) {
        mModifierList = modifierList;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitClassInitializer(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @NonNull
    @Override
    public PsiCodeBlock getBody() {
        return mBody;
    }

    @Nullable
    @Override
    public PsiModifierList getModifierList() {
        return mModifierList;
    }

    @Override
    public boolean hasModifierProperty(@NonNull @PsiModifier.ModifierConstant String s) {
        return mModifierList != null && mModifierList.hasModifierProperty(s);
    }

    @Nullable
    @Override
    public String getName() {
        return null;
    }
}
