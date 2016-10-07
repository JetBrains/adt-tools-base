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

import static com.android.tools.lint.psi.EcjPsiManager.getTypeName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.meta.PsiMetaData;

import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.ElementValuePair;

class EcjPsiBinaryAnnotation extends EcjPsiBinaryElement implements PsiAnnotation,
        PsiAnnotationParameterList {

    private final AnnotationBinding mBinding;

    private final String mQualifiedName;

    private final PsiAnnotationOwner mOwner;

    private PsiNameValuePair[] mPairs;

    private PsiJavaCodeReferenceElement mNameReferenceElement;

    EcjPsiBinaryAnnotation(
            @NonNull EcjPsiManager manager,
            @NonNull PsiAnnotationOwner owner,
            @NonNull final AnnotationBinding binding) {
        super(manager, null);
        mOwner = owner;
        mBinding = binding;
        mQualifiedName = getTypeName(mBinding.getAnnotationType());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":" + mQualifiedName;
    }

    @NonNull
    @Override
    public PsiAnnotationParameterList getParameterList() {
        return this;
    }

    @Nullable
    @Override
    public String getQualifiedName() {
        return mQualifiedName;
    }

    @Nullable
    @Override
    public PsiJavaCodeReferenceElement getNameReferenceElement() {
        if (mNameReferenceElement == null) {
            mNameReferenceElement = new EcjPsiBinaryJavaCodeReferenceElement(mManager,
                    mBinding.getAnnotationType());
        }
        return mNameReferenceElement;
    }

    @Nullable
    @Override
    public PsiAnnotationMemberValue findAttributeValue(String s) {
        for (PsiNameValuePair pair : getAttributes()) {
            if (Objects.equal(pair.getName(), s)) {
                return pair.getValue();
            }
        }
        return null;
    }

    @Nullable
    @Override
    public PsiAnnotationMemberValue findDeclaredAttributeValue(String s) {
        return findAttributeValue(s);
    }

    @Nullable
    @Override
    public PsiAnnotationOwner getOwner() {
        return mOwner;
    }

    @Nullable
    @Override
    public PsiMetaData getMetaData() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiNameValuePair[] getAttributes() {
        if (mPairs == null) {
            ElementValuePair[] elementValuePairs = mBinding.getElementValuePairs();
            if (elementValuePairs != null && elementValuePairs.length > 0) {
                mPairs = new PsiNameValuePair[elementValuePairs.length];
                for (int i = 0; i < elementValuePairs.length; i++) {
                    ElementValuePair pair = elementValuePairs[i];
                    mPairs[i] = new EcjPsiBinaryNameValuePair(mManager, pair);

                }
            } else {
                mPairs = PsiNameValuePair.EMPTY_ARRAY;
            }
        }
        return mPairs;
    }
}
