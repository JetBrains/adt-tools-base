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
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.Expression;

class EcjPsiNewExpression extends EcjPsiCallExpression implements PsiNewExpression {

    private PsiExpression mQualifier;

    private PsiExpression[] mArrayDimensions;

    private PsiArrayInitializerExpression mArrayInitializer;

    private PsiJavaCodeReferenceElement mClassReference;

    private PsiAnonymousClass mAnonymousClass;

    EcjPsiNewExpression(@NonNull EcjPsiManager manager,
            @NonNull Expression expression) {
        super(manager, expression);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitNewExpression(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    void setQualifier(PsiExpression qualifier) {
        mQualifier = qualifier;
    }

    void setArrayDimensions(PsiExpression[] arrayDimensions) {
        mArrayDimensions = arrayDimensions;
    }

    void setArrayInitializer(PsiArrayInitializerExpression arrayInitializer) {
        mArrayInitializer = arrayInitializer;
    }

    void setClassReference(PsiJavaCodeReferenceElement classReference) {
        mClassReference = classReference;
    }

    void setAnonymousClass(PsiAnonymousClass anonymousClass) {
        mAnonymousClass = anonymousClass;
    }

    @Nullable
    @Override
    public PsiExpression getQualifier() {
        return mQualifier;
    }

    @NonNull
    @Override
    public PsiExpression[] getArrayDimensions() {
        return mArrayDimensions;
    }

    @Nullable
    @Override
    public PsiArrayInitializerExpression getArrayInitializer() {
        return mArrayInitializer;
    }

    @Nullable
    @Override
    public PsiJavaCodeReferenceElement getClassReference() {
        return mClassReference;
    }

    @Nullable
    @Override
    public PsiAnonymousClass getAnonymousClass() {
        return mAnonymousClass;
    }

    @Nullable
    @Override
    public PsiJavaCodeReferenceElement getClassOrAnonymousClassReference() {
        return mClassReference;
    }

    @Nullable
    @Override
    public PsiType getOwner(@NonNull PsiAnnotation psiAnnotation) {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiMethod resolveConstructor() {
        if (mNativeNode instanceof AllocationExpression) {
            return mManager.findMethod(((AllocationExpression)mNativeNode).binding);
        }
        // No constructor for array allocation expressions
        return null;
    }
}
