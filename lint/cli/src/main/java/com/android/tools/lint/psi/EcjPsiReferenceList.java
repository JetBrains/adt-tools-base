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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.ast.TypeReference;

import java.util.List;

class EcjPsiReferenceList extends EcjPsiSourceElement implements PsiReferenceList {

    private final TypeReference[] mReferences;

    private final Role mRole;

    private PsiJavaCodeReferenceElement[] mElements;

    private PsiClassType[] mTypes;

    EcjPsiReferenceList(
            @NonNull EcjPsiManager manager,
            @Nullable TypeReference[] references,
            @NonNull Role role) {
        super(manager, null);
        mReferences = references;
        mRole = role;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitReferenceList(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setReferenceElements(PsiJavaCodeReferenceElement[] elements) {
        mElements = elements;
    }

    @NonNull
    @Override
    public PsiJavaCodeReferenceElement[] getReferenceElements() {
        return mElements;
    }

    @NonNull
    @Override
    public PsiClassType[] getReferencedTypes() {
        if (mReferences == null) {
            return PsiClassType.EMPTY_ARRAY;
        }
        if (mTypes == null) {
            List<PsiClassType> types = Lists.newArrayListWithCapacity(mReferences.length);
            for (TypeReference reference : mReferences) {
                PsiType type = mManager.findType(reference);
                if (type instanceof PsiClassType) {
                    types.add((PsiClassType) type);
                }
            }
            mTypes = types.toArray(PsiClassType.EMPTY_ARRAY);
        }
        return mTypes;
    }

    @Override
    public Role getRole() {
        return mRole;
    }
}
