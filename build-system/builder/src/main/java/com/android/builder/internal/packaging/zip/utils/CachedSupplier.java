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

package com.android.builder.internal.packaging.zip.utils;

import java.io.IOException;

/**
 * Supplier that will cache a computed value and always supply the same value. It can be used to
 * lazily compute data. For example:
 * <pre>
 * CachedSupplier&lt;Integer&gt; value = new CachedSupplier&lt;Integer&gt;() {
 *     protected Integer compute() {
 *         Integer result;
 *         // Do some expensive computation.
 *         return result;
 *     }
 * }
 *
 * if (a) {
 *     // We need the result of the expensive computation.
 *     Integer r = value.get();
 * }
 *
 * if (b) {
 *     // We also need the result of the expensive computation.
 *     Integer r = value.get();
 * }
 *
 * // If neither a nor b are true, we avoid doing the computation at all.
 * </pre>
 */
public abstract class CachedSupplier<T> {

    /**
     * The cached data, {@code null} if computation resulted in {@code null}.
     */
    private T mCached;

    /**
     * Is the current data in {@link #mCached} valid?
     */
    private boolean mValid;

    /**
     * Creates a new supplier.
     */
    public CachedSupplier() {
        mValid = false;
    }


    /**
     * Obtains the value.
     *
     * @return the value, either cached (if one exists) or computed
     * @throws IOException failed to compute the value
     */
    public synchronized T get() throws IOException {
        if (!mValid) {
            mCached = compute();
            mValid = true;
        }

        return mCached;
    }

    /**
     * Computes the supplier value. This method is only invoked once.
     *
     * @return the result of the computation, {@code null} is allowed and, if returned, then
     * {@link #get()} will also return {@code null}
     * @throws IOException failed to compute the value
     */
    protected abstract T compute() throws IOException;

    /**
     * Resets the cache forcing a {@link #compute()} next time {@link #get()} is invoked.
     */
    public synchronized void reset() {
        mValid = false;
    }

    /**
     * In some cases, we may be able to precompute the cache value (or load it from somewhere we
     * had previously stored it). This method allows the cache value to be loaded.
     * <p>
     * If this method is invoked, then an invocation of {@link #get()} will not trigger an
     * invocation of {@link #compute()}.
     *
     * @param t the new cache contents; will replace any currently cache content, if one exists
     */
    public synchronized void precomputed(T t) {
        mCached = t;
        mValid = true;
    }
}
