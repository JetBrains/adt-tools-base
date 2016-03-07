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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;

class EcjPsiTypeElement extends EcjPsiSourceElement implements PsiTypeElement {

    private PsiJavaCodeReferenceElement mReferenceElement;

    EcjPsiTypeElement(@NonNull EcjPsiManager manager,
            @Nullable TypeReference reference) {
        super(manager, reference);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitTypeElement(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setReferenceElement(PsiJavaCodeReferenceElement referenceElement) {
        mReferenceElement = referenceElement;
    }

    @NonNull
    @Override
    public PsiType getType() {
        PsiType type = mManager.findType((TypeReference) mNativeNode);
        if (type != null && mNativeNode instanceof ArrayTypeReference &&
                (((ArrayTypeReference)mNativeNode).bits & ASTNode.IsVarArgs) != 0) {
            return PsiEllipsisType.createEllipsis(type.getDeepComponentType(),
                    type.getAnnotations());
        }
        assert type != null;
        return type;
    }

    @Nullable
    @Override
    public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
        return mReferenceElement;
    }

    @NonNull
    @Override
    public PsiAnnotation[] getAnnotations() {
        return PsiAnnotation.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiAnnotation[] getApplicableAnnotations() {
        return PsiAnnotation.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotation(@NonNull String s) {
        return null;
    }
}
