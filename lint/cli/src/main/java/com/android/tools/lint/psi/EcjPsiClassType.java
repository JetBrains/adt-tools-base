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

import static com.android.tools.lint.psi.EcjPsiManager.getInternalName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.lookup.ParameterizedTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;

import java.util.List;

class EcjPsiClassType extends PsiClassType {

    private final ReferenceBinding mReferenceBinding;

    private final EcjPsiManager mManager;

    EcjPsiClassType(
            @NonNull EcjPsiManager manager,
            @NonNull ReferenceBinding referenceBinding) {
        super(null);
        mManager = manager;
        mReferenceBinding = referenceBinding;
    }

    @NonNull
    ReferenceBinding getBinding() {
        return mReferenceBinding;
    }

    @Nullable
    @Override
    public PsiClass resolve() {
        return mManager.findClass(mReferenceBinding);
    }

    @Override
    public String getClassName() {
        // Only the simple name, not the fully qualified name
        char[][] compoundName = mReferenceBinding.compoundName;
        if (compoundName != null) {
            return EcjPsiManager.getTypeName(compoundName[compoundName.length - 1]);
        } else {
            // Type variables
            return EcjPsiManager.getTypeName(mReferenceBinding.sourceName);
        }
    }

    @NonNull
    @Override
    public PsiType[] getParameters() {
        if (mReferenceBinding instanceof ParameterizedTypeBinding) {
            ParameterizedTypeBinding binding = (ParameterizedTypeBinding) mReferenceBinding;
            TypeBinding[] bindings = binding.arguments;
            if (bindings != null && bindings.length > 0) {
                List<PsiType> types = Lists.newArrayListWithExpectedSize(bindings.length);
                for (TypeBinding b : bindings) {
                    PsiType type = mManager.findType(b);
                    if (type != null) {
                        types.add(type);
                    }
                }
                return types.toArray(PsiType.EMPTY_ARRAY);
            }
        }
        return PsiType.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiClassType.ClassResolveResult resolveGenerics() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiClassType rawType() {
        return this;
    }

    @NonNull
    @Override
    public String getPresentableText() {
        return getCanonicalText();
    }

    @NonNull
    @Override
    public String getCanonicalText() {
        if (mReferenceBinding.compoundName == null) {
            // TypeVariableBindings for example - maybe call genericSignature() ?
            char[] sourceName = mReferenceBinding.sourceName();
            if (sourceName != null) {
                return new String(sourceName);
            }
        }
        return EcjPsiManager.getTypeName(mReferenceBinding);
    }

    @NonNull
    @Override
    public String getInternalCanonicalText() {
        return getInternalName(mReferenceBinding.compoundName);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean equalsToText(@NonNull String s) {
        return s.equals(getCanonicalText());
    }

    @NonNull
    @Override
    public LanguageLevel getLanguageLevel() {
        return mManager.getLanguageLevel();
    }
}
