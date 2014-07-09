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
import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.ByteBufferUtil;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import com.google.common.primitives.UnsignedInts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Set;

public class VmTraceParser {
    private static final int TRACE_MAGIC = 0x574f4c53; // 'SLOW'

    private static final String HEADER_SECTION_VERSION = "*version";
    private static final String HEADER_SECTION_THREADS = "*threads";
    private static final String HEADER_SECTION_METHODS = "*methods";
    private static final String HEADER_END = "*end";

    private static final String KEY_CLOCK = "clock";
    private static final String KEY_DATA_OVERFLOW = "data-file-overflow";
    private static final String KEY_VM = "vm";

    private final File mTraceFile;

    private final VmTraceData.Builder mTraceDataBuilder;
    private VmTraceData mTraceData;

    public VmTraceParser(File traceFile) {
        if (!traceFile.exists()) {
            throw new IllegalArgumentException(
                    "Trace file " + traceFile.getAbsolutePath() + " does not exist.");
        }
        mTraceFile = traceFile;
        mTraceDataBuilder = new VmTraceData.Builder();
    }

    public void parse() throws IOException {
        long headerLength = parseHeader(mTraceFile);
        ByteBuffer buffer = ByteBufferUtil.mapFile(mTraceFile, headerLength, ByteOrder.LITTLE_ENDIAN);
        parseData(buffer);
        computeTimingStatistics();
    }

    public VmTraceData getTraceData() {
        if (mTraceData == null) {
            mTraceData = mTraceDataBuilder.build();
        }

        return mTraceData;
    }

    static final int PARSE_VERSION = 0;
    static final int PARSE_THREADS = 1;
    static final int PARSE_METHODS = 2;
    static final int PARSE_OPTIONS = 4;

