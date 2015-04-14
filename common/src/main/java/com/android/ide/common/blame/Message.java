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

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.util.List;

@Immutable
public final class Message {

    @NonNull
    private final Kind mKind;

    @NonNull
    private final String mText;

    @NonNull
    private final List<SourceFilePosition> mSourceFilePositions;

    public Message(@NonNull Kind kind,
            @NonNull String text,
            @NonNull SourceFilePosition sourceFilePosition,
            @NonNull SourceFilePosition... sourceFilePositions) {
        mKind = kind;
        mText = text;
        mSourceFilePositions = ImmutableList.<SourceFilePosition>builder()
                .add(sourceFilePosition).add(sourceFilePositions).build();
    }

    /*package*/ Message(@NonNull Kind kind,
            @NonNull String text,
            @NonNull List<SourceFilePosition> positions) {
        mKind = kind;
        mText = text;
        if (positions.isEmpty()) {
            mSourceFilePositions = ImmutableList.of(SourceFilePosition.UNKNOWN);
        } else {
            mSourceFilePositions = positions;
        }
    }


    @NonNull
    public Kind getKind() {
        return mKind;
    }

    @NonNull
    public String getText() {
        return mText;
    }

    /**
     * Returns a list of sourceFilePositions. Will always contain at least one item.
     */
    @NonNull
    public List<SourceFilePosition> getSourceFilePositions() {
        return mSourceFilePositions;
    }

    public enum Kind {
        ERROR, WARNING, INFO, STATISTICS, UNKNOWN
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Message)) {
            return false;
        }
        Message that = (Message) o;
        return Objects.equal(mKind, that.mKind) &&
                Objects.equal(mText, that.mText) &&
                Objects.equal(mSourceFilePositions, mSourceFilePositions);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mKind, mText, mSourceFilePositions);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("kind", mKind).add("text", mText)
                .add("sources", mSourceFilePositions).toString();
    }
}
