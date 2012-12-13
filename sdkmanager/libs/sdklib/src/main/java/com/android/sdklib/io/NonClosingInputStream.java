/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.sdklib.io;

import com.android.annotations.NonNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * Wraps an {@link InputStream} to change its closing behavior:
 * this makes it possible to ignore close operations or have them perform a
 * {@link InputStream#reset()} instead (if supported by the underlying stream)
 * or plain ignored.
 */
public class NonClosingInputStream extends FilterInputStream {

    private final InputStream mInputStream;
    private CloseBehavior mCloseBehavior = CloseBehavior.CLOSE;

    public enum CloseBehavior {
        /**
         * The behavior of {@link NonClosingInputStream#close()} is to close the
         * underlying input stream. This is the default.
         */
        CLOSE,
        /**
         * The behavior of {@link NonClosingInputStream#close()} is to ignore the
         * close request and do nothing.
         */
        IGNORE,
        /**
         * The behavior of {@link NonClosingInputStream#close()} is to call
         * {@link InputStream#reset()} on the underlying stream. This will
         * only succeed if the underlying stream supports it, e.g. it must
         * have {@link InputStream#markSupported()} return true <em>and</em>
         * the caller should have called {@link InputStream#mark(int)} at some
         * point before.
         */
        RESET
    }

    /**
     * Wraps an existing stream into this filtering stream.
     * @param in A non-null input stream.
     */
    public NonClosingInputStream(@NonNull InputStream in) {
        super(in);
        mInputStream = in;
    }

    /**
     * Returns the current {@link CloseBehavior}.
     * @return the current {@link CloseBehavior}. Never null.
     */
    public @NonNull CloseBehavior getCloseBehavior() {
        return mCloseBehavior;
    }

    /**
     * Changes the current {@link CloseBehavior}.
     *
     * @param closeBehavior A new non-null {@link CloseBehavior}.
     * @return Self for chaining.
     */
    public NonClosingInputStream setCloseBehavior(@NonNull CloseBehavior closeBehavior) {
        mCloseBehavior = closeBehavior;
        return this;
    }

    /**
     * Performs the requested {@code close()} operation, depending on the current
     * {@link CloseBehavior}.
     */
    @Override
    public void close() throws IOException {
        switch (mCloseBehavior) {
        case IGNORE:
            break;
        case RESET:
            mInputStream.reset();
            break;
        case CLOSE:
            mInputStream.close();
            break;
        }
    }
}
