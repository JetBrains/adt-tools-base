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

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Process;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.android.profilerapp.R;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/*
 * Fragment view for simulating and visualizing different memory behaviors, including
 * - Graphs for Java/Native Free vs Alloc size in heap, Java/Native PSS
 * - Commands to allocate/deallocate/churn java and native memory
 */
public class MemoryFragment extends Fragment implements View.OnClickListener {

    static {
        System.loadLibrary("profilermodule");
    }

    private native void jniAllocIntArray(int bytes, boolean initialize);
    private native void jniLeakIntArrays();
    private native void jniFreeIntArrays();
    private native void jniAllocTempIntArray(int bytes, boolean initialize);
    private native void jniFreeTempIntArray();

    private static int CHURN_RATE = 50; // ms
    private static int PROFILE_RATE = 50; // ms

    private IMemoryInfoProvider mInfoProvider = new ProfilerAppMemoryInfoProvider();
    private Object mLock = new Object();
    private Handler mProfilerHandler = new Handler();
    private int mPrevFreedCount = -1;

    private List<Bitmap> mBitmaps = new ArrayList<Bitmap>();
    private List<int[]> mInts = new ArrayList<int[]>();

    private boolean mChurnSmallIntAllocation = false;
    private boolean mChurnSmallBitmapAllocation = false;
    private boolean mChurnSmallJniAllocation = false;

    private View mFragmentView;
    private TextView mTextArtHeap;
    private TextView mTextNativeHeap;
    private TextView mTextPss;
    private MemoryGraphView mGraphArtHeap;
    private MemoryGraphView mGraphNativeHeap;
    private MemoryGraphView mGraphPss;
    private MemorySmapsView mMappingPss;

    private Thread mProfilerThread = null;
    private Thread mChurnAllocatorThread = null;
    private boolean mIsProfilerRunning = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mProfilerHandler = new Handler();

        // Deprecated - currently used for detecting when GC happens
        Debug.startAllocCounting();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View mFragmentView = inflater.inflate(R.layout.fragment_memory, container, false);

        mTextArtHeap = ((TextView) mFragmentView.findViewById(R.id.textArtHeap));
        mGraphArtHeap = (MemoryGraphView) mFragmentView.findViewById(R.id.graphArtHeap);
        mTextNativeHeap = ((TextView) mFragmentView.findViewById(R.id.textNativeHeap));
        mGraphNativeHeap = (MemoryGraphView) mFragmentView.findViewById(R.id.graphNativeHeap);
        mTextPss = ((TextView) mFragmentView.findViewById(R.id.textPss));
        mGraphPss = (MemoryGraphView) mFragmentView.findViewById(R.id.graphPss);
        mMappingPss = (MemorySmapsView) mFragmentView.findViewById(R.id.mappingPss);

        // Setup click handlers for all the buttons
        Button smapsInfo = (Button) mFragmentView.findViewById(R.id.buttonGatherSmapsInfo);
        smapsInfo.setOnClickListener(this);
        Button toggleProfile = (Button) mFragmentView.findViewById(R.id.buttonToggleProfile);
        toggleProfile.setOnClickListener(this);
        Button clearGraphs = (Button) mFragmentView.findViewById(R.id.buttonClearGraphs);
        clearGraphs.setOnClickListener(this);
        Button forceGc = (Button) mFragmentView.findViewById(R.id.buttonForceGc);
        forceGc.setOnClickListener(this);
        Button allocateBigInt = (Button) mFragmentView.findViewById(R.id.buttonAllocateBigInt);
        allocateBigInt.setOnClickListener(this);
        Button allocateBigIntUninit = (Button) mFragmentView.findViewById(R.id.buttonAllocateBigIntUninit);
        allocateBigIntUninit.setOnClickListener(this);
        Button releaseBigInts = (Button) mFragmentView.findViewById(R.id.buttonReleaseBigInts);
        releaseBigInts.setOnClickListener(this);
        Button bigBitmap = (Button) mFragmentView.findViewById(R.id.buttonBigBitmap);
        bigBitmap.setOnClickListener(this);
        Button bigBitmapUninit = (Button) mFragmentView.findViewById(R.id.buttonBigBitmapUninit);
        bigBitmapUninit.setOnClickListener(this);
        Button releaseBigBitmaps = (Button) mFragmentView.findViewById(R.id.buttonReleaseBigBitmaps);
        releaseBigBitmaps.setOnClickListener(this);
        Button intChurn = (Button) mFragmentView.findViewById(R.id.buttonIntChurn);
        intChurn.setOnClickListener(this);
        Button bitmapChurn = (Button) mFragmentView.findViewById(R.id.buttonBitmapChurn);
        bitmapChurn.setOnClickListener(this);
        Button jniBigArray = (Button) mFragmentView.findViewById(R.id.buttonJniBigArray);
        jniBigArray.setOnClickListener(this);
        Button allocateJniBigArrayUninit = (Button) mFragmentView.findViewById(R.id.buttonAllocateJniBigArrayUninit);
        allocateJniBigArrayUninit.setOnClickListener(this);
        Button releaseJniBigArrays = (Button) mFragmentView.findViewById(R.id.buttonReleaseJniBigArrays);
        releaseJniBigArrays.setOnClickListener(this);
        Button leakAllocatedJniBigArrays = (Button) mFragmentView.findViewById(R.id.buttonLeakAllocatedJniBigArrays);
        leakAllocatedJniBigArrays.setOnClickListener(this);
        Button nativeChurn = (Button) mFragmentView.findViewById(R.id.buttonNativeChurn);
        nativeChurn.setOnClickListener(this);

