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

package com.android.profilerapp.memory;

import android.os.Debug;

public class ProfilerAppMemoryInfoProvider implements IMemoryInfoProvider {

    @Override
    public int GetFreedObjectsCount() {
        return Debug.getGlobalFreedCount();
    }

    @Override
    public int GetFreedObjectsSize() {
        return Debug.getGlobalFreedSize();
    }

    @Override
    public long GetManagedHeapMaxSize() {
        return (Runtime.getRuntime().maxMemory() + BYTE_TO_KILOBYTE - 1) / BYTE_TO_KILOBYTE;
    }

    @Override
    public long GetManagedHeapTotalSize() {
        return (Runtime.getRuntime().totalMemory() + BYTE_TO_KILOBYTE - 1) / BYTE_TO_KILOBYTE;
    }

    @Override
    public long GetManagedHeapAllocatedSize() {
        Runtime rt = Runtime.getRuntime();
        return ((rt.totalMemory() - rt.freeMemory()) + BYTE_TO_KILOBYTE - 1) / BYTE_TO_KILOBYTE;
    }

    @Override
    public long GetManagedHeapFreeSize() {
        return (Runtime.getRuntime().freeMemory() + BYTE_TO_KILOBYTE - 1) / BYTE_TO_KILOBYTE;
    }

    @Override
    public long GetNativeHeapTotalSize() {
        return (Debug.getNativeHeapSize() + BYTE_TO_KILOBYTE - 1) / BYTE_TO_KILOBYTE;
    }

    @Override
    public long GetNativeHeapAllocatedSize() {
        return (Debug.getNativeHeapAllocatedSize() + BYTE_TO_KILOBYTE - 1) / BYTE_TO_KILOBYTE;
    }

    @Override
    public long GetNativeHeapFreeSize() {
        return (Debug.getNativeHeapFreeSize() + BYTE_TO_KILOBYTE - 1) / BYTE_TO_KILOBYTE;
    }

    @Override
    public long GetTotalPssSize() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return info.getTotalPss();
    }

    @Override
    public long GetManagedPssSize() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return info.dalvikPss;
    }

    @Override
    public long GetNativePssSize() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return info.nativePss;
    }

    @Override
    public long GetOtherPssSize() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return info.otherPss;
    }
}
