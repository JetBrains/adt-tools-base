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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiQualifiedNamedElement;

import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.PackageBinding;

import java.util.Collection;
import java.util.List;

class EcjPsiPackage extends EcjPsiBinaryElement implements PsiPackage, PsiModifierList {

    private final PackageBinding mPackageBinding;

    public EcjPsiPackage(@NonNull EcjPsiManager manager,
            @Nullable PackageBinding binding) {
        super(manager, binding);
        mPackageBinding = binding;
    }

    @NonNull
    @Override
    public String getQualifiedName() {
        return EcjPsiManager.getTypeName(mPackageBinding.compoundName);
    }

    @Nullable
    @Override
    public String getName() {
        return new String(mPackageBinding.compoundName[mPackageBinding.compoundName.length - 1]);
    }

    @Nullable
    @Override
    public PsiPackage getParentPackage() {
        // TODO: mPackageBinding.fParent isn't exposed.
        // Have to look it up from the lookup map.
        return null;
    }

    @Nullable
    @Override
    public PsiQualifiedNamedElement getContainer() {
        return null;
    }

    @Nullable
    @Override
    public PsiModifierList getAnnotationList() {
        return getModifierList();
    }

    // Modifier list inlined here

    @NonNull
    @Override
    public PsiModifierList getModifierList() {
        return this;
    }

    @Override
    public boolean hasModifierProperty(@NonNull @PsiModifier.ModifierConstant String s) {
        return false;
    }

    @Override
    public boolean hasExplicitModifier(@NonNull @PsiModifier.ModifierConstant String s) {
        return false;
    }

    @NonNull
    @Override
    public PsiAnnotation[] getAnnotations() {
        return getApplicableAnnotations();
    }

    @NonNull
    @Override
    public PsiAnnotation[] getApplicableAnnotations() {
        return findAnnotations(false);
    }

    @SuppressWarnings("SameParameterValue")
    private PsiAnnotation[] findAnnotations(boolean includeSuper) {
        List<PsiAnnotation> all = Lists.newArrayListWithExpectedSize(4);
        ExternalAnnotationRepository manager = mManager.getAnnotationRepository();

        PackageBinding binding = this.mPackageBinding;
        AnnotationBinding[] annotations = binding.getAnnotations();
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
            Collection<PsiAnnotation> external = manager.getAnnotations(binding);
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

    @Nullable
    public PackageBinding getPackageBinding() {
        return mPackageBinding;
    }
}
