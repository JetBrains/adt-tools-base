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

import junit.framework.TestCase;

import java.util.List;

public class CallStackReconstructorTest extends TestCase {
    public void testBasicCallStack() {
        CallStackReconstructor reconstructor = new CallStackReconstructor(0xff);
        reconstructor.addTraceAction(0x1, TraceAction.METHOD_ENTER, 10, 10);
        reconstructor.addTraceAction(0x1, TraceAction.METHOD_EXIT, 15, 15);

        Call topLevel = reconstructor.getTopLevel();
        assertEquals(1, topLevel.getCallees().size());
        assertEquals(0x1, topLevel.getCallees().get(0).getMethodId());
    }

    public void testCallStack1() {
        CallStackReconstructor reconstructor = new CallStackReconstructor(0xff);

        reconstructor.addTraceAction(0x1, TraceAction.METHOD_ENTER, 10, 10);
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_ENTER, 11, 11);
        reconstructor.addTraceAction(0x3, TraceAction.METHOD_ENTER, 12, 12);
        reconstructor.addTraceAction(0x3, TraceAction.METHOD_EXIT, 13, 13);
        reconstructor.addTraceAction(0x3, TraceAction.METHOD_ENTER, 14, 14);
        reconstructor.addTraceAction(0x3, TraceAction.METHOD_EXIT, 15, 15);
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_EXIT, 16, 16);
        reconstructor.addTraceAction(0x5, TraceAction.METHOD_ENTER, 17, 17);
        reconstructor.addTraceAction(0x5, TraceAction.METHOD_EXIT, 18, 18);
        reconstructor.addTraceAction(0x1, TraceAction.METHOD_EXIT, 20, 20);
        reconstructor.addTraceAction(0x6, TraceAction.METHOD_ENTER, 21, 21);
        reconstructor.addTraceAction(0x6, TraceAction.METHOD_EXIT, 22, 22);

        String callStack = reconstructor.getTopLevel().toString();
        String expectedCallStack =
                  " -> 255 -> 1 -> 2 -> 3\n"
                + "                  -> 3\n"
                + "             -> 5\n"
                + "        -> 6";
        assertEquals(expectedCallStack, callStack);
    }

    public void testInvalidTrace() {
        CallStackReconstructor reconstructor = new CallStackReconstructor(0xff);

        try {
            reconstructor.addTraceAction(0x1, TraceAction.METHOD_ENTER, 1, 1);
            reconstructor.addTraceAction(0x2, TraceAction.METHOD_EXIT, 1, 1);
            fail("Runtime Exception should've been thrown by the previous statement");
        } catch (RuntimeException e) {
            // expected
        }
    }

    public void testMisMatchedCallStack() {
        CallStackReconstructor reconstructor = new CallStackReconstructor(0xff);

        reconstructor.addTraceAction(0x3, TraceAction.METHOD_EXIT, 1, 1);
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_EXIT, 2, 2);
        reconstructor.addTraceAction(0x1, TraceAction.METHOD_EXIT, 3, 3);

        String callStack = reconstructor.getTopLevel().toString();
        assertEquals(" -> 255 -> 1 -> 2 -> 3", callStack);
    }

    public void testCallStackDepths() {
        CallStackReconstructor reconstructor = new CallStackReconstructor(0xff);

        reconstructor.addTraceAction(0x1, TraceAction.METHOD_ENTER, 10, 10);
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_ENTER, 11, 11);
        reconstructor.addTraceAction(0x3, TraceAction.METHOD_ENTER, 12, 12);
        reconstructor.addTraceAction(0x3, TraceAction.METHOD_EXIT, 13, 13);
        reconstructor.addTraceAction(0x3, TraceAction.METHOD_ENTER, 14, 14);
        reconstructor.addTraceAction(0x3, TraceAction.METHOD_EXIT, 15, 15);
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_EXIT, 16, 16);
        reconstructor.addTraceAction(0x5, TraceAction.METHOD_ENTER, 17, 17);
        reconstructor.addTraceAction(0x5, TraceAction.METHOD_EXIT, 18, 18);
        reconstructor.addTraceAction(0x1, TraceAction.METHOD_EXIT, 20, 20);

        Call topLevel = reconstructor.getTopLevel();
        assertEquals(1, topLevel.getCallees().size());

        Call c = topLevel.getCallees().get(0);
        String actualDepths = c.format(new Call.Formatter() {
            @Override
            public String format(Call c) {
                return Integer.toString(c.getDepth());
            }
        });

        String expected =
                  " -> 1 -> 2 -> 3\n"
                + "           -> 3\n"
                + "      -> 2";
        assertEquals(expected, actualDepths);
    }

    public void testCallDurations() {
        CallStackReconstructor reconstructor = new CallStackReconstructor(0xff);

        reconstructor.addTraceAction(0x1, TraceAction.METHOD_ENTER, 10, 10);
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_ENTER, 11, 11);
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_EXIT, 14, 14);
        reconstructor.addTraceAction(0x1, TraceAction.METHOD_EXIT, 15, 15);

        List<Call> callees = reconstructor.getTopLevel().getCallees();
        assertFalse(callees.isEmpty());
        Call call = callees.get(0);
        assertEquals(15 - 10, call.getInclusiveThreadTime());
        assertEquals(15 - 10 - (14 - 11), call.getExclusiveThreadTime());
    }

    public void testMissingCallDurations() {
        CallStackReconstructor reconstructor = new CallStackReconstructor(0xff);

        // missing entry time for method 0x1, verify that it is computed as 1 less than
        // the entry time for its callee (method 0x2)
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_ENTER, 11, 11);
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_EXIT, 14, 14);
        reconstructor.addTraceAction(0x1, TraceAction.METHOD_EXIT, 15, 15);

        List<Call> callees = reconstructor.getTopLevel().getCallees();
        assertFalse(callees.isEmpty());
        Call call = callees.get(0);
        assertEquals(15 - (11 - 1), call.getInclusiveThreadTime());
        assertEquals(15 - (11 - 1) - (14 - 11), call.getExclusiveThreadTime());
    }

    /**
     * Verify that the model handles cases where the timings exceed {@link Integer#MAX_VALUE},
     * but are still within the scope of an unsigned integer.
     */
    public void testCallDurationOverflow() {
        CallStackReconstructor reconstructor = new CallStackReconstructor(0xff);

        reconstructor.addTraceAction(0x1, TraceAction.METHOD_ENTER, 0xfffffff0, 0xfffffff0);
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_ENTER, 0xfffffff2, 0xfffffff2);
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_EXIT,  0xfffffff4, 0xfffffff4);
        reconstructor.addTraceAction(0x1, TraceAction.METHOD_EXIT,  0xfffffff8, 0xfffffff8);

        List<Call> callees = reconstructor.getTopLevel().getCallees();
        assertFalse(callees.isEmpty());
        Call call = callees.get(0);
        assertEquals(8, call.getInclusiveThreadTime());
        assertEquals(6, call.getExclusiveThreadTime());
    }
}
