/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.lint.EcjSourceFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackageStatement;

import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;

import java.io.File;

class EcjPsiJavaFile extends EcjPsiSourceElement implements PsiJavaFile {

    private final EcjSourceFile mSource;

    private EcjPsiImportList mImportList;

    private EcjPsiPackageStatement mPackageStatement;

    private PsiClass[] mClasses;

    EcjPsiJavaFile(@NonNull EcjPsiManager manager,
            @NonNull EcjSourceFile source,
            @NonNull CompilationUnitDeclaration node) {
        super(manager, node);
        mSource = source;
    }

    void setImportList(EcjPsiImportList importList) {
        mImportList = importList;
    }

    void setPackageStatement(@NonNull EcjPsiPackageStatement packageStatement) {
        mPackageStatement = packageStatement;
    }

    public void setClasses(@NonNull PsiClass[] classes) {
        mClasses = classes;
    }

    @Override
    public String getText() {
        return mSource.getSource();
    }

    @Override
    public String toString() {
        return super.toString() + ":" + getName();
    }

    @Nullable
    @Override
    public PsiImportList getImportList() {
        return mImportList;
    }

    @Nullable
    @Override
    public PsiPackageStatement getPackageStatement() {
        return mPackageStatement;
    }

    @NonNull
    @Override
    public PsiClass[] getClasses() {
        return mClasses;
    }

    @NonNull
    @Override
    public String getPackageName() {
        return mPackageStatement != null ? mPackageStatement.getPackageName() : "";
    }

    @NonNull
    @Override
    public String getName() {
        return new String(mSource.getFileName());
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitJavaFile(this);
        } else {
            visitor.visitFile(this);
        }
    }

    @Override
    public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
        return this;
    }

    @Override
    public EcjPsiDirectory getParent() {
        return null;
    }

    @NonNull
    @Override
    public PsiElement[] getOnDemandImports(boolean b, @Deprecated boolean b1) {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiClass[] getSingleClassImports(@Deprecated boolean b) {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public String[] getImplicitlyImportedPackages() {
        return new String[] { "java.lang" };
    }

    @NonNull
    @Override
    public PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass psiClass) {
        String qualifiedName = psiClass.getQualifiedName();
        PsiImportStatementBase importStatement = mImportList
                .findSingleClassImportStatement(qualifiedName);
        if (importStatement != null) {
            return importStatement.getImportReference();
        }
        importStatement = mImportList.findSingleImportStatement(qualifiedName);
        if (importStatement != null) {
            return importStatement.getImportReference();
        }

        return null;
    }

    @NonNull
    @Override
    public LanguageLevel getLanguageLevel() {
        return LanguageLevel.JDK_1_8;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public long getModificationStamp() {
        return 0;
    }

    @NonNull
    @Override
    public PsiFile getOriginalFile() {
        return this;
    }

    public File getIoFile() {
        return mSource.getFile();
    }
}
