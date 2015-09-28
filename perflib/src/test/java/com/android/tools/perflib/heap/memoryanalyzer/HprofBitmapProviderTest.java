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
package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.ddmlib.BitmapDecoder;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;

import junit.framework.TestCase;

import java.awt.image.BufferedImage;
import java.io.File;

public class HprofBitmapProviderTest extends TestCase {
    public static final int ARGB_565_INSTANCE = 0x12cff7c0;

    public static final int ARGB_8888_MUTABLE = 0x12cff780;

    public static final int ARGB_8888_INSTANCE = 0x12cff740;

    public static final int BITMAP_DRAWABLE_INSTANCE = 0x12cb0c40;

    public static final int ACTIVITY_INSTANCE = 0x12c722a0;

    private static Snapshot mSnapshot;

    private static Heap mAppHeap;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        File testHprofFile = new File(
                getClass().getResource("/bitmap_test.android-hprof").getFile());
        assert testHprofFile.exists();
        mSnapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(testHprofFile));
        mAppHeap = mSnapshot.getHeap("app");
        assert mAppHeap != null;
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        mSnapshot.dispose();
        mSnapshot = null;
        mAppHeap = null;
    }

    public void testGetBitmapFromBitmapBitmap() {
        Instance bitmapDrawable = mAppHeap.getInstance(ARGB_565_INSTANCE);
        boolean isBitmap = HprofBitmapProvider.canGetBitmapFromInstance(bitmapDrawable);
        assert isBitmap;
    }

    public void testGetBitmapFromBitmapDrawable() {
        Instance bitmapDrawable = mAppHeap.getInstance(BITMAP_DRAWABLE_INSTANCE);
        boolean isBitmap = HprofBitmapProvider.canGetBitmapFromInstance(bitmapDrawable);
        assert isBitmap;
    }

    public void testFailGetBitmapFromWrongObject() {
        Instance bitmapDrawable = mAppHeap.getInstance(ACTIVITY_INSTANCE);
        boolean isBitmap = HprofBitmapProvider.canGetBitmapFromInstance(bitmapDrawable);
        assert !isBitmap;
    }

    public void testDecodeARGB888() throws Exception {
        Instance argb888Instance = mAppHeap.getInstance(ARGB_8888_INSTANCE);
        BufferedImage bitmap = BitmapDecoder.getBitmap(new HprofBitmapProvider(argb888Instance));
        assert bitmap != null;
    }

    public void testDecodeMutableBitmapWithBigBuffer() throws Exception {
        // 360x360 mutable bitmap with buffer length 640000
        Instance argb888MutableBitmap = mAppHeap.getInstance(ARGB_8888_MUTABLE);
        BufferedImage bitmap = BitmapDecoder
                .getBitmap(new HprofBitmapProvider(argb888MutableBitmap));
        assert bitmap != null;
    }

    public void testFailDecodeWrongInstance() throws Exception {
        Instance argb565Bitmap = mAppHeap.getInstance(ACTIVITY_INSTANCE);
        try {
            BitmapDecoder.getBitmap(new HprofBitmapProvider(argb565Bitmap));
        } catch (RuntimeException e) {
            return;
        }
        fail("BitmapDecoder should've thrown an error when given the wrong instance.");
    }
}
