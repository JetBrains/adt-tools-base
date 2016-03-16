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
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiReference;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;

abstract class EcjPsiSourceElement extends EcjPsiElement
        implements PsiElement, NavigationItem, Navigatable {
    protected ASTNode mNativeNode;
    protected PsiFile mFile;
    protected EcjPsiSourceElement mParent;
    protected TextRange mRange;
    protected EcjPsiSourceElement mFirstChild;
    protected EcjPsiSourceElement mLastChild;
    protected EcjPsiSourceElement mNextSibling;
    protected EcjPsiSourceElement mPrevSibling;

    protected EcjPsiSourceElement(@NonNull EcjPsiManager manager, @Nullable ASTNode ecjNode) {
        super(manager);
        if (ecjNode != null) {
            mNativeNode = ecjNode;
            setRange(ecjNode.sourceStart, ecjNode.sourceEnd + 1);
        }

        if (EcjPsiBuilder.DEBUG) {
            EcjPsiBuilder.registerElement(this);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getSimpleName());
        TextRange range = getTextRange();
        if (range != null) {
            sb.append(range.toString());
            String text = getText();
            if (text != null) {
                final int SNIPPET_LENGTH = 40;
                sb.append(':');
                int length = text.length();
                sb.append('"');
                text = text.substring(0, Math.min(SNIPPET_LENGTH, length)).replace("\n", "\\n");
                sb.append(text);
                if (length > SNIPPET_LENGTH) {
                    sb.append('\u2026');
                }
                sb.append('"');
            }
        }
        return sb.toString();
    }

    @Override
    public abstract void accept(@NonNull PsiElementVisitor visitor);

    @Override
    public final void acceptChildren(@NonNull PsiElementVisitor visitor) {
        EcjPsiSourceElement child = mFirstChild;
        while (child != null) {
            child.accept(visitor);
            child = child.mNextSibling;
        }
    }

    @Nullable
    public Object getNativeNode() {
        return mNativeNode;
    }

    public void setNativeNode(ASTNode nativeNode) {
        mNativeNode = nativeNode;
    }

    protected void adoptChild(@NonNull EcjPsiSourceElement child) {
        assert child.getParent() == null;
        child.setParent(this);
        if (mFirstChild == null) {
            mFirstChild = child;
            mLastChild = child;
        } else {
            mLastChild.mNextSibling = child;
            child.mPrevSibling = mLastChild;
            mLastChild = child;
        }
    }

    protected void setRange(int start, int end) {
        mRange = new TextRange(start, end);
    }

    void setParent(EcjPsiSourceElement parent) {
        mParent = parent;
    }

    void setRange(TextRange range) {
        mRange = range;
    }

    @NonNull
    @Override
    public PsiElement[] getChildren() {
        if (mFirstChild == null) {
            return new PsiElement[0];
        } else if (mFirstChild.mNextSibling == null) {
            return new PsiElement[] { mFirstChild };
        } else {
            EcjPsiSourceElement curr = mFirstChild;
            int count = 0;
            while (curr != null) {
                curr = curr.mNextSibling;
                count++;
            }
            PsiElement[] children = new PsiElement[count];
            curr = mFirstChild;
            int index = 0;
            while (curr != null) {
                children[index++] = curr;
                curr = curr.mNextSibling;
            }
            assert index == count;
            return children;
        }
    }

    @Override
    public EcjPsiSourceElement getParent() {
        return mParent;
    }

    @Override
    public PsiElement getFirstChild() {
        return mFirstChild;
    }

    @Override
    public PsiElement getLastChild() {
        return mLastChild;
    }

    @Override
    public PsiElement getNextSibling() {
        return mNextSibling;
    }

    @Override
    public PsiElement getPrevSibling() {
        return mPrevSibling;
    }

    @Override
    public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
        if (mFile != null) {
            return mFile;
        } else if (mParent != null) {
            return mFile = mParent.getContainingFile();
        } else {
            return null;
        }
    }

    @Override
    public TextRange getTextRange() {
        // Fallback: many elements have their offset ranges set up front, on construction,
        // based on ECJ node offsets. However, there are some nodes not directly mapped
        // to an underlying node, such as parameter lists (and type parameter lists, and
        // reference lists etc), as well as modifier lists, and sometimes these are empty
        // yet we have to create an AST node for them because it's required by PSI.
        // In this case, we create reasonable offsets based on the available sibling or
        // parent offsets. However, we can't do that at construction time (since at that
        // point siblings aren't yet known) so it's done here.
        if (mRange == null) {
            if (mFirstChild != null) {
                setRange(mFirstChild.getTextRange().getStartOffset(),
                        getLastChild().getTextRange().getEndOffset());
            } else {
                // Try to lazily compute a reasonable source offset
                int startOffset = 0;
                if (mPrevSibling != null && mPrevSibling.getTextRange() != null) {
                    startOffset = mPrevSibling.getTextRange().getEndOffset();
                } else if (mParent != null && mParent.getTextRange() != null) {
                    startOffset = mParent.getTextRange().getStartOffset();
                }
                int endOffset = startOffset;
                mRange = new TextRange(startOffset, endOffset);
            }
        }

        return mRange;
    }

    @Override
    public int getStartOffsetInParent() {
        int startOffset = getTextRange().getStartOffset();
        return mParent != null ? startOffset - mParent.getTextRange().getStartOffset() : startOffset;
    }

    @Override
    public int getTextLength() {
        return getTextRange().getEndOffset() - getTextRange().getStartOffset();
    }

    @Override
    public int getTextOffset() {
        return getTextRange().getStartOffset();
    }

    @Override
    public String getText() {
        EcjPsiJavaFile file;
        if (this instanceof EcjPsiJavaFile) {
            file = (EcjPsiJavaFile) this;
        } else {
            file = (EcjPsiJavaFile) getContainingFile();
        }
        if (file != null) {
            TextRange range = getTextRange();
            if (range != null) {
                String text = file.getText();
                if (text != null) {
                    return text.substring(range.getStartOffset(), range.getEndOffset());
                }
            }
            return super.toString();
        }

        return "<text not available>";
    }

    @NonNull
    @Override
    public char[] textToCharArray() {
        return getText().toCharArray();
    }

    @NonNull
    @Override
    public PsiElement getNavigationElement() {
        return this;
    }

    @Override
    public PsiElement getOriginalElement() {
        return this;
    }

    @Override
    public boolean textMatches(@NonNull CharSequence charSequence) {
        return getText().equals(charSequence.toString());
    }

    @Override
    public boolean textMatches(@NonNull PsiElement psiElement) {
        return getText().equals(psiElement.getText());
    }

    @Override
    public boolean textContains(char c) {
        return getText().indexOf(c) != -1;
    }

    @Nullable
    @Override
    public PsiReference getReference() {
        return null;
    }

    @NonNull
    @Override
    public PsiReference[] getReferences() {
        return PsiReference.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public PsiElement getContext() {
        return mParent;
    }

    @Nullable
    @Override
    public String getName() {
        // Children *actually* providing names should override this method
        throw new UnimplementedLintPsiApiException();
    }
}
