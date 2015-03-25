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

package com.android.ide.common.caching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import org.junit.Test;

/**
 */
public class CreatingCacheTest {

    private static class FakeFactory implements CreatingCache.ValueFactory<String, String> {
        @Override
        @NonNull
        public String create(@NonNull String key) {
            return key;
        }
    }

    private static class DelayedFactory implements CreatingCache.ValueFactory<String, String> {

        @Override
        @NonNull
        public String create(@NonNull String key) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {

            }
            return key;
        }
    }

    @Test
    public void testSingleThread() throws Exception {
        CreatingCache<String, String> cache = new CreatingCache<String, String>(new FakeFactory());

        String value1 = cache.get("key");
        assertEquals("key", value1);
        String value2 = cache.get("key");
        assertEquals("key", value2);
        //noinspection StringEquality
        assertTrue("repetitive calls give same instance", value1 == value2);
    }

    private static class CacheRunnable implements Runnable {

        private final CreatingCache<String, String> mCache;
        private final long mSleep;

        private String mResult;
        private InterruptedException mException;

        public CacheRunnable(
                CreatingCache<String, String> cache,
                long sleep) {
            mCache = cache;
            mSleep = sleep;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(mSleep);
                mResult = mCache.get("foo");
            } catch (InterruptedException e) {
                mException = e;
            }
        }

        public String getResult() {
            return mResult;
        }

        public InterruptedException getException() {
            return mException;
        }
    }

    @Test
    public void testMultiThread() throws Exception {
        final CreatingCache<String, String>
                cache = new CreatingCache<String, String>(new DelayedFactory());

        CacheRunnable runnable1 = new CacheRunnable(cache, 0);
        Thread t1 = new Thread(runnable1);
        t1.start();

        CacheRunnable runnable2 = new CacheRunnable(cache, 1000);
        Thread t2 = new Thread(runnable2);
        t2.start();

        t1.join();
        t2.join();

        assertEquals("foo", runnable1.getResult());
        assertEquals("foo", runnable2.getResult());
        //noinspection StringEquality
        assertTrue("repetitive calls give same instance", runnable1.getResult() == runnable2.getResult());
    }

    @Test(expected = IllegalStateException.class)
    public void testClear() throws Exception {
        final CreatingCache<String, String>
                cache = new CreatingCache<String, String>(new DelayedFactory());

        new Thread() {
            @Override
            public void run() {
                cache.get("foo");
            }
        }.start();

        Thread.sleep(1000);

        cache.clear();
    }
}