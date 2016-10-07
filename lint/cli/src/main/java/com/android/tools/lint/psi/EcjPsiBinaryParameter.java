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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;

import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;

public class EcjPsiBinaryParameter extends EcjPsiBinaryElement implements PsiParameter,
        PsiModifierList {

    private boolean mVarArgs;
    private final int mIndex;
    private final EcjPsiBinaryMethod mMethod;

    public EcjPsiBinaryParameter(@NonNull EcjPsiManager manager,
            @Nullable TypeBinding binding, @NonNull EcjPsiBinaryMethod method,
            int index) {
        super(manager, binding);
        mMethod = method;
        mIndex = index;
    }

    @SuppressWarnings("SameParameterValue")
    void setVarArgs(boolean varArgs) {
        mVarArgs = varArgs;
    }

    @NonNull
    EcjPsiBinaryMethod getOwnerMethod() {
        return mMethod;
    }

    public int getIndex() {
        return mIndex;
    }

    @Override
    public boolean isVarArgs() {
        return mVarArgs;
    }

    @NonNull
    @Override
    public PsiType getType() {
        PsiType type = mManager.findType((TypeBinding) mBinding);
        if (type == null) {
            type = PsiType.NULL;
        }
        return type;
    }

    @Nullable
    @Override
    public PsiTypeElement getTypeElement() {
        return null;
    }

    @Nullable
    @Override
    public PsiExpression getInitializer() {
        return null;
    }

    @Override
    public boolean hasInitializer() {
        return false;
    }

    @Nullable
    @Override
    public Object computeConstantValue() {
        return null;
    }

    @Nullable
    @Override
    public PsiIdentifier getNameIdentifier() {
        return null;
    }

    @Nullable
    @Override
    public String getName() {
        return null;
    }

    // Modifier list inlined here

    @NonNull
    @Override
    public PsiModifierList getModifierList() {
        return this;
    }

    @Override
    public boolean hasModifierProperty(@NonNull @PsiModifier.ModifierConstant String s) {
        return hasExplicitModifier(s);
    }

    @Override
    public boolean hasExplicitModifier(@NonNull @PsiModifier.ModifierConstant String s) {
        return mBinding instanceof ReferenceBinding
                && EcjPsiModifierList .hasModifier(((ReferenceBinding) mBinding).modifiers, s);
    }

    @NonNull
    @Override
    public PsiAnnotation[] getAnnotations() {
        // TODO: Merge in modifiers from external sources and here
        //return new PsiAnnotation[0];
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiAnnotation[] getApplicableAnnotations() {
        return getAnnotations();
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotation(@NonNull String s) {
        for (PsiAnnotation annotation : getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (s.equals(qualifiedName)) {
                return annotation;
            }
        }
        return null;
    }
}
