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
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.javadoc.PsiDocComment;

import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AnnotationMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;

class EcjPsiMethod extends EcjPsiMember implements PsiMethod {

    private final AbstractMethodDeclaration mDeclaration;

    private final String mName;

    private PsiIdentifier mIdentifier;

    private EcjPsiModifierList mModifierList;

    private PsiCodeBlock mBody;

    private PsiParameterList mArguments;

    private PsiTypeParameterList mTypeParameters;

    private PsiReferenceList mThrownExceptions;

    private PsiTypeElement mReturnTypeElement;

    EcjPsiMethod(@NonNull EcjPsiManager manager,
            @NonNull EcjPsiClass containingClass,
            @NonNull AbstractMethodDeclaration declaration) {
        // Passing null as native node such that we can set a custom offset range
        super(manager, containingClass, null);
        setRange(declaration.declarationSourceStart, declaration.declarationSourceEnd + 1);
        mNativeNode = mDeclaration = declaration;
        mName = new String(declaration.selector);
        manager.registerElement(declaration.binding, this);
    }

    void setNameIdentifier(@Nullable PsiIdentifier identifier) {
        mIdentifier = identifier;
    }

    void setModifierList(@NonNull EcjPsiModifierList modifierList) {
        mModifierList = modifierList;
    }

    void setBody(@Nullable PsiCodeBlock body) {
        mBody = body;
    }

    void setArguments(PsiParameterList arguments) {
        mArguments = arguments;
    }

    void setTypeParameters(PsiTypeParameterList typeParameters) {
        mTypeParameters = typeParameters;
    }

    void setThrownExceptions(PsiReferenceList thrownExceptions) {
        mThrownExceptions = thrownExceptions;
    }

    void setReturnTypeElement(PsiTypeElement returnTypeElement) {
        mReturnTypeElement = returnTypeElement;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitMethod(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @NonNull
    @Override
    public String getName() {
        return mName;
    }

    @Nullable
    @Override
    public PsiIdentifier getNameIdentifier() {
        return mIdentifier;
    }

    @NonNull
    @Override
    public PsiModifierList getModifierList() {
        return mModifierList;
    }

    @Override
    public boolean hasModifierProperty(@NonNull @PsiModifier.ModifierConstant String s) {
        return mModifierList != null && mModifierList.hasModifierProperty(s);
    }

    @NonNull
    @Override
    public PsiParameterList getParameterList() {
        return mArguments;
    }

    @NonNull
    @Override
    public PsiReferenceList getThrowsList() {
        return mThrownExceptions;
    }

    @Nullable
    @Override
    public PsiCodeBlock getBody() {
        return mBody;
    }

    @Override
    public boolean isConstructor() {
        return mDeclaration.isConstructor();
    }

    @Override
    public boolean isVarArgs() {
        PsiParameter[] parameters = getParameterList().getParameters();
        return parameters.length > 0 && parameters[parameters.length - 1].isVarArgs();
    }

    @Nullable
    @Override
    public PsiType getReturnType() {
        return mDeclaration instanceof MethodDeclaration
                ? mManager.findType(((MethodDeclaration)mDeclaration).returnType)
                : null;
    }

    @Nullable
    @Override
    public PsiTypeElement getReturnTypeElement() {
        return mReturnTypeElement;
    }

    @NonNull
    @Override
    public PsiMethod[] findSuperMethods() {
        return getSuperMethods(true);
    }

    private PsiMethod[] getSuperMethods(boolean checkAccess) {
        MethodBinding superBinding = EcjPsiManager.findSuperMethodBinding(mDeclaration.binding,
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
        // TODO: Populate from AbstractMethodDeclaration.javadoc
        return null;
    }

    @Override
    public boolean isDeprecated() {
        return mDeclaration.binding != null
                && (mDeclaration.binding.modifiers & ClassFileConstants.AccDeprecated) != 0;
    }

    @Override
    public boolean hasTypeParameters() {
        return mTypeParameters != null;
    }

    @Nullable
    @Override
    public PsiTypeParameterList getTypeParameterList() {
        return mTypeParameters;
    }

    @NonNull
    @Override
    public PsiTypeParameter[] getTypeParameters() {
        return mTypeParameters != null
                ? mTypeParameters.getTypeParameters()
                : PsiTypeParameter.EMPTY_ARRAY;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EcjPsiMethod && mDeclaration.binding.equals(
                ((EcjPsiMethod)o).mDeclaration.binding);
    }
}
