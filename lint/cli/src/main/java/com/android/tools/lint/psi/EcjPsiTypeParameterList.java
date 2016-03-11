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
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;

class EcjPsiTypeParameterList extends EcjPsiSourceElement implements PsiTypeParameterList {
    private PsiTypeParameter[] mTypeParameters;

    EcjPsiTypeParameterList(@NonNull EcjPsiManager manager) {
        super(manager, null);
    }

    void setTypeParameters(PsiTypeParameter[] typeParameters) {
        mTypeParameters = typeParameters;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitTypeParameterList(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Override
    public PsiTypeParameter[] getTypeParameters() {
        return mTypeParameters;
    }

    @Override
    public int getTypeParameterIndex(PsiTypeParameter psiTypeParameter) {
        for (int i = 0; i < mTypeParameters.length; i++) {
            PsiTypeParameter parameter = mTypeParameters[i];
            if (parameter.equals(psiTypeParameter)) {
                return i;
            }
        }

        return -1;
    }
}
