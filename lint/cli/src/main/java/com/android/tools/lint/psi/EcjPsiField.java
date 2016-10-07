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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.javadoc.PsiDocComment;

import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;

class EcjPsiField extends EcjPsiMember implements PsiField {

    protected final FieldDeclaration mDeclaration;

    private PsiExpression mInitializer;

    private PsiModifierList mModifierList;

    private PsiIdentifier mIdentifier;

    private String mName;

    private PsiTypeElement mTypeElement;

    EcjPsiField(
            @NonNull EcjPsiManager manager,
            @NonNull EcjPsiClass containingClass,
            @NonNull FieldDeclaration field) {
        // Passing null as native node such that we can set a custom offset range
        super(manager, containingClass, null);
        setRange(field.declarationSourceStart, field.declarationSourceEnd + 1);
        mNativeNode = mDeclaration = field;
        mManager.registerElement(field.binding, this);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitField(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setModifierList(@NonNull EcjPsiModifierList modifierList) {
        mModifierList = modifierList;
    }

    void setIdentifier(PsiIdentifier identifier) {
        mIdentifier = identifier;
    }

    void setTypeElement(PsiTypeElement typeElement) {
        mTypeElement = typeElement;
    }

    void setFieldInitializer(PsiExpression initializer) {
        mInitializer = initializer;
    }

    @Nullable
    @Override
    public PsiModifierList getModifierList() {
        return mModifierList;
    }

    @Override
    public boolean hasModifierProperty(@NonNull @PsiModifier.ModifierConstant String s) {
        return mModifierList.hasModifierProperty(s);
    }

    @Nullable
    @Override
    public String getName() {
        if (mName == null) {
            mName = new String((mDeclaration).name);
        }

        return mName;
    }

    @Nullable
    @Override
    public PsiExpression getInitializer() {
        return mInitializer;
    }

    @Override
    public boolean hasInitializer() {
        return mInitializer != null;
    }

    @NonNull
    @Override
    public PsiType getType() {
        PsiType type = mManager.findType(mDeclaration.type);
        if (type == null) {
            type = PsiType.NULL;
        }
        return type;
    }

    @Nullable
    @Override
    public PsiTypeElement getTypeElement() {
        return mTypeElement;
    }

    @Nullable
    @Override
    public Object computeConstantValue() {
        return EcjPsiManager.getConstantValue(mDeclaration.binding.constant());
    }

    @NonNull
    @Override
    public PsiIdentifier getNameIdentifier() {
        return mIdentifier;
    }

    @Nullable
    @Override
    public PsiDocComment getDocComment() {
        return null;
    }

    @Override
    public boolean isDeprecated() {
        return mDeclaration.binding != null
                && (mDeclaration.binding.modifiers & ClassFileConstants.AccDeprecated) != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        FieldBinding binding = mDeclaration.binding;
        FieldBinding otherBinding = null;
        if (o instanceof EcjPsiField) {
            otherBinding = (((EcjPsiField) o).mDeclaration).binding;
        } else if (o instanceof EcjPsiBinaryField) {
            otherBinding = (((EcjPsiBinaryField) o).getBinding());
        }
        return !(binding == null || otherBinding == null) && binding.equals(otherBinding);
    }

    @Override
    public int hashCode() {
        return mDeclaration.binding != null ? mDeclaration.binding.hashCode() : 0;
    }

    @Nullable
    FieldBinding getFieldBinding() {
        return mDeclaration.binding;
    }
}
