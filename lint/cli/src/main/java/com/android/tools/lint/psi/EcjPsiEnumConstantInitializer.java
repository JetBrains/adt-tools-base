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
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiEnumConstantInitializer;

import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;

class EcjPsiEnumConstantInitializer extends EcjPsiAnonymousClass implements PsiEnumConstantInitializer {

    private PsiEnumConstant mConstant;

    EcjPsiEnumConstantInitializer(@NonNull EcjPsiManager manager,
            @NonNull TypeDeclaration declaration) {
        super(manager, declaration);
    }

    void setConstant(PsiEnumConstant constant) {
        mConstant = constant;
    }

    @NonNull
    @Override
    public PsiEnumConstant getEnumConstant() {
        return mConstant;
    }
}
