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
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiBreakStatement;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLoopStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.util.PsiTreeUtil;

import org.eclipse.jdt.internal.compiler.ast.Statement;

class EcjPsiBreakStatement extends EcjPsiStatement implements PsiBreakStatement {

    private PsiIdentifier mIdentifier;

    EcjPsiBreakStatement(@NonNull EcjPsiManager manager,
            @Nullable Statement statement) {
        super(manager, statement);
    }

    public void setIdentifier(PsiIdentifier identifier) {
        mIdentifier = identifier;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitBreakStatement(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @Nullable
    @Override
    public PsiIdentifier getLabelIdentifier() {
        return mIdentifier;
    }

    @Nullable
    @Override
    public PsiStatement findExitedStatement() {
        return findStatement(this, getLabelIdentifier());
    }

    /**
     * Returns the statement pointed to by the given break/continue statement, with the
     * given optional label, if any
     */
    @Nullable
    public static PsiStatement findStatement(@NonNull PsiElement element,
            @Nullable PsiIdentifier label) {
        if (label != null) {
            String labelName = label.getText();
            PsiElement curr = element.getParent();
            while (curr != null) {
                if (curr instanceof PsiMethod
                        || curr instanceof PsiAnonymousClass
                        || curr instanceof PsiLambdaExpression) {
                    return null;
                }
                PsiLabeledStatement statement = PsiTreeUtil.getParentOfType(curr,
                        PsiLabeledStatement.class, true);
                if (statement != null) {
                    if (labelName.equals(statement.getLabelIdentifier().getText())) {
                        return statement.getStatement();
                    }
                }
                curr = statement;
            }
        } else {
            PsiElement curr = element.getParent();
            while (curr != null) {
                if (curr instanceof PsiLoopStatement) {
                    return (PsiLoopStatement) curr;
                } else if (curr instanceof PsiSwitchStatement
                        // Only break works in switch statement
                        && element instanceof PsiBreakStatement) {
                    return (PsiSwitchStatement) curr;
                } else if (curr instanceof PsiMethod
                        || curr instanceof PsiClassInitializer) {
                    return null;
                }
                curr = curr.getParent();
            }
        }

        return null;
    }
}
