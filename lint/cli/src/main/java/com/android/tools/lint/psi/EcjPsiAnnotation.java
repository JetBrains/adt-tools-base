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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.meta.PsiMetaData;

import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;

class EcjPsiAnnotation extends EcjPsiSourceElement implements PsiAnnotation {

    private final Annotation mAnnotation;

    private String mQualifiedName;

    private PsiAnnotationParameterList mParameterList;

    private PsiJavaCodeReferenceElement mIdentifier;

    EcjPsiAnnotation(@NonNull EcjPsiManager manager, @NonNull Annotation ecjAnnotation) {
        super(manager, null); // setting range manually below
        mNativeNode = mAnnotation = ecjAnnotation;
        setRange(ecjAnnotation.sourceStart, ecjAnnotation.declarationSourceEnd + 1);
    }

    void setNameElement(PsiJavaCodeReferenceElement identifier) {
        mIdentifier = identifier;
    }

    void setParameterList(PsiAnnotationParameterList parameterList) {
        mParameterList = parameterList;
    }

    @Nullable
    @Override
    public String getQualifiedName() {
        if (mQualifiedName == null) {
            if (mAnnotation.resolvedType instanceof ReferenceBinding) {
                mQualifiedName = getTypeName(
                        ((ReferenceBinding)mAnnotation.resolvedType).compoundName);
            } else {
                mQualifiedName = getTypeName(mAnnotation.type);
            }
        }
        return mQualifiedName;
    }

    @Nullable
    @Override
    public PsiAnnotationOwner getOwner() {
        EcjPsiSourceElement parent = getParent();
        while (parent != null) {
            if (parent instanceof PsiAnnotationOwner) {
                return (PsiAnnotationOwner) parent;
            }
            parent = getParent();
        }
        return null;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitAnnotation(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @NonNull
    @Override
    public PsiAnnotationParameterList getParameterList() {
        return mParameterList;
    }

    @Nullable
    @Override
    public PsiJavaCodeReferenceElement getNameReferenceElement() {
        return mIdentifier;
    }

    @Nullable
    @Override
    public PsiAnnotationMemberValue findAttributeValue(String s) {
        if (mParameterList == null) {
            return null;
        }
        for (PsiNameValuePair pair : mParameterList.getAttributes()) {
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
    public PsiMetaData getMetaData() {
        throw new UnimplementedLintPsiApiException();
    }
}
