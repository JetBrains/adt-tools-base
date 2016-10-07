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

package com.android.builder.internal.packaging.zip;


import com.android.annotations.NonNull;
import com.android.builder.internal.packaging.zip.utils.CloseableByteSource;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;

/**
 * {@code ByteSource} that delegates all operations to another {@code ByteSource}. The other
 * byte source, the <em>delegate</em>, may be computed lazily.
 */
public class LazyDelegateByteSource extends CloseableByteSource {

    /**
     * Byte source where we delegate operations to.
     */
    @NonNull
    private final ListenableFuture<CloseableByteSource> mDelegate;

    /**
     * Creates a new byte source that delegates operations to the provided source.
     * @param delegate the source that will receive all operations
     */
    public LazyDelegateByteSource(@NonNull ListenableFuture<CloseableByteSource> delegate) {
        mDelegate = delegate;
    }

    /**
     * Obtains the delegate future.
     * @return the delegate future, that may be computed or not
     */
    @NonNull
    public ListenableFuture<CloseableByteSource> getDelegate() {
        return mDelegate;
    }

    /**
     * Obtains the byte source, waiting for the future to be computed.
     * @return the byte source
     * @throws IOException failed to compute the future :)
     */
    @NonNull
    private CloseableByteSource get() throws IOException {
        try {
            CloseableByteSource r = mDelegate.get();
            if (r == null) {
                throw new IOException("Delegate byte source computation resulted in null.");
            }

            return r;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for byte source computation.", e);
        } catch (ExecutionException e) {
            throw new IOException("Failed to compute byte source.", e);
        }
    }

    @Override
    public CharSource asCharSource(Charset charset) {
        try {
            return get().asCharSource(charset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream openBufferedStream() throws IOException {
        return get().openBufferedStream();
    }

    @Override
    public ByteSource slice(long offset, long length) {
        try {
            return get().slice(offset, length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isEmpty() throws IOException {
        return get().isEmpty();
    }

    @Override
    public long size() throws IOException {
        return get().size();
    }

    @Override
    public long copyTo(@NonNull OutputStream output) throws IOException {
        return get().copyTo(output);
    }

    @Override
    public long copyTo(@NonNull ByteSink sink) throws IOException {
        return get().copyTo(sink);
    }

    @Override
    public byte[] read() throws IOException {
        return get().read();
    }

    @Override
    public <T> T read(@NonNull ByteProcessor<T> processor) throws IOException {
        return get().read(processor);
    }

    @Override
    public HashCode hash(HashFunction hashFunction) throws IOException {
        return get().hash(hashFunction);
    }

    @Override
    public boolean contentEquals(@NonNull ByteSource other) throws IOException {
        return get().contentEquals(other);
    }

    @Override
    public InputStream openStream() throws IOException {
        return get().openStream();
    }

    @Override
    public void innerClose() throws IOException {
        get().close();
    }
}
