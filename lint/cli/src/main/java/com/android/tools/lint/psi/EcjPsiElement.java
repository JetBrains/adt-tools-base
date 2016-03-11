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
import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiReference;

abstract class EcjPsiElement implements PsiElement {
    @NonNull protected final EcjPsiManager mManager;

    EcjPsiElement(@NonNull EcjPsiManager manager) {
        mManager = manager;
    }

    @NonNull
    @Override
    public Language getLanguage() {
        return getContainingFile().getLanguage();
    }

    @NonNull
    @Override
    public PsiElement[] getChildren() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public PsiElement getParent() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public PsiElement getFirstChild() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public PsiElement getLastChild() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public PsiElement getNextSibling() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public PsiElement getPrevSibling() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public TextRange getTextRange() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public int getStartOffsetInParent() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public int getTextLength() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public int getTextOffset() {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public String getText() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public char[] textToCharArray() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiElement getNavigationElement() {
        return this;
    }

    @Override
    public PsiElement getOriginalElement() {
        return this;
    }

    @Override
    public boolean textMatches(@NonNull CharSequence charSequence) {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean textMatches(@NonNull PsiElement psiElement) {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean textContains(char c) {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Nullable
    @Override
    public PsiReference getReference() {
        if (this instanceof PsiReference) {
            return (PsiReference)this;
        }
        return null;
    }

    @NonNull
    @Override
    public PsiReference[] getReferences() {
        PsiReference reference = getReference();
        if (reference != null) {
            return new PsiReference[]{reference};
        } else {
            return PsiReference.EMPTY_ARRAY;
        }
    }

    @Nullable
    @Override
    public PsiElement getContext() {
        return null;
    }

    @Override
    public boolean isPhysical() {
        return true;
    }

    // Navigatable
    // Here such that concrete children which implement Navigatable (for example, parameters)
    // don't have to repeat these no-op method declarations

    @SuppressWarnings({"UnusedParameters", "EmptyMethod", "unused"})
    public void navigate(boolean b) {
    }

    @SuppressWarnings({"SameReturnValue", "unused"})
    public boolean canNavigate() {
        return false;
    }

    @SuppressWarnings({"SameReturnValue", "unused"})
    public boolean canNavigateToSource() {
        return false;
    }
}
