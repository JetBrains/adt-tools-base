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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;

import java.lang.reflect.Modifier;

class EcjPsiModifierList extends EcjPsiSourceElement implements PsiModifierList {

    private int mModifiers;

    private PsiAnnotation[] mAnnotations;

    EcjPsiModifierList(@NonNull EcjPsiManager manager, int modifiers) {
        super(manager, null);
        mModifiers = modifiers;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if (mAnnotations != null && mAnnotations.length > 0) {
            sb.append(mAnnotations.length).append(" annotations");
        } else if (mModifiers == 0) {
            sb.append(":<empty>");
            return sb.toString();
        }
        sb.append(':').append(Integer.toString(mModifiers));
        if ((mModifiers & Modifier.PUBLIC) != 0) {
            sb.append(':').append(PsiModifier.PUBLIC);
        }
        if ((mModifiers & Modifier.PROTECTED) != 0) {
            sb.append(':').append(PsiModifier.PROTECTED);
        }
        if ((mModifiers & Modifier.PRIVATE) != 0) {
            sb.append(':').append(PsiModifier.PRIVATE);
        }
        if ((mModifiers & Modifier.STATIC) != 0) {
            sb.append(':').append(PsiModifier.STATIC);
        }
        if ((mModifiers & Modifier.ABSTRACT) != 0) {
            sb.append(':').append(PsiModifier.ABSTRACT);
        }
        if ((mModifiers & Modifier.FINAL) != 0) {
            sb.append(':').append(PsiModifier.FINAL);
        }
        if ((mModifiers & Modifier.NATIVE) != 0) {
            sb.append(':').append(PsiModifier.NATIVE);
        }
        if ((mModifiers & Modifier.SYNCHRONIZED) != 0) {
            sb.append(':').append(PsiModifier.SYNCHRONIZED);
        }
        if ((mModifiers & Modifier.STRICT) != 0) {
            sb.append(':').append(PsiModifier.STRICTFP);
        }
        return sb.toString();
    }

    int getModifiers() {
        return mModifiers;
    }

    void setModifiers(int modifiers) {
        mModifiers = modifiers;
    }

    @NonNull
    @Override
    public PsiAnnotation[] getAnnotations() {
        return mAnnotations;
    }

    void setAnnotations(EcjPsiAnnotation[] annotations) {
        mAnnotations = annotations;
    }

    @Override
    public boolean hasModifierProperty(@PsiModifier.ModifierConstant @NonNull String s) {
        // TODO: Figure out implicit modifiers and merge those in. (E.g. interface methods
        // are public, etc.)
        return hasExplicitModifier(s);
    }

    @Override
    public boolean hasExplicitModifier(@PsiModifier.ModifierConstant @NonNull String s) {
        return hasModifier(mModifiers, s);
    }

    static boolean hasModifier(int modifiers, @PsiModifier.ModifierConstant @NonNull String s) {
        if (PsiModifier.PUBLIC.equals(s)) {
            return (modifiers & Modifier.PUBLIC) != 0;
        }
        if (PsiModifier.PROTECTED.equals(s)) {
            return (modifiers & Modifier.PROTECTED) != 0;
        }
        if (PsiModifier.PRIVATE.equals(s)) {
            return (modifiers & Modifier.PRIVATE) != 0;
        }
        // TODO: PsiModifier.PACKAGE_LOCAL
        if (PsiModifier.STATIC.equals(s)) {
            return (modifiers & Modifier.STATIC) != 0;
        }
        if (PsiModifier.ABSTRACT.equals(s)) {
            return (modifiers & Modifier.ABSTRACT) != 0;
        }
        if (PsiModifier.FINAL.equals(s)) {
            return (modifiers & Modifier.FINAL) != 0;
        }
        if (PsiModifier.TRANSIENT.equals(s)) {
            return (modifiers & Modifier.TRANSIENT) != 0;
        }
        if (PsiModifier.VOLATILE.equals(s)) {
            return (modifiers & Modifier.VOLATILE) != 0;
        }
        if (PsiModifier.NATIVE.equals(s)) {
            return (modifiers & Modifier.NATIVE) != 0;
        }
        if (PsiModifier.SYNCHRONIZED.equals(s)) {
            return (modifiers & Modifier.SYNCHRONIZED) != 0;
        }
        if (PsiModifier.STRICTFP.equals(s)) {
            return (modifiers & Modifier.STRICT) != 0;
        }
        // What about PACKAGE_LOCAL and DEFAULT?
        return false;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitModifierList(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @NonNull
    @Override
    public PsiAnnotation[] getApplicableAnnotations() {
        throw new UnimplementedLintPsiApiException();
// TODO: What's this?
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotation(@NonNull String s) {
        if (mAnnotations != null) {
            for (PsiAnnotation annotation : mAnnotations) {
                if (s.equals(annotation.getQualifiedName())) {
                    return annotation;
                }
            }
        }

        return null;
    }
}
