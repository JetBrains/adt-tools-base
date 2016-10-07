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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiNameValuePair;

import org.eclipse.jdt.internal.compiler.ast.MemberValuePair;

class EcjPsiNameValuePair extends EcjPsiSourceElement implements PsiNameValuePair {

    private PsiIdentifier mNameIdentifier;

    private PsiAnnotationMemberValue mValue;

    EcjPsiNameValuePair(@NonNull EcjPsiManager manager,
            @NonNull MemberValuePair pair) {
        super(manager, null);
        mNativeNode = pair;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitNameValuePair(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setNameIdentifier(PsiIdentifier nameIdentifier) {
        mNameIdentifier = nameIdentifier;
    }

    void setMemberValue(PsiAnnotationMemberValue value) {
        mValue = value;
    }

    @Nullable
    @Override
    public String getName() {
        return mNameIdentifier != null ? mNameIdentifier.getText() : null;
    }

    @Nullable
    @Override
    public PsiIdentifier getNameIdentifier() {
        return mNameIdentifier;
    }

    @Nullable
    @Override
    public String getLiteralValue() {
        return mNativeNode.toString();
    }

    @Nullable
    @Override
    public PsiAnnotationMemberValue getValue() {
        return mValue;
    }
}