    /** Parses the trace file header and returns the offset in the file where the header ends. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    long parseHeader(File f) throws IOException {
        long offset = 0;
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(f), Charsets.US_ASCII));

            int mode = PARSE_VERSION;
            String line;
            while (true) {
                line = in.readLine();
                if (line == null) {
                    throw new IOException("Key section does not have an *end marker");
                }

                // Calculate how much we have read from the file so far.  The
                // extra byte is for the line ending not included by readLine().
                offset += line.length() + 1;

                if (line.startsWith("*")) {
                    if (line.equals(HEADER_SECTION_VERSION)) {
                        mode = PARSE_VERSION;
                        continue;
                    }
                    if (line.equals(HEADER_SECTION_THREADS)) {
                        mode = PARSE_THREADS;
                        continue;
                    }
                    if (line.equals(HEADER_SECTION_METHODS)) {
                        mode = PARSE_METHODS;
                        continue;
                    }
                    if (line.equals(HEADER_END)) {
                        break;
                    }
                }

                switch (mode) {
                    case PARSE_VERSION:
                        mTraceDataBuilder.setVersion(Integer.decode(line));
                        mode = PARSE_OPTIONS;
                        break;
                    case PARSE_THREADS:
                        parseThread(line);
                        break;
                    case PARSE_METHODS:
                        parseMethod(line);
                        break;
                    case PARSE_OPTIONS:
                        parseOption(line);
                        break;
                }
            }
        } finally {
            if (in != null) {
                try {
                    Closeables.close(in, true /* swallowIOException */);
                } catch (IOException e) {
                    // cannot happen
                }
            }
        }

        return offset;
    }

    /** Parses trace option formatted as a key value pair. */
    private void parseOption(String line) {
        String[] tokens = line.split("=");
        if (tokens.length == 2) {
            String key = tokens[0];
            String value = tokens[1];

            if (key.equals(KEY_CLOCK)) {
                if (value.equals("thread-cpu")) {
                    mTraceDataBuilder.setVmClockType(VmTraceData.VmClockType.THREAD_CPU);
                } else if (value.equals("wall")) {
                    mTraceDataBuilder.setVmClockType(VmTraceData.VmClockType.WALL);
                } else if (value.equals("dual")) {
                    mTraceDataBuilder.setVmClockType(VmTraceData.VmClockType.DUAL);
                }
            } else if (key.equals(KEY_DATA_OVERFLOW)) {
                mTraceDataBuilder.setDataFileOverflow(Boolean.parseBoolean(value));
            } else if (key.equals(KEY_VM)) {
                mTraceDataBuilder.setVm(value);
            } else {
                mTraceDataBuilder.setProperty(key, value);
            }
        }
    }

    /** Parses thread information comprising an integer id and the thread name */
    private void parseThread(String line) {
        int index = line.indexOf('\t');
        if (index < 0) {
            return;
        }

        try {
            int id = Integer.decode(line.substring(0, index));
            String name = line.substring(index).trim();
            mTraceDataBuilder.addThread(id, name);
        } catch (NumberFormatException ignored) {
        }
    }

    void parseMethod(String line) {
        String[] tokens = line.split("\t");
        long id;
        try {
            id = Long.decode(tokens[0]);
        } catch (NumberFormatException e) {
            return;
        }

        String className = tokens[1];
        String methodName = null;
        String signature = null;
        String pathname = null;
        int lineNumber = -1;
        if (tokens.length == 6) {
            methodName = tokens[2];
            signature = tokens[3];
            pathname = tokens[4];
            lineNumber = Integer.decode(tokens[5]);
            pathname = constructPathname(className, pathname);
        } else if (tokens.length > 2) {
            if (tokens[3].startsWith("(")) {
                methodName = tokens[2];
                signature = tokens[3];
            } else {
                pathname = tokens[2];
                lineNumber = Integer.decode(tokens[3]);
            }
        }

        mTraceDataBuilder.addMethod(id, new MethodInfo(id, className, methodName, signature,
                pathname, lineNumber));
    }

    private String constructPathname(String className, String pathname) {
        int index = className.lastIndexOf('/');
        if (index > 0 && index < className.length() - 1 && pathname.endsWith(".java")) {
            pathname = className.substring(0, index + 1) + pathname;
        }
        return pathname;
    }

    /**
     * Parses the data section of the trace. The data section comprises of a header followed
     * by a list of records.
     *
     * All values are stored in little-endian order.
     */
    private void parseData(ByteBuffer buffer) {
        int recordSize = readDataFileHeader(buffer);
        parseMethodTraceData(buffer, recordSize);
    }

    /**
     * Parses the list of records corresponding to each trace event (method entry, exit, ...)
     *  Record format v1:
     *  u1  thread ID
     *  u4  method ID | method action
     *  u4  time delta since start, in usec
     *
     * Record format v2:
     *  u2  thread ID
     *  u4  method ID | method action
     *  u4  time delta since start, in usec
     *
     * Record format v3:
     *  u2  thread ID
     *  u4  method ID | method action
     *  u4  time delta since start, in usec
     *  u4  wall time since start, in usec (when clock == "dual" only)
     *
     * 32 bits of microseconds is 70 minutes.
     */
    private void parseMethodTraceData(ByteBuffer buffer, int recordSize) {
        int methodId;
        int threadId;
        int version = mTraceDataBuilder.getVersion();
        VmTraceData.VmClockType vmClockType = mTraceDataBuilder.getVmClockType();
        while (buffer.hasRemaining()) {
            int threadTime;
            int globalTime;

            int positionStart = buffer.position();

            threadId = version == 1 ? buffer.get() : buffer.getShort();
            methodId = buffer.getInt();

            switch (vmClockType) {
                case WALL:
                    globalTime = buffer.getInt();
                    threadTime = globalTime;
                    break;
                case DUAL:
                    threadTime = buffer.getInt();
                    globalTime = buffer.getInt();
                    break;
                case THREAD_CPU:
                default:
                    threadTime = buffer.getInt();
                    globalTime = threadTime;
                    break;
            }

            int positionEnd = buffer.position();
            int bytesRead = positionEnd - positionStart;
            if (bytesRead < recordSize) {
                buffer.position(positionEnd + (recordSize - bytesRead));
            }

            int action = methodId & 0x03;
            TraceAction methodAction;
            switch (action) {
                case 0:
                    methodAction = TraceAction.METHOD_ENTER;
                    break;
                case 1:
                    methodAction = TraceAction.METHOD_EXIT;
                    break;
                case 2:
                    methodAction = TraceAction.METHOD_EXIT_UNROLL;
                    break;
                default:
                    throw new RuntimeException(
                            "Invalid trace action, expected one of method entry, exit or unroll.");
            }
            methodId = methodId & ~0x03;

            mTraceDataBuilder.addMethodAction(threadId, UnsignedInts.toLong(methodId), methodAction,
                    threadTime, globalTime);
        }
    }

    /**
     *  Parses the data header with the following format:
     *  u4  magic ('SLOW')
     *  u2  version
     *  u2  offset to data
     *  u8  start date/time in usec
     *  u2  record size in bytes (version >= 2 only)
     *  ... padding to 32 bytes

     * @param buffer byte buffer pointing to the header
     * @return record size for each data entry following the header
     */
    private int readDataFileHeader(ByteBuffer buffer) {
        int magic = buffer.getInt();
        if (magic != TRACE_MAGIC) {
            String msg = String.format("Error: magic number mismatch; got 0x%x, expected 0x%x\n",
                    magic, TRACE_MAGIC);
            throw new RuntimeException(msg);
        }

        // read version
        int version = buffer.getShort();
        if (version != mTraceDataBuilder.getVersion()) {
            String msg = String.format(
                    "Error: version number mismatch; got %d in data header but %d in options\n",
                    version, mTraceData.getVersion());
            throw new RuntimeException(msg);
        }
        if (version < 1 || version > 3) {
            String msg = String.format(
                    "Error: unsupported trace version number %d.  "
                            + "Please use a newer version of TraceView to read this file.",
                    version);
            throw new RuntimeException(msg);
        }

        // read offset
        int offsetToData = buffer.getShort() - 16;

        // read startWhen
        buffer.getLong();

        // read record size
        int recordSize;
        switch (version) {
            case 1:
                recordSize = 9;
                break;
            case 2:
                recordSize = 10;
                break;
            default:
                recordSize = buffer.getShort();
                offsetToData -= 2;
                break;
        }

        // Skip over offsetToData bytes
        while (offsetToData-- > 0) {
            buffer.get();
        }

        return recordSize;
    }

    private void computeTimingStatistics() {
        VmTraceData data = getTraceData();

        ProfileDataBuilder builder = new ProfileDataBuilder();
        for (ThreadInfo thread : data.getThreads()) {
            Call c = thread.getTopLevelCall();
            if (c == null) {
                continue;
            }

            builder.computeCallStats(c, null, thread);
        }

        for (Long methodId : builder.getMethodsWithProfileData()) {
            MethodInfo method = data.getMethod(methodId);
            method.setProfileData(builder.getProfileData(methodId));
        }
    }

    private static class ProfileDataBuilder {
        /** Maps method ids to their corresponding method data builders */
        private final Map<Long, MethodProfileData.Builder> mBuilderMap = Maps.newHashMap();

        public void computeCallStats(Call c, Call parent, ThreadInfo thread) {
            long methodId = c.getMethodId();
            MethodProfileData.Builder builder = getProfileDataBuilder(methodId);
            builder.addCallTime(c, parent, thread);
            builder.incrementInvocationCount(c, parent, thread);
            if (c.isRecursive()) {
                builder.setRecursive();
            }

            for (Call callee: c.getCallees()) {
                computeCallStats(callee, c, thread);
            }
        }

        @NonNull
        private MethodProfileData.Builder getProfileDataBuilder(long methodId) {
            MethodProfileData.Builder builder = mBuilderMap.get(methodId);
            if (builder == null) {
                builder = new MethodProfileData.Builder();
                mBuilderMap.put(methodId, builder);
            }
            return builder;
        }

        public Set<Long> getMethodsWithProfileData() {
            return mBuilderMap.keySet();
        }

        public MethodProfileData getProfileData(Long methodId) {
            return mBuilderMap.get(methodId).build();
        }
    }
}