        return mFragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Churn Allocator Thread Setup
        mChurnAllocatorThread = new Thread(new ChurnAllocatorRunnable());
        mChurnAllocatorThread.start();

        // Profiler Thread setup
        mProfilerThread = new Thread(new MemoryInfoRunnable());
        mProfilerThread.start();
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            mChurnAllocatorThread.interrupt();
            mChurnAllocatorThread.join();
            mChurnAllocatorThread = null;

            mProfilerThread.interrupt();
            mProfilerThread.join();
            mProfilerThread = null;
        } catch (InterruptedException e) {
            System.out.println(e.toString());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Deprecated - currently used for detecting when GC happens
        Debug.stopAllocCounting();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonGatherSmapsInfo:
                graphSmapsInfo(v);
                break;
            case R.id.buttonToggleProfile:
                toggleProfile(v);
                break;
            case R.id.buttonClearGraphs:
                clearGraphs(v);
                break;
            case R.id.buttonForceGc:
                forceGarbageCollect(v);
                break;
            case R.id.buttonAllocateBigInt:
                allocateBigIntArray(v, true);
                break;
            case R.id.buttonAllocateBigIntUninit:
                allocateBigIntArray(v, false);
                break;
            case R.id.buttonReleaseBigInts:
                releaseBigIntArrays(v);
                break;
            case R.id.buttonBigBitmap:
                allocateBigBitmapArray(v, true);
                break;
            case R.id.buttonBigBitmapUninit:
                allocateBigBitmapArray(v, false);
                break;
            case R.id.buttonReleaseBigBitmaps:
                releaseBigBitmapArrays(v);
                break;
            case R.id.buttonIntChurn:
                toggleChurnIntAllocation(v);
                break;
            case R.id.buttonBitmapChurn:
                toggleChurnBitmapAllocation(v);
                break;
            case R.id.buttonJniBigArray:
                allocateJniBigArray(v, true);
                break;
            case R.id.buttonAllocateJniBigArrayUninit:
                allocateJniBigArray(v, false);
                break;
            case R.id.buttonReleaseJniBigArrays:
                releaseJniBigArrays(v);
                break;
            case R.id.buttonLeakAllocatedJniBigArrays:
                leakAllocatedJniBigArrays(v);
                break;
            case R.id.buttonNativeChurn:
                toggleChurnJniAllocation(v);
                break;
            default:
                break;
        }

    }

    private void toggleProfile(View view) {
        Button button = (Button)view;
        if (button == null) {
            return;
        }

        mIsProfilerRunning = !mIsProfilerRunning;
        String text = mIsProfilerRunning ? "Pause" : "Start";
        button.setText(text);
    }

    private void clearGraphs(View view) {
        synchronized (mLock) {
            mGraphArtHeap.clearData();
            mGraphNativeHeap.clearData();
            mGraphPss.clearData();
            mMappingPss.clearData();

            // Backward-compatbility - not needed from 3.0+
            for (Bitmap bm : mBitmaps){
                bm.recycle();
            }
        }
    }

    private void forceGarbageCollect(View view) {
        Runtime.getRuntime().gc();
    }

    /*
     * Allocate 1MB of integers
     * If init is true, assign values to the integers to dirty the memory space,
     * Other the memory usage is only accounted for in the heap but not PSS.
     */
    private void allocateBigIntArray(View view, boolean init) {
        int[] ints = new int[256*1024];
        if (init) {
            for (int i = 0; i < ints.length; i++) {
                ints[i] = i;
            }
        }
        mInts.add(ints);
    }

    private void releaseBigIntArrays(View view) {
        mInts.clear();
    }

    private void toggleChurnIntAllocation(View view) {
        mChurnSmallIntAllocation = !mChurnSmallIntAllocation;

        String text = mChurnSmallIntAllocation ? "Stop Churn" : "Start Churn";
        ((Button) view).setText(text);
    }

    /*
     * Allocate 1MB of bitmap data
     * If init is true, assign values to the bitmaps to dirty the memory space,
     * Other the memory usage is only accounted for in the heap but not PSS.
     */
    private void allocateBigBitmapArray(View view, boolean init) {
        Bitmap map = Bitmap.createBitmap(1024,1024,Bitmap.Config.ALPHA_8);
        if (init) {
            for (int i = 0; i < map.getHeight(); i++) {
                for (int j = 0; j < map.getWidth(); j++) {
                    map.setPixel(j, i, 0);
                }
            }
        }
        mBitmaps.add(map);
    }

    private void releaseBigBitmapArrays(View view) {
        mBitmaps.clear();
    }

    private void toggleChurnBitmapAllocation(View view) {
        mChurnSmallBitmapAllocation = !mChurnSmallBitmapAllocation;

        String text = mChurnSmallBitmapAllocation ? "Stop Churn" : "Start Churn";
        ((Button) view).setText(text);
    }

    private void allocateJniBigArray(View view, boolean init) {
        jniAllocIntArray(1024 * 256, init);    // 1Mb
    }

    private void releaseJniBigArrays(View view) {
        jniFreeIntArrays();
    }

    private void leakAllocatedJniBigArrays(View view) {
        jniLeakIntArrays();
    }

    private void toggleChurnJniAllocation(View view) {
        Button button = (Button)view;
        if (button == null) {
            return;
        }

        mChurnSmallJniAllocation = !mChurnSmallJniAllocation;
        String text = mChurnSmallJniAllocation ? "Stop Churn" : "Start Churn";
        button.setText(text);
    }

    private void graphSmapsInfo(View view) {
        new SmapsParserTask().execute();
    }

    /*
     * Async task to parse the smaps virtual file living inside /proc/pid/
     * which gives us the memory composition (i.e. private/shared dirty) categorized
     * by heaps/libraries/etc that the app is currently using.
     * The data is passed onto the MemoryGraphView for render when the task is complete.
     * NOTE - this is just a showcase of what we can get, not sure how useful the data actually is
     */
    private class SmapsParserTask extends AsyncTask<Void, Void, Set<Map.Entry<String, long[]>>> {
        @Override
        protected Set<Map.Entry<String, long[]>> doInBackground(Void... params) {
            HashMap<String, long[]> pathSizeAllocation = new HashMap<String, long[]>();
            try {
                RandomAccessFile smapsFile = new RandomAccessFile("/proc/" + android.os.Process.myPid() + "/smaps", "r");

                /* sample format of smaps entry:
                    bf338000-bfb37000 rw-p 00000000 00:00 0          [stack]
                    Size:               8188 kB
                    Rss:                  36 kB
                    Pss:                  36 kB
                    Shared_Clean:          0 kB
                    Shared_Dirty:          0 kB
                    Private_Clean:         0 kB
                    Private_Dirty:        36 kB
                    Referenced:           36 kB
                    Anonymous:            36 kB
                    AnonHugePages:         0 kB
                    Swap:                  0 kB
                    KernelPageSize:        4 kB
                    MMUPageSize:           4 kB
                    Locked:                0 kB
                    VmFlags: rd wr mr mw me gd ac
                 */
                String map, size, rss, pss, sharedClean, sharedDirty, privateClean, privateDirty;

                pathSizeAllocation.clear();
                String[] mapSplit;
                long mappingSize, pageRss, pagePss, sharedPages, privatePages;
                long totalMappingSize = 0, totalRss = 0, totalPss = 0, totalUss = 0;
                int entryCount = 0;

                parseSmaps: {
                    while ((map = smapsFile.readLine()) != null) {
                        // if line is not beginning of entry as defined by the startAddress-endAddress format, skips ahead
                        mapSplit = map.split(" +");
                        while (mapSplit[0].split("-").length == 1) {
                            map = smapsFile.readLine();
                            if (map == null) {
                                break parseSmaps;
                            }
                            mapSplit = map.split(" +");
                        }

                        entryCount++;

                        // Read remaining info from the mapping entry
                        size = smapsFile.readLine();
                        rss = smapsFile.readLine();
                        pss = smapsFile.readLine();
                        sharedClean = smapsFile.readLine();
                        sharedDirty = smapsFile.readLine();
                        privateClean = smapsFile.readLine();
                        privateDirty = smapsFile.readLine();

                        // Bookkeeping
                        mappingSize = Long.parseLong(size.split(" +")[1]);
                        pageRss = Long.parseLong(rss.split(" +")[1]);
                        pagePss = Long.parseLong(pss.split(" +")[1]);
                        totalMappingSize += mappingSize;
                        totalRss += pageRss;
                        totalPss += pagePss;

                        sharedPages = Long.parseLong(sharedClean.split(" +")[1]);
                        sharedPages += Long.parseLong(sharedDirty.split(" +")[1]);

                        privatePages = Long.parseLong(privateClean.split(" +")[1]);
                        privatePages += Long.parseLong(privateDirty.split(" +")[1]);

                        totalUss += privatePages;

                        String path = mapSplit.length > 5 ? mapSplit[5] : " ";
                        long[] pathInfo = pathSizeAllocation.get(path);
                        if (pathInfo == null) {
                            pathInfo = new long[5];
                            pathSizeAllocation.put(path, pathInfo);
                        }

                        pathInfo[0] += sharedPages;
                        pathInfo[1] += privatePages;
                        pathInfo[2] += pagePss;
                        pathInfo[3] += pageRss;
                        pathInfo[4] += mappingSize;
                    }
                }

                // total size of virtual memory space, and PSS
                System.out.println("Num smaps Entry: " + entryCount);
                System.out.println("Process PSS: " + totalPss);
                System.out.println("Process RSS: " + totalRss);
                System.out.println("Process USS: " + totalUss);
                System.out.println("Process Virtual Memory Space: " + totalMappingSize);

            } catch (IOException ignored) { }

            return pathSizeAllocation.entrySet();
        }

        @Override
        protected void onPostExecute(Set<Map.Entry<String, long[]>> entries) {
            super.onPostExecute(entries);
            mMappingPss.addData(true, entries);
        }
    }

    /*
     * Memory churn thread
     */
    private class ChurnAllocatorRunnable implements Runnable {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            try {
                Random rand = new Random();
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(CHURN_RATE);

                    if (mChurnSmallIntAllocation) {
                        int[] ints = new int[256 * rand.nextInt(1024 * 5)];
                        for (int i = 0; i < ints.length; i++) {
                            ints[i] = i;
                        }
                    }

                    if (mChurnSmallBitmapAllocation) {
                        Bitmap map = Bitmap.createBitmap(1024, rand.nextInt(1024 * 5), Bitmap.Config.ALPHA_8);
                        for (int i = 0; i < map.getHeight(); i++) {
                            for (int j = 0; j < map.getWidth(); j++) {
                                map.setPixel(j, i, 0);
                            }
                        }
                    }

                    jniFreeTempIntArray();
                    if (mChurnSmallJniAllocation) {
                        jniAllocTempIntArray(256 * rand.nextInt(1024 * 5), true);
                    }
                }
            } catch (InterruptedException e) {
                System.out.println("Allocator thread interrupted!");
            }
        }
    }

    /*
     * Thread with Handler to stream the java/native heap/pss data to the various graphs
     */
    private class MemoryInfoRunnable implements Runnable {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (mIsProfilerRunning) {
                        Thread.sleep(PROFILE_RATE);

                        mProfilerHandler.post(new Runnable() {
                            @Override
                            public void run() {

                                int currentFreedCount = mInfoProvider.GetFreedObjectsCount();
                                boolean gcHappened = currentFreedCount != mPrevFreedCount;
                                if (gcHappened) {
                                    mPrevFreedCount = currentFreedCount;
                                }

                                long totalMemory = mInfoProvider.GetManagedHeapTotalSize();
                                long allocMemory = mInfoProvider.GetManagedHeapAllocatedSize();
                                mTextArtHeap.setText(String.format("ART: used - %d, alloc - %d", totalMemory, allocMemory));
                                mGraphArtHeap.addPoint((int)allocMemory, (int)totalMemory, gcHappened);

                                totalMemory = mInfoProvider.GetNativeHeapTotalSize();
                                allocMemory = mInfoProvider.GetNativeHeapAllocatedSize();
                                mTextNativeHeap.setText(String.format("NATIVE: used - %d, alloc - %d", totalMemory, allocMemory));
                                mGraphNativeHeap.addPoint((int)allocMemory, (int)totalMemory, false);

                                long totalPss = mInfoProvider.GetTotalPssSize();
                                long artPss = mInfoProvider.GetManagedPssSize();
                                long nativePss = mInfoProvider.GetNativePssSize();
                                mTextPss.setText(String.format("PSS: total - %d, art - %d, native - %d", totalPss, artPss, nativePss));
                                mGraphPss.addPoint((int)nativePss, (int)(artPss + nativePss), false);
                            }
                        });
                    }
                }
            } catch (InterruptedException e) {
                System.out.println("Profiler thread interrupted!");
            }

        }
    }
}
