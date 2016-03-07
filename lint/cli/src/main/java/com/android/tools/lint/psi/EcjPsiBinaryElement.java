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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;

import org.eclipse.jdt.internal.compiler.lookup.Binding;

abstract class EcjPsiBinaryElement extends EcjPsiElement implements PsiCompiledElement {
    protected final Binding mBinding;

    EcjPsiBinaryElement(@NonNull EcjPsiManager manager, @Nullable Binding binding) {
        super(manager);
        mBinding = binding;
    }

    Binding getBinding() {
        return mBinding;
    }

    @Override
    public PsiElement getParent() {
        return null;
    }

    @Override
    public PsiElement getFirstChild() {
        return null;
    }

    @Override
    public PsiElement getNextSibling() {
        return null;
    }

    @Override
    public PsiElement getLastChild() {
        return null;
    }

    @NonNull
    @Override
    public PsiElement[] getChildren() {
        return PsiElement.EMPTY_ARRAY;
    }

    @Override
    public TextRange getTextRange() {
        return TextRange.EMPTY_RANGE;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                ":" + (mBinding != null ? mBinding.toString() : "<unknown>");
    }

    @Override
    public void accept(@NonNull PsiElementVisitor psiElementVisitor) {
        // Can't run visitor on binary elements
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public void acceptChildren(@NonNull PsiElementVisitor visitor) {
        // Can't run visitor on binary elements
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public PsiElement getMirror() {
        return null;
    }
}
