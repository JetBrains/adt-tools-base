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
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiNameValuePair;

import org.eclipse.jdt.internal.compiler.lookup.ElementValuePair;

class EcjPsiBinaryNameValuePair extends EcjPsiBinaryElement implements PsiNameValuePair,
        PsiAnnotationMemberValue, PsiLiteral {

    private final ElementValuePair mPair;

    private final String mName;

    private EcjPsiBinaryAnnotationMemberValue mValue;

    EcjPsiBinaryNameValuePair(@NonNull EcjPsiManager manager,
            @NonNull ElementValuePair pair) {
        super(manager, null);
        mPair = pair;
        mName = new String(pair.getName());
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
        // Uhm... we don't have this for binary elements
        return null;
    }

    @Nullable
    @Override
    public PsiAnnotationMemberValue getValue() {
        if (mValue == null) {
            mValue = new EcjPsiBinaryAnnotationMemberValue(mManager, mPair.getValue());
        }
        return mValue;
    }
}
