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

package com.android.ide.common.blame;

import com.google.common.base.Objects;

/**
 * An immutable position in a text file, used in errors to point the user to an issue.
 *
 * Positions that are unknown are represented by -1.
 */
public class SourcePosition {

    public static final SourcePosition UNKNOWN = new SourcePosition();

    private final int mStartLine;

    private final int mStartColumn;

    private final int mStartOffset;

    private final int mEndLine;

    private final int mEndColumn;

    private final int mEndOffset;

    public SourcePosition(int startLine, int startColumn, int startOffset, int endLine,
            int endColumn,
            int endOffset) {
        mStartLine = startLine;
        mStartColumn = startColumn;
        mStartOffset = startOffset;
        mEndLine = endLine;
        mEndColumn = endColumn;
        mEndOffset = endOffset;
    }

    public SourcePosition(int lineNumber, int column, int offset) {
        mStartLine = mEndLine = lineNumber;
        mStartColumn = mEndColumn = column;
        mStartOffset = mEndOffset = offset;
    }

    private SourcePosition() {
        mStartLine = mStartColumn = mStartOffset = mEndLine = mEndColumn = mEndOffset = -1;
    }

    protected SourcePosition(SourcePosition copy) {
        mStartLine = copy.getStartLine();
        mStartColumn = copy.getStartColumn();
        mStartOffset = copy.getStartOffset();
        mEndLine = copy.getEndLine();
        mEndColumn = copy.getEndColumn();
        mEndOffset = copy.getEndOffset();
    }

    /**
     * Outputs positions as human-readable formatted strings.
     *
     * e.g.
     * <pre>84
     * 84-86
     * 84:5
     * 84:5-28
     * 85:5-86:47</pre>
     *
     * @return a human readable position.
     */
    @Override
    public String toString() {
        StringBuilder sB = new StringBuilder();
        sB.append(mStartLine);
        if (mStartColumn != -1) {
            sB.append(':');
            sB.append(mStartColumn);
        }
        if (mEndLine != -1) {

            if (mEndLine == mStartLine) {
                if (mEndColumn != -1 && mEndColumn != mStartColumn) {
                    sB.append('-');
                    sB.append(mEndColumn);
                }
            } else {
                sB.append('-');
                sB.append(mEndLine);
                if (mEndColumn != -1) {
                    sB.append(':');
                    sB.append(mEndColumn);
                }
            }
        }
        return sB.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SourcePosition) {
            SourcePosition other = (SourcePosition) obj;

            return other.mStartLine == mStartLine &&
                    other.mStartColumn == mStartColumn &&
                    other.mStartOffset == mStartOffset &&
                    other.mEndLine == mEndLine &&
                    other.mEndColumn == mEndColumn &&
                    other.mEndOffset == mEndOffset;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects
                .hashCode(mStartLine, mStartColumn, mStartOffset, mEndLine, mEndColumn, mEndOffset);
    }

    public int getStartLine() {
        return mStartLine;
    }

    public int getStartColumn() {
        return mStartColumn;
    }

    public int getStartOffset() {
        return mStartOffset;
    }


    public int getEndLine() {
        return mEndLine;
    }

    public int getEndColumn() {
        return mEndColumn;
    }

    public int getEndOffset() {
        return mEndOffset;
    }

}
