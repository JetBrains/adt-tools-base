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
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiNameValuePair;

public class ExternalPsiNameValuePair extends EcjPsiElement implements PsiNameValuePair {

    private final String mName;

    private final String mLiteral;

    private final PsiAnnotationMemberValue mMemberValue;

    public ExternalPsiNameValuePair(
            @Nullable String name,
            @NonNull String literal,
            @NonNull PsiAnnotationMemberValue value) {
        //noinspection ConstantConditions
        super(null);

        mName = name;
        mLiteral = literal;
        mMemberValue = value;
    }

    @Nullable
    @Override
    public PsiIdentifier getNameIdentifier() {
        return null;
    }

    @Nullable
    @Override
    public String getName() {
        return mName;
    }

    @Nullable
    @Override
    public String getLiteralValue() {
        return mLiteral;
    }

    @Nullable
    @Override
    public PsiAnnotationMemberValue getValue() {
        return mMemberValue;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor psiElementVisitor) {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public void acceptChildren(@NonNull PsiElementVisitor psiElementVisitor) {
        throw new UnimplementedLintPsiApiException();
    }
}
