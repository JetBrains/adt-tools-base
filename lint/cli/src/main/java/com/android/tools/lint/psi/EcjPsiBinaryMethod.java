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
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.javadoc.PsiDocComment;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;

import java.util.Collection;
import java.util.List;

class EcjPsiBinaryMethod extends EcjPsiBinaryElement implements PsiMethod, PsiParameterList,
        PsiModifierList {

    private final MethodBinding mMethodBinding;

    private String mName;

    EcjPsiBinaryMethod(
            @NonNull EcjPsiManager manager,
            @NonNull MethodBinding binding) {
        super(manager, binding);
        mMethodBinding = binding;
    }

    @NonNull
    @Override
    public MethodBinding getBinding() {
        return mMethodBinding;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitMethod(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @Override
    public void acceptChildren(@NonNull PsiElementVisitor visitor) {
        // Not exposing method bodies for binary elements
    }

    @NonNull
    @Override
    public String getName() {
        if (mName == null) {
            if (mMethodBinding.isConstructor()) {
                ReferenceBinding cls = mMethodBinding.declaringClass;
                while (cls != null && cls.isAnonymousType()) {
                    cls = cls.superclass();
                }
                if (cls != null) {
                    char[][] compoundName = cls.compoundName;
                    mName = new String(compoundName[compoundName.length - 1]);
                    return mName;
                }
            }
            mName = new String(mMethodBinding.selector);
        }
        return mName;
    }

    @Nullable
    @Override
    public PsiType getReturnType() {
        return mManager.findType(mMethodBinding.returnType);
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
    public PsiTypeElement getReturnTypeElement() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiReferenceList getThrowsList() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiCodeBlock getBody() {
        return null;
    }

    @Override
    public boolean isConstructor() {
        return mMethodBinding.isConstructor();
    }

    @Override
    public boolean isVarArgs() {
        return mMethodBinding.isVarargs();
    }

    @Nullable
    @Override
    public PsiIdentifier getNameIdentifier() {
        return null;
    }

    @NonNull
    @Override
    public PsiMethod[] findSuperMethods() {
        return getSuperMethods(true);
    }

    private PsiMethod[] getSuperMethods(boolean checkAccess) {
        MethodBinding superBinding = EcjPsiManager.findSuperMethodBinding(mMethodBinding,
                false, checkAccess);
        if (superBinding != null) {
            PsiMethod method = mManager.findMethod(superBinding);
            if (method != null) {
                // Currently we only check super class hierarchy, not methods in interfaces
                // so there's always just at most one match
                return new PsiMethod[] { method };
            }
        }

        return PsiMethod.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiMethod[] findSuperMethods(boolean checkAccess) {
        return getSuperMethods(checkAccess);
    }

    @NonNull
    @Override
    public PsiMethod[] findSuperMethods(PsiClass parentClass) {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiMethod findDeepestSuperMethod() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiMethod[] findDeepestSuperMethods() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiDocComment getDocComment() {
        return null;
    }

    @Override
    public boolean isDeprecated() {
        return (mMethodBinding.modifiers & ClassFileConstants.AccDeprecated) != 0;
    }

    @Override
    public boolean hasTypeParameters() {
        return mMethodBinding.typeVariables != null && mMethodBinding.typeVariables.length > 0;
    }

    @Nullable
    @Override
    public PsiTypeParameterList getTypeParameterList() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiTypeParameter[] getTypeParameters() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiClass getContainingClass() {
        return mManager.findClass(mMethodBinding.declaringClass);
    }

    @NonNull
    @Override
    public PsiParameterList getParameterList() {
        return this;
    }

    private EcjPsiBinaryParameter[] mParameters;

    @NonNull
    @Override
    public PsiParameter[] getParameters() {
        if (mParameters == null) {
            TypeBinding[] parameters = mMethodBinding.parameters;
            mParameters = new EcjPsiBinaryParameter[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                mParameters[i] = new EcjPsiBinaryParameter(mManager, parameters[i], this, i);
            }
            if (mMethodBinding.isVarargs() && parameters.length > 0) {
                mParameters[parameters.length - 1].setVarArgs(true);
            }
        }

        return mParameters;
    }

    @Override
    public int getParameterIndex(PsiParameter psiParameter) {
        PsiParameter[] parameters = getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i] == psiParameter) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public int getParametersCount() {
        return getParameters().length;
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
        return EcjPsiModifierList.hasModifier(mMethodBinding.modifiers, s);
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

        MethodBinding binding = this.mMethodBinding;
        while (binding != null) {
            //noinspection VariableNotUsedInsideIf
            if (binding.declaringClass != null) { // prevent NPE in binding.getAnnotations()
                AnnotationBinding[] annotations = binding.getAnnotations();
                int count = annotations.length;
                if (count > 0) {
                    for (AnnotationBinding annotation : annotations) {
                        if (annotation != null) {
                            all.add(new EcjPsiBinaryAnnotation(mManager, this, annotation));
                        }
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

            if (!includeSuper) {
                break;
            }

            binding = EcjPsiManager.findSuperMethodBinding(binding, false, false);
            if (binding != null && binding.isPrivate()) {
                break;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        MethodBinding binding = mMethodBinding;
        MethodBinding otherBinding;
        if (o instanceof EcjPsiMethod) {
            otherBinding = (((EcjPsiMethod) o).getBinding());
            return binding != null && otherBinding != null && binding.equals(otherBinding);
        } else if (o instanceof EcjPsiBinaryMethod) {
            otherBinding = (((EcjPsiBinaryMethod) o).getBinding());
            return binding != null && otherBinding != null && binding.equals(otherBinding);
        } else if (o instanceof ExternalPsiReferenceExpressionMemberValue) {
            String signature = ((ExternalPsiReferenceExpressionMemberValue) o).getQualifiedName();
            PsiClass containingClass = getContainingClass();
            if (containingClass != null) {
                String fqn = containingClass.getQualifiedName();
                if (fqn != null) {
                    if (signature.startsWith(fqn) && signature.endsWith(getName())
                            && signature.length() == fqn.length() + getName().length() + 1) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        return mMethodBinding.hashCode();
    }
}
