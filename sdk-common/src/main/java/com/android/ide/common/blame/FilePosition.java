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
import com.google.common.base.Objects;

import java.io.File;

/**
 * An immutable position in a text file with a reference to that file.
 */
public class FilePosition extends SourcePosition {

    private final File sourceFile;

    public FilePosition(@NonNull File sourceFile, @NonNull SourcePosition position) {
        super(position);
        this.sourceFile = sourceFile;
    }

    public static FilePosition wholeFile(@NonNull File sourceFile) {
        return new FilePosition(sourceFile, SourcePosition.UNKNOWN);
    }

    public File getSourceFile() {
        return sourceFile;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(sourceFile.getAbsoluteFile());
        if (getStartLine() != -1) {
            stringBuilder.append(':');
            stringBuilder.append(super.toString());
        }
        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FilePosition)) {
            return false;
        }
        FilePosition other = (FilePosition) obj;

        return other.getSourceFile().getAbsolutePath().equals(getSourceFile().getAbsolutePath()) &&
                super.equals((SourcePosition) other);

    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), sourceFile.getAbsolutePath());
    }
}
