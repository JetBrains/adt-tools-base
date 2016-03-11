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
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.util.PsiTreeUtil;

import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;

class EcjPsiAnonymousClass extends EcjPsiClass implements PsiAnonymousClass {

    private EcjPsiExpressionList mArgumentList;

    private PsiJavaCodeReferenceElement mBaseClassReference;

    private boolean mInQualifiedNew;

    EcjPsiAnonymousClass(@NonNull EcjPsiManager manager,
            @NonNull TypeDeclaration declaration) {
        super(manager, declaration, null);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitAnonymousClass(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    void setArgumentList(EcjPsiExpressionList argumentList) {
        mArgumentList = argumentList;
    }

    void setBaseClassReference(PsiJavaCodeReferenceElement baseClassReference) {
        mBaseClassReference = baseClassReference;
    }

    void setInQualifiedNew(boolean inQualifiedNew) {
        mInQualifiedNew = inQualifiedNew;
    }

    @Override
    public PsiReferenceList getExtendsList() {
        return null;
    }

    @Override
    public PsiReferenceList getImplementsList() {
        return null;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isAnnotationType() {
        return false;
    }

    @Override
    public boolean isEnum() {
        return false;
    }

    @Override
    public String getQualifiedName() {
        return null;
    }

    @Override
    public PsiIdentifier getNameIdentifier() {
        return null;
    }

    @Override
    public PsiModifierList getModifierList() {
        return null;
    }

    @Override
    public boolean hasModifierProperty(@NonNull String name) {
        return name.equals(PsiModifier.FINAL);
    }

    @Override
    public PsiTypeParameterList getTypeParameterList() {
        return null;
    }

    @Override
    public PsiClass getContainingClass() {
        return PsiTreeUtil.getParentOfType(mParent, PsiClass.class, true);
    }

    @NonNull
    @Override
    public PsiJavaCodeReferenceElement getBaseClassReference() {
        return mBaseClassReference;
    }

    @NonNull
    @Override
    public PsiClassType getBaseClassType() {
        PsiElement resolved = mBaseClassReference.resolve();
        if (resolved instanceof PsiClass) {
            mManager.getClassType((PsiClass) resolved);
        }
        // This shouldn't happen; PSI requires this to be non null
        //noinspection ConstantConditions
        return null;
    }

    @Nullable
    @Override
    public PsiExpressionList getArgumentList() {
        return mArgumentList;
    }

    @Override
    public boolean isInQualifiedNew() {
        return mInQualifiedNew;
    }
}
