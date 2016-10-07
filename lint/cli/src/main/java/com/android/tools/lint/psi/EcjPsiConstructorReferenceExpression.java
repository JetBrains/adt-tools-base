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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall;

public class EcjPsiConstructorReferenceExpression extends EcjPsiReferenceExpression {

    private final ExplicitConstructorCall mCall;

    EcjPsiConstructorReferenceExpression(@NonNull EcjPsiManager manager,
            @NonNull ExplicitConstructorCall call) {
        super(manager, null);
        mCall = call;
    }

    @Nullable
    @Override
    public PsiType getType() {
        if (mCall.binding != null) {
            return mManager.findType(mCall.binding.declaringClass);
        }
        return null;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        return mManager.findMethod(mCall.binding);
    }
}
