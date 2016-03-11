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
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;

import org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;

class EcjPsiEnumConstant extends EcjPsiField implements PsiEnumConstant {

    private PsiEnumConstantInitializer mInitializer;

    private PsiExpressionList mArgumentList;

    EcjPsiEnumConstant(@NonNull EcjPsiManager manager,
            @NonNull EcjPsiClass containingClass,
            @NonNull FieldDeclaration field) {
        super(manager, containingClass, field);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitEnumConstant(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    void setInitializer(PsiEnumConstantInitializer initializer) {
        mInitializer = initializer;
    }

    void setArgumentList(PsiExpressionList argumentList) {
        mArgumentList = argumentList;
    }

    @Override
    public PsiClass getContainingClass() {
        PsiElement parent = getParent();
        return parent instanceof PsiClass ? (PsiClass)parent : null;
    }

    @Override
    public boolean hasModifierProperty(@NonNull String name) {
        // Implicit
        return PsiModifier.PUBLIC.equals(name)
                || PsiModifier.STATIC.equals(name)
                || PsiModifier.FINAL.equals(name);
    }

    @Nullable
    @Override
    public PsiExpressionList getArgumentList() {
        return mArgumentList;
    }

    @Nullable
    @Override
    public PsiMethod resolveMethod() {
        return resolveConstructor();
    }

    @Nullable
    @Override
    public PsiMethod resolveConstructor() {
        if (mDeclaration.initialization instanceof AllocationExpression) {
            return mManager.findMethod(((AllocationExpression)mDeclaration.initialization).binding);
        }
        return null;
    }

    @NonNull
    @Override
    public JavaResolveResult resolveMethodGenerics() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiEnumConstantInitializer getInitializingClass() {
        return mInitializer;
    }

    @NonNull
    @Override
    public PsiEnumConstantInitializer getOrCreateInitializingClass() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean hasInitializer() {
        return true;
    }

    @Override
    public Object computeConstantValue() {
        return this;
    }
}
