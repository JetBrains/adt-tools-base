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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportStaticStatement;

import org.eclipse.jdt.internal.compiler.ast.ImportReference;

class EcjPsiStaticImport extends EcjPsiImport implements PsiImportStaticStatement {

    EcjPsiStaticImport(@NonNull EcjPsiManager manager, @NonNull ImportReference ecjNode,
            String qualifiedName, boolean onDemand) {
        super(manager, ecjNode, qualifiedName, onDemand);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitImportStaticStatement(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @Nullable
    @Override
    public PsiClass resolveTargetClass() {
        return mManager.findClass(((ImportReference)mNativeNode).getImportName());
    }

    @Nullable
    @Override
    public String getReferenceName() {
        char[][] tokens = ((ImportReference) mNativeNode).tokens;
        return new String(tokens[tokens.length - 1]);
    }
}
