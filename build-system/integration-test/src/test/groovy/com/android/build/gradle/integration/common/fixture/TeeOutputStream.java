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

package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream that duplicates write to two other OutputStream.
 */
public class TeeOutputStream extends OutputStream {

    @NonNull
    private final OutputStream stream1;
    @NonNull
    private final OutputStream stream2;

    public TeeOutputStream(@NonNull OutputStream stream1, @NonNull OutputStream stream2) {
        this.stream1 = stream1;
        this.stream2 = stream2;
    }

    @Override
    public void write(int b) throws IOException {
        stream1.write(b);
        stream2.write(b);
    }

    @Override
    public void write(@NonNull byte[] b) throws IOException {
        stream1.write(b);
        stream2.write(b);
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        stream1.write(b, off, len);
        stream2.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        stream1.flush();
        stream2.flush();
    }

    @Override
    public void close() throws IOException {
        stream1.close();
        stream2.close();
    }
}
