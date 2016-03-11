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
import com.intellij.psi.PsiDiamondType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;

class EcjPsiReferenceParameterList extends EcjPsiSourceElement implements
        PsiReferenceParameterList {

    private PsiTypeElement[] mTypeParameters;

    EcjPsiReferenceParameterList(@NonNull EcjPsiManager manager) {
        super(manager, null);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitReferenceParameterList(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setTypeParameters(PsiTypeElement[] typeParameters) {
        mTypeParameters = typeParameters;
    }

    @NonNull
    @Override
    public PsiTypeElement[] getTypeParameterElements() {
        return mTypeParameters;
    }

    @NonNull
    @Override
    public PsiType[] getTypeArguments() {
        PsiType[] types = new PsiType[mTypeParameters.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = mTypeParameters[i].getType();
        }
        if (types.length == 1 && types[0] instanceof PsiDiamondType) {
            return ((PsiDiamondType) types[0]).resolveInferredTypes().getTypes();
        }
        return types;
    }
}
