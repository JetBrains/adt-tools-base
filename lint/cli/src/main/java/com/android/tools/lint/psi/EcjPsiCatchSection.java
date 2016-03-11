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
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.Block;

import java.util.List;

class EcjPsiCatchSection extends EcjPsiSourceElement implements PsiCatchSection {

    private PsiParameter mParameter;

    private PsiCodeBlock mCodeBlock;

    public EcjPsiCatchSection(EcjPsiManager manager, Argument catchArgument, Block catchBlock) {
        super(manager, null);
        setRange(catchArgument.declarationSourceStart, catchBlock.sourceEnd + 1);
    }

    void setParameter(PsiParameter parameter) {
        mParameter = parameter;
    }

    void setCodeBlock(PsiCodeBlock codeBlock) {
        mCodeBlock = codeBlock;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitCatchSection(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @Nullable
    @Override
    public PsiParameter getParameter() {
        return mParameter;
    }

    @Nullable
    @Override
    public PsiCodeBlock getCatchBlock() {
        return mCodeBlock;
    }

    @Nullable
    @Override
    public PsiType getCatchType() {
        return mParameter.getType();
    }

    @NonNull
    @Override
    public List<PsiType> getPreciseCatchTypes() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiTryStatement getTryStatement() {
        return (PsiTryStatement)getParent();
    }

    @Nullable
    @Override
    public PsiJavaToken getRParenth() {
        return null;
    }

    @Nullable
    @Override
    public PsiJavaToken getLParenth() {
        return null;
    }
}
