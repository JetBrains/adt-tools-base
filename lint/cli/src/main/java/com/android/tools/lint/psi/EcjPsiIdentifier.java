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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.tree.IElementType;

class EcjPsiIdentifier extends EcjPsiSourceElement implements PsiIdentifier {

    private final String mIdentifier;

    EcjPsiIdentifier(@NonNull EcjPsiManager manager, @NonNull String identifier,
            @NonNull TextRange range) {
        super(manager, null);
        mIdentifier = identifier;
        mRange = range;
    }

    @Override
    public String getText() {
        /*
        Ideally we'd have completely accurate source offsets such that this wouldn't
        be necessary; we could just call getText() here to look up the identifier
        from the source. The below check looks for this. However it turns out that
        there are a number of scenarios where we end up with weird source offsets;
        especially for TypeReferences, where union types and annotation types give
        us offsets that aren't correct.
        if (EcjPsiBuilder.DEBUG) {
            if (!mIdentifier.equals(super.getText())
                    && getContainingFile() != null) {
                // If this is true, get rid of local field, else fix position offsets
                assert false : mIdentifier + " vs " + super.getText();
            }
        }
        */
        return mIdentifier;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitIdentifier(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @Override
    public IElementType getTokenType() {
        return JavaTokenType.IDENTIFIER;
    }
}
