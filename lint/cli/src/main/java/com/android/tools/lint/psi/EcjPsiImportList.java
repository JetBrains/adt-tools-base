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
import com.google.common.collect.Lists;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;

import java.util.List;

class EcjPsiImportList extends EcjPsiSourceElement implements PsiImportList {

    private List<EcjPsiImport> mImports;

    EcjPsiImportList(@NonNull EcjPsiManager manager) {
        super(manager, null);
    }

    void setImports(@NonNull List<EcjPsiImport> imports) {
        mImports = imports;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitImportList(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @NonNull
    @Override
    public PsiImportStatement[] getImportStatements() {
        return mImports.toArray(new PsiImportStatement[0]);
    }

    @NonNull
    @Override
    public PsiImportStaticStatement[] getImportStaticStatements() {
        List<EcjPsiStaticImport> result = Lists.newArrayList();
        for (EcjPsiImport statement : mImports) {
            if (statement instanceof EcjPsiStaticImport) {
                result.add((EcjPsiStaticImport)statement);
            }
        }
        return result.toArray(new PsiImportStaticStatement[0]);
    }

    @NonNull
    @Override
    public PsiImportStatementBase[] getAllImportStatements() {
        return mImports.toArray(new PsiImportStatement[0]);
    }

    @Nullable
    @Override
    public PsiImportStatement findSingleClassImportStatement(String s) {
        for (PsiImportStatement statement : mImports) {
            if (s.equals(statement.getQualifiedName())) {
                return statement;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public PsiImportStatement findOnDemandImportStatement(String packageName) {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiImportStatementBase findSingleImportStatement(String s) {
        for (PsiImportStatement statement : mImports) {
            if (s.equals(statement.getQualifiedName())) {
                return statement;
            }
        }
        return null;
    }
}
