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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * HTTP stacks can use this interface to report the key states and data associated with individual
 * requests, to be consumed by the AndroidStudio network profiler.
 *
 * The methods in this interface are expected to be called in the following order (the calls marked
 * with question mark are optional)
 *
 * <pre>
 *  trackRequestBody()? --->
 *    trackRequest() --->
 *       trackResponse() --->
 *           trackResponseBody()? --->
 *              disconnect()?
 * </pre>
 *
 * Each method must be called on the thread that initiates the corresponding operation.
 */
interface HttpConnectionTracker {

    /**
     * Reports an explicit disconnect request
     */
    void disconnect();

    /**
     * Reports a fatal HTTP exchange failure
     *
     * @param message error message
     */
    void error(String message);

    /**
     * Tracks an optional request body (before the request is sent)
     *
     * @param stream the stream used to write the request body
     * @return an output stream which may wrap the original stream
     */
    OutputStream trackRequestBody(OutputStream stream);

    /**
     * A HTTP request is about to be sent to the wire
     *
     * @param method HTTP method
     * @param fields HTTP request header fields
     */
    void trackRequest(String method, Map<String, List<String>> fields);

    /**
     * Tracks the receiving of a HTTP response
     *
     * @param response response message
     * @param fields   HTTP response header fields
     */
    void trackResponse(String response, Map<String, List<String>> fields);

    /**
     * Tracks an optional response body after the response is received
     *
     * @param stream the stream used to read the response body
     * @return an input stream which may wrap the original stream
     */
    InputStream trackResponseBody(InputStream stream);
}
