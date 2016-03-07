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
import com.android.tools.lint.ExternalAnnotationRepository;
import com.google.common.collect.Lists;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.javadoc.PsiDocComment;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;

import java.util.Collection;
import java.util.List;

class EcjPsiBinaryField extends EcjPsiBinaryElement implements PsiField, PsiModifierList {

    private final FieldBinding mBinding;

    private String mName;

    EcjPsiBinaryField(@NonNull EcjPsiManager manager, @NonNull FieldBinding binding) {
        super(manager, binding);
        mBinding = binding;
    }

    @Override
    FieldBinding getBinding() {
        return mBinding;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitField(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @Override
    public void acceptChildren(@NonNull PsiElementVisitor visitor) {
        // Not exposing field initializers etc for binary elements
    }

    @NonNull
    @Override
    public String getName() {
        if (mName == null) {
            mName = new String(mBinding.name);
        }
        return mName;
    }

    @NonNull
    @Override
    public PsiType getType() {
        PsiType type = mManager.findType(mBinding.type);
        assert type != null : this;
        return type;
    }

    @Nullable
    @Override
    public PsiTypeElement getTypeElement() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiExpression getInitializer() {
        return null;
    }

    @Override
    public boolean hasInitializer() {
        return false;
    }

    @Nullable
    @Override
    public PsiReference getReference() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiReference[] getReferences() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public Object computeConstantValue() {
        return EcjPsiManager.getConstantValue(mBinding.constant());
    }

    @NonNull
    @Override
    public PsiIdentifier getNameIdentifier() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiDocComment getDocComment() {
        return null;
    }

    @Override
    public boolean isDeprecated() {
        return (mBinding.modifiers & ClassFileConstants.AccDeprecated) != 0;
    }

    @Nullable
    @Override
    public PsiClass getContainingClass() {
        return mManager.findClass(mBinding.declaringClass);
    }

    // Modifier list inlined here

    @NonNull
    @Override
    public PsiModifierList getModifierList() {
        return this;
    }

    @Override
    public boolean hasModifierProperty(@NonNull @PsiModifier.ModifierConstant String s) {
        return hasExplicitModifier(s);
    }

    @Override
    public boolean hasExplicitModifier(@NonNull @PsiModifier.ModifierConstant String s) {
        return EcjPsiModifierList.hasModifier(mBinding.modifiers, s);
    }

    @NonNull
    @Override
    public PsiAnnotation[] getAnnotations() {
        return getApplicableAnnotations();
    }

    @NonNull
    @Override
    public PsiAnnotation[] getApplicableAnnotations() {
        List<PsiAnnotation> all = Lists.newArrayListWithExpectedSize(4);
        ExternalAnnotationRepository manager = mManager.getAnnotationRepository();

        AnnotationBinding[] annotations = mBinding.getAnnotations();
        int count = annotations.length;
        if (count > 0) {
            for (AnnotationBinding annotation : annotations) {
                if (annotation != null) {
                    all.add(new EcjPsiBinaryAnnotation(mManager, this, annotation));
                }
            }
        }

        // Look for external annotations
        if (manager != null) {
            Collection<PsiAnnotation> external = manager.getAnnotations(mBinding);
            if (external != null) {
                all.addAll(external);
            }
        }

        return EcjPsiManager.ensureUnique(all);
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotation(@NonNull String s) {
        for (PsiAnnotation annotation : getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (s.equals(qualifiedName)) {
                return annotation;
            }
        }
        return null;
    }
}
