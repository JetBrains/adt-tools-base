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
import com.intellij.psi.PsiResourceList;
import com.intellij.psi.PsiResourceListElement;
import com.intellij.psi.PsiResourceVariable;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Iterator;
import java.util.List;

public class EcjPsiResourceList extends EcjPsiSourceElement implements PsiResourceList {
    private List<PsiResourceVariable> mResourceVariables;

    public EcjPsiResourceList(@NonNull EcjPsiManager manager) {
        super(manager, null);
    }

    void setResourceVariables(List<PsiResourceVariable> resourceVariables) {
        mResourceVariables = resourceVariables;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitResourceList(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Override
    public int getResourceVariablesCount() {
        return mResourceVariables.size();
    }

    @NonNull
    @Override
    public List<PsiResourceVariable> getResourceVariables() {
        return mResourceVariables;
    }

    @Override
    public Iterator<PsiResourceListElement> iterator() {
        return PsiTreeUtil.childIterator(this, PsiResourceListElement.class);
    }
}
