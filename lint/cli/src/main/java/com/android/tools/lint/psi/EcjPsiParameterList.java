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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;

class EcjPsiParameterList extends EcjPsiSourceElement implements PsiParameterList {
    private PsiParameter[] mParameters;

    EcjPsiParameterList(@NonNull EcjPsiManager manager) {
        super(manager, null);
    }

    void setParameters(@NonNull PsiParameter[] parameters) {
        mParameters = parameters;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitParameterList(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @NonNull
    @Override
    public PsiParameter[] getParameters() {
        return mParameters;
    }

    @Override
    public int getParameterIndex(PsiParameter psiParameter) {
        for (int i = 0; i < mParameters.length; i++) {
            if (mParameters[i] == psiParameter) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public int getParametersCount() {
        return mParameters.length;
    }
}
