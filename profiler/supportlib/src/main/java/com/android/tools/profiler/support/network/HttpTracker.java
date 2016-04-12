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
package com.android.tools.profiler.support.network;

import android.util.Log;
import com.android.tools.profiler.support.network.HttpConnectionTracker;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;


/**
 * This is the factory for the {@link HttpConnectionTracker} instances
 */
final class HttpTracker {

    /**
     * Wraps an InputStream to enable the network profiler capturing of response body
     */
    static final class InputStreamTracker extends FilterInputStream {

        private Connection myConnectionTracker;
        private boolean myFirstRead = true;

        InputStreamTracker(InputStream wrapped, Connection connectionTracker) {
            super(wrapped);
            myConnectionTracker = connectionTracker;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public void mark(int readLimit) {
            throw new UnsupportedOperationException("mark() not supported");
        }

        @Override
        public void reset() throws IOException {
            throw new UnsupportedOperationException("reset() not supported");
        }

        @Override
        public void close() throws IOException {
            super.close();
            Log.i("HTTP_CloseInput", myConnectionTracker.myURL);
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            return read(buffer, 0, buffer.length);
        }

        @Override
        public int read() throws IOException {
            if (myFirstRead) {
                Log.i("HTTP_Read", myConnectionTracker.myURL);
                myFirstRead = false;
            }
            return super.read();
        }

        @Override
        public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            if (myFirstRead) {
                Log.i("HTTP_Read", myConnectionTracker.myURL);
                myFirstRead = false;
            }
            return super.read(buffer, byteOffset, byteCount);
        }

        @Override
        public long skip(long byteCount) throws IOException {
            if (myFirstRead) {
                Log.i("HTTP_Read", myConnectionTracker.myURL);
                myFirstRead = false;
            }
            return super.skip(byteCount);
        }
    }

    /**
     * Wraps an OutputStream to enable the network profiler capturing of request body
     */
    static final class OutputStreamTracker extends FilterOutputStream {

        private Connection myConnectionTracker;
        private boolean myFirstWrite = true;

        OutputStreamTracker(OutputStream wrapped, Connection connectionTracker) {
            super(wrapped);
            myConnectionTracker = connectionTracker;
        }

        @Override
        public void close() throws IOException {
            super.close();
            Log.i("HTTP_CloseOutput", myConnectionTracker.myURL);
        }

        @Override
        public void flush() throws IOException {
            super.flush();
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            if (myFirstWrite) {
                Log.i("HTTP_Write", myConnectionTracker.myURL);
                myFirstWrite = false;
            }
            super.write(buffer, offset, length);
        }

        @Override
        public void write(int oneByte) throws IOException {
            if (myFirstWrite) {
                Log.i("HTTP_Write", myConnectionTracker.myURL);
                myFirstWrite = false;
            }
            super.write(oneByte);
        }

        @Override
        public void write(byte[] buffer) throws IOException {
            if (myFirstWrite) {
                Log.i("HTTP_Write", myConnectionTracker.myURL);
                myFirstWrite = false;
            }
            write(buffer, 0, buffer.length);
        }
    }


    /**
     * This is the concrete AndroidStudio implementation of the public HTTP tracking interface.
     * We're passing the HTTP events and content to the AndroidStudio network profiler.
     *
     * Note that the HTTP stacks using {@link HttpConnectionTracker} should not care or know about
     * the details of the implementation of the interface.
     */
    static final class Connection implements HttpConnectionTracker {

        private String myURL;
        private StackTraceElement[] myCallstack;

        private Connection(String url, StackTraceElement[] callstack) {
            myURL = url;
            myCallstack = callstack;

            StringBuilder s = new StringBuilder();
            s.append(String.format("%s\n", myURL));
            for (StackTraceElement e : callstack) {
                s.append(String.format("%s\n", e.toString()));
            }

            Log.i("HTTP_PreConnect", s.toString());
        }

        @Override
        public void disconnect() {
            Log.i("HTTP_Disconnect", myURL);
        }

        @Override
        public void error(String status) {
            Log.i("HTTP_Error", String.format("%s (%s)", myURL, status));
        }

        @Override
        public void trackRequest(String method, Map<String, List<String>> fields) {

            StringBuilder s = new StringBuilder();
            s.append(String.format("%s (%s)\n", myURL, method));
            for (Map.Entry<String, List<String>> e : fields.entrySet()) {
                s.append(String.format("    %s = ", e.getKey()));
                for (String val : e.getValue()) {
                    s.append(String.format("%s ; ", val));
                }
                s.append("\n");
            }

            Log.i("HTTP_Request", s.toString());
        }

        @Override
        public void trackResponse(String response, Map<String, List<String>> fields) {

            StringBuilder s = new StringBuilder();
            s.append(String.format("%s (%s)\n", myURL, response));
            for (Map.Entry<String, List<String>> e : fields.entrySet()) {
                s.append(String.format("%s = ", e.getKey()));
                for (String val : e.getValue()) {
                    s.append(String.format("%s ; ", val));
                }
                s.append("\n");
            }

            Log.i("HTTP_Response", s.toString());
        }

        @Override
        public InputStream trackResponseBody(InputStream stream) {
            Log.i("HTTP_GetResponseBody", myURL);
            return new InputStreamTracker(stream, this);
        }

        @Override
        public OutputStream trackRequestBody(OutputStream stream) {
            Log.i("HTTP_GetRequestBody", myURL);
            return new OutputStreamTracker(stream, this);
        }
    }

    /**
     * Starts tracking a HTTP request
     *
     * @param url       the request URL
     * @param callstack optional callstack, if null the code location is not tracked
     * @return an object implementing {@link HttpConnectionTracker} that associated with a
     * particular HTTP request
     */
    public static HttpConnectionTracker trackConnection(String url, StackTraceElement[] callstack) {
        return new Connection(url, callstack);
    }
}

