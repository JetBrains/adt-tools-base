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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;

public abstract class EcjPsiMember extends EcjPsiSourceElement implements PsiMember {

    private final EcjPsiClass mContainingClass;

    protected EcjPsiMember(@NonNull EcjPsiManager manager, @NonNull EcjPsiClass containingClass,
            @Nullable ASTNode ecjNode) {
        super(manager, ecjNode);
        mContainingClass = containingClass;
    }

    @Nullable
    @Override
    public PsiClass getContainingClass() {
        return mContainingClass;
    }

    @Override
    public String toString() {
        return super.toString() + ":" + getName();
    }
}
