/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.perflib.vmtrace;

import com.android.annotations.NonNull;
import com.android.utils.SparseArray;
import com.google.common.base.Joiner;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VmTraceParserTest extends TestCase {
    public void testParseHeader() throws IOException {
        File f = getFile("/header.trace");
        VmTraceParser parser = new VmTraceParser(f);
        parser.parseHeader(f);
        VmTraceData traceData = parser.getTraceData();

        assertEquals(3, traceData.getVersion());
        assertTrue(traceData.isDataFileOverflow());
        assertEquals(VmTraceData.ClockType.DUAL, traceData.getClockType());
        assertEquals("dalvik", traceData.getVm());

        SparseArray<String> threads = traceData.getThreads();
        assertEquals(2, threads.size());
        assertEquals("main", threads.get(1));
        assertEquals("AsyncTask #1", threads.get(11));

        Map<Long, MethodInfo> methods = traceData.getMethods();
        assertEquals(4, methods.size());

        MethodInfo info = traceData.getMethod(0x62830738);
        assertNotNull(info);
        assertEquals("android/graphics/Bitmap", info.className);
        assertEquals("access$100", info.methodName);
        assertEquals("(I)V", info.signature);
        assertEquals("android/graphics/BitmapF.java", info.srcPath);
        assertEquals(29, info.srcLineNumber);

        info = traceData.getMethod(0x6282b4b0);
        assertNotNull(info);
        assertEquals(-1, info.srcLineNumber);
    }

    private class CallFormatter implements Call.Formatter {
        private final Map<Long, MethodInfo> mMethodInfo;

        public CallFormatter(Map<Long, MethodInfo> methodInfo) {
            mMethodInfo = methodInfo;
        }

        @Override
        public String format(Call c) {
            MethodInfo info = mMethodInfo.get(c.getMethodId());
            return info == null ? Long.toString(c.getMethodId()) : info.getFullName();
        }
    }

    private void testTrace(String traceName, String threadName, String expectedCallSequence) throws IOException {
        VmTraceData traceData = getVmTraceData(traceName);

        int threadId = findThreadIdFromName(threadName, traceData.getThreads());
        assertTrue(String.format("Thread %s was not found in the trace", threadName), threadId > 0);

        Call call = traceData.getTopLevelCall(threadId);
        String actual = call.format(new CallFormatter(traceData.getMethods()));
        assertEquals(expectedCallSequence, actual);
    }

    public void testBasicTrace() throws IOException {
        String expected =
                          " -> AsyncTask #1.:  -> android/os/Debug.startMethodTracing: (Ljava/lang/String;)V -> android/os/Debug.startMethodTracing: (Ljava/lang/String;II)V -> dalvik/system/VMDebug.startMethodTracing: (Ljava/lang/String;II)V\n"
                        + "                    -> com/test/android/traceview/Basic.foo: ()V -> com/test/android/traceview/Basic.bar: ()I\n"
                        + "                    -> android/os/Debug.stopMethodTracing: ()V -> dalvik/system/VMDebug.stopMethodTracing: ()V";
        testTrace("/basic.trace", "AsyncTask #1", expected);
    }

    public void testMisMatchedTrace() throws IOException {
        String expected =
                  " -> AsyncTask #1.:  -> com/test/android/traceview/MisMatched.foo: ()V -> com/test/android/traceview/MisMatched.bar: ()V -> android/os/Debug.startMethodTracing: (Ljava/lang/String;)V -> android/os/Debug.startMethodTracing: (Ljava/lang/String;II)V -> dalvik/system/VMDebug.startMethodTracing: (Ljava/lang/String;II)V\n"
                + "                                                                                                                        -> com/test/android/traceview/MisMatched.baz: ()I\n"
                + "                    -> android/os/Debug.stopMethodTracing: ()V -> dalvik/system/VMDebug.stopMethodTracing: ()V";
        testTrace("/mismatched.trace", "AsyncTask #1", expected);
    }

    public void testExceptionTrace() throws IOException {
        String expected =
                  " -> AsyncTask #1.:  -> android/os/Debug.startMethodTracing: (Ljava/lang/String;)V -> android/os/Debug.startMethodTracing: (Ljava/lang/String;II)V -> dalvik/system/VMDebug.startMethodTracing: (Ljava/lang/String;II)V\n"
                + "                    -> com/test/android/traceview/Exceptions.foo: ()V -> com/test/android/traceview/Exceptions.bar: ()V -> com/test/android/traceview/Exceptions.baz: ()V -> java/lang/RuntimeException.<init>: ()V -> java/lang/Exception.<init>: ()V -> java/lang/Throwable.<init>: ()V -> java/util/Collections.emptyList: ()Ljava/util/List;\n"
                + "                                                                                                                                                                                                                                                                                          -> java/lang/Throwable.fillInStackTrace: ()Ljava/lang/Throwable; -> java/lang/Throwable.nativeFillInStackTrace: ()Ljava/lang/Object;\n"
                + "                    -> android/os/Debug.stopMethodTracing: ()V -> dalvik/system/VMDebug.stopMethodTracing: ()V";
        testTrace("/exception.trace", "AsyncTask #1", expected);
    }

    private int findThreadIdFromName(@NonNull String threadName,
            @NonNull SparseArray<String> threads) {
        for (int i = 0; i < threads.size(); i++) {
            int id = threads.keyAt(i);
            String name = threads.valueAt(i);
            if (threadName.equals(name)) {
                return id;
            }
        }

        return -1;
    }

    private VmTraceData getVmTraceData(String traceFilePath) throws IOException {
        VmTraceParser parser = new VmTraceParser(getFile(traceFilePath));
        parser.parse();
        return parser.getTraceData();
    }

    private File getFile(String path) {
        URL resource = getClass().getResource(path);
        // Note: When running from an IntelliJ, make sure the IntelliJ compiler settings treats
        // *.trace files as resources, otherwise they are excluded from compiler output
        // resulting in a NPE.
        assertNotNull(path + " not found", resource);
        return new File(resource.getFile());
    }
}
