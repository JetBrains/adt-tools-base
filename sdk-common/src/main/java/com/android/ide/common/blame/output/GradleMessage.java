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

package com.android.ide.common.blame.output;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.SourceFragmentPositionRange;
import com.google.common.base.Objects;
import com.google.gson.annotations.SerializedName;

/**
 * Message produced by tools invoked when building an Android project.
 */
public class GradleMessage {

    @SerializedName("kind")
    @NonNull
    private final Kind mKind;

    @SerializedName("text")
    @NonNull
    private final String mText;

    @SerializedName("sourcePath")
    @Nullable
    private final String mSourcePath;

    @SerializedName("position")
    @NonNull
    private final SourceFragmentPositionRange mPosition;

    @SerializedName("original")
    @NonNull
    private final String mOriginal;

    public GradleMessage(@NonNull Kind kind, @NonNull String text) {
        this(kind, text, null /* sourcePath */, SourceFragmentPositionRange.UNKNOWN, text);
    }


    public GradleMessage(@NonNull Kind kind,
                         @NonNull String text,
                         @Nullable String sourcePath,
                         @NonNull SourceFragmentPositionRange position,
                         @NonNull String original) {
        mKind = kind;
        mText = text;
        mSourcePath = sourcePath;
        mPosition = position;
        mOriginal = original;
    }

    public GradleMessage(@NonNull Kind kind, @NonNull String text, @Nullable String sourcePath, int line, int column) {
        this(kind, text, sourcePath, new SourceFragmentPositionRange(line, column, -1), text);
    }

    @NonNull
    public Kind getKind() {
        return mKind;
    }

    @NonNull
    public String getText() {
        return mText;
    }

    @Nullable
    public String getSourcePath() {
        return mSourcePath;
    }

    public int getLineNumber() {
        return mPosition.getStartLine();
    }

    public int getColumn() {
        return mPosition.getStartColumn();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GradleMessage that = (GradleMessage) o;

        return Objects.equal(mPosition, that.mPosition) &&
                mKind == that.mKind &&
                Objects.equal(mSourcePath, that.mSourcePath) &&
                Objects.equal(mText, that.mText);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mPosition, mKind, mSourcePath, mText);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "kind=" + mKind +
                ", text=\"" + mText + '\"' +
                ", sourcePath=" + mSourcePath +
                ", position=" + mPosition.toString() +
                ']';
    }

    public SourceFragmentPositionRange getPosition() {
        return mPosition;
    }

    public String getOriginal() {
        return mOriginal;
    }

    public enum Kind {
        ERROR, WARNING, INFO, STATISTICS, SIMPLE;

        @Nullable
        public static Kind findIgnoringCase(@NonNull String s) {
            for (Kind kind : values()) {
                if (kind.toString().equalsIgnoreCase(s)) {
                    return kind;
                }
            }
            return null;
        }
    }
}
