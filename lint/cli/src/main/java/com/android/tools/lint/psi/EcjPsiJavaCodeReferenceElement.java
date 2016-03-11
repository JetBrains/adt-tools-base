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
import com.google.common.collect.Lists;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.ast.ImportReference;
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;

import java.util.List;

class EcjPsiJavaCodeReferenceElement extends EcjPsiSourceElement
        implements PsiJavaCodeReferenceElement {

    private PsiElement mNameElement;

    private EcjPsiJavaCodeReferenceElement mQualifier;

    private PsiReferenceParameterList mParameterList;

    EcjPsiJavaCodeReferenceElement(@NonNull EcjPsiManager manager,
            @Nullable TypeReference typeReference) {
        super(manager, typeReference);
    }

    EcjPsiJavaCodeReferenceElement(@NonNull EcjPsiManager manager,
            @Nullable ImportReference importReference) {
        super(manager, importReference);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitReferenceElement(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setNameElement(PsiElement nameElement) {
        mNameElement = nameElement;
    }

    void setQualifier(EcjPsiJavaCodeReferenceElement qualifier) {
        mQualifier = qualifier;
    }

    void setParameterList(PsiReferenceParameterList parameterList) {
        mParameterList = parameterList;
    }

    @Nullable
    @Override
    public PsiElement getReferenceNameElement() {
        return mNameElement;
    }

    @Nullable
    @Override
    public PsiReferenceParameterList getParameterList() {
        return mParameterList;
    }

    @NonNull
    @Override
    public PsiType[] getTypeParameters() {
        if (!(mNativeNode instanceof TypeReference)) {
            // No type parameters for import statements
            return PsiType.EMPTY_ARRAY;
        }
        TypeReference typeReference = (TypeReference)mNativeNode;
        TypeReference[][] typeArguments = typeReference.getTypeArguments();
        if (typeArguments.length == 0) {
            return PsiType.EMPTY_ARRAY;
        }

        for (int i = typeArguments.length - 1; i >= 0; i--) {
            TypeReference[] refs = typeArguments[i];
            if (refs != null && refs.length > 0) {
                List<PsiType> types = Lists.newArrayListWithCapacity(refs.length);
                for (TypeReference ref : refs) {
                    PsiType type = mManager.findType(ref);
                    if (type != null) {
                        types.add(type);
                    }
                }
                return types.toArray(PsiType.EMPTY_ARRAY);
            }
        }

        return PsiType.EMPTY_ARRAY;
    }

    @Override
    public boolean isQualified() {
        return mNativeNode instanceof ImportReference
                || mNativeNode instanceof QualifiedTypeReference;
    }

    @Override
    public String getQualifiedName() {
        if (mNativeNode instanceof ImportReference) {
            return getTypeName(((ImportReference)mNativeNode).getImportName());
        }
        TypeReference reference = (TypeReference)mNativeNode;
        if (reference.resolvedType instanceof ReferenceBinding) {
            return getTypeName((ReferenceBinding) reference.resolvedType);
        } else {
            return getTypeName(reference.getTypeName());
        }
    }

    @Nullable
    @Override
    public String getReferenceName() {
        return mNameElement.getText();
    }

    @Nullable
    @Override
    public PsiElement getQualifier() {
        return mQualifier;
    }

    @Override
    public PsiElement getElement() {
        return this;
    }

    @Override
    public TextRange getRangeInElement() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        return mManager.findElement(mNativeNode);
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
