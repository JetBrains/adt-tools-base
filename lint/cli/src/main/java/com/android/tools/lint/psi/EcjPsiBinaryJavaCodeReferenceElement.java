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
import com.google.common.collect.Lists;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeVariableBinding;

import java.util.List;

class EcjPsiBinaryJavaCodeReferenceElement extends EcjPsiBinaryElement implements
        PsiJavaCodeReferenceElement {
    private final ReferenceBinding mReferenceBinding;

    public EcjPsiBinaryJavaCodeReferenceElement(
            @NonNull EcjPsiManager manager,
            ReferenceBinding referenceBinding) {
        super(manager, referenceBinding);
        mReferenceBinding = referenceBinding;
    }

    @Nullable
    @Override
    public PsiElement getReferenceNameElement() {
        return null;
    }

    @Nullable
    @Override
    public PsiReferenceParameterList getParameterList() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiType[] getTypeParameters() {
        TypeVariableBinding[] typeArguments = mReferenceBinding.typeVariables();
        if (typeArguments.length == 0) {
            return PsiType.EMPTY_ARRAY;
        }

        List<PsiType> types = Lists.newArrayListWithCapacity(typeArguments.length);
        for (TypeVariableBinding typeArgument : typeArguments) {
            PsiType type = mManager.findType(typeArgument);
            if (type != null) {
                types.add(type);
            }
        }
        return types.toArray(PsiType.EMPTY_ARRAY);
    }

    @Override
    public boolean isQualified() {
        return true;
    }

    @Override
    public String getQualifiedName() {
        return EcjPsiManager.getTypeName(mReferenceBinding);
    }

    @Nullable
    @Override
    public PsiElement getQualifier() {
        return null;
    }

    @Nullable
    @Override
    public String getReferenceName() {
        return null;
    }

    @Override
    public PsiElement getElement() {
        return this;
    }

    @Override
    public TextRange getRangeInElement() {
        return null;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        return mManager.findElement(mReferenceBinding);
    }

    @NonNull
    @Override
    public String getCanonicalText() {
        return getQualifiedName();
    }

    @Override
    public boolean isSoft() {
        return false;
    }
}
