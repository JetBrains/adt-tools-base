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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiResourceList;
import com.intellij.psi.PsiTryStatement;

import org.eclipse.jdt.internal.compiler.ast.Statement;

import java.util.ArrayList;

class EcjPsiTryStatement extends EcjPsiStatement implements PsiTryStatement {

    private PsiCodeBlock mTryBlock;

    private PsiCodeBlock mFinallyBlock;

    private PsiResourceList mResourceList;

    private PsiCatchSection[] mCatchSections;

    EcjPsiTryStatement(@NonNull EcjPsiManager manager,
            @Nullable Statement statement) {
        super(manager, statement);
    }

    void setTryBlock(PsiCodeBlock tryBlock) {
        mTryBlock = tryBlock;
    }

    void setCatchSections(PsiCatchSection[] catchSections) {
        mCatchSections = catchSections;
    }

    void setFinallyBlock(PsiCodeBlock finallyBlock) {
        mFinallyBlock = finallyBlock;
    }

    void setResourceList(PsiResourceList resourceList) {
        mResourceList = resourceList;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitTryStatement(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @Nullable
    @Override
    public PsiCodeBlock getTryBlock() {
        return mTryBlock;
    }

    @Nullable
    @Override
    public PsiCodeBlock getFinallyBlock() {
        return mFinallyBlock;
    }

    @Nullable
    @Override
    public PsiResourceList getResourceList() {
        return mResourceList;
    }

    @NonNull
    @Override
    public PsiCodeBlock[] getCatchBlocks() {
        PsiCatchSection[] catchSections = getCatchSections();
        if (catchSections.length == 0) {
            return PsiCodeBlock.EMPTY_ARRAY;
        }
        boolean lastIncomplete = catchSections[catchSections.length - 1].getCatchBlock() == null;
        PsiCodeBlock[] blocks = new PsiCodeBlock[lastIncomplete ? catchSections.length - 1
                : catchSections.length];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = catchSections[i].getCatchBlock();
        }
        return blocks;
    }

    @NonNull
    @Override
    public PsiParameter[] getCatchBlockParameters() {
        PsiCatchSection[] catchSections = getCatchSections();
        if (catchSections.length == 0) {
            return PsiParameter.EMPTY_ARRAY;
        }
        boolean lastIncomplete = catchSections[catchSections.length - 1].getCatchBlock() == null;
        int limit = lastIncomplete ? catchSections.length - 1 : catchSections.length;
        ArrayList<PsiParameter> parameters = new ArrayList<PsiParameter>();
        for (int i = 0; i < limit; i++) {
            PsiParameter parameter = catchSections[i].getParameter();
            if (parameter != null) {
                parameters.add(parameter);
            }
        }
        return parameters.toArray(new PsiParameter[parameters.size()]);
    }

    @NonNull
    @Override
    public PsiCatchSection[] getCatchSections() {
        return mCatchSections;
    }
}
