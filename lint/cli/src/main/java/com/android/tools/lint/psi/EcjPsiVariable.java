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
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.util.IncorrectOperationException;

import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding;

abstract class EcjPsiVariable extends EcjPsiSourceElement implements PsiVariable {
    protected final LocalDeclaration mVariable;
    private EcjPsiModifierList mModifierList;
    private PsiIdentifier mNameIdentifier;
    private PsiExpression mInitializer;
    private String mName;

    private PsiTypeElement mTypeElement;

    EcjPsiVariable(@NonNull EcjPsiManager manager,
            @Nullable LocalDeclaration variable) {
        super(manager, variable);
        mVariable = variable;
    }

    void setModifierList(@NonNull EcjPsiModifierList modifierList) {
        mModifierList = modifierList;
    }

    void setNameIdentifier(@NonNull PsiIdentifier nameIdentifier) {
        mNameIdentifier = nameIdentifier;
    }

    void setTypeElement(PsiTypeElement typeElement) {
        mTypeElement = typeElement;
    }

    @Nullable
    @Override
    public String getName() {
        if (mName == null) {
            mName = new String(mVariable.name);
        }

        return mName;
    }

    @Nullable
    @Override
    public PsiIdentifier getNameIdentifier() {
        return mNameIdentifier;
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

    @Override
    public boolean hasInitializer() {
        return mInitializer != null;
    }

    @Nullable
    @Override
    public PsiExpression getInitializer() {
        return mInitializer;
    }

    public void setInitializer(PsiExpression expression) throws IncorrectOperationException {
        mInitializer = expression;
    }

    @NonNull
    @Override
    public PsiType getType() {
        PsiType type = mManager.findType(mVariable.type);
        assert type != null : this;
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
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EcjPsiVariable)) {
            return false;
        }
        LocalVariableBinding binding = mVariable.binding;
        LocalVariableBinding otherBinding = (((EcjPsiVariable)o).mVariable.binding);
        if (binding == null || otherBinding == null) {
            return mVariable.equals(((EcjPsiVariable)o).mVariable);
        }

        return binding.equals(otherBinding);
    }

    @Override
    public int hashCode() {
        return mVariable.binding != null ? mVariable.binding.hashCode() : 0;
    }

    @Nullable
    LocalVariableBinding getVariableBinding() {
        return mVariable != null ? mVariable.binding : null;
    }
}
