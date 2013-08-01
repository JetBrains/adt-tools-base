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

import com.google.common.base.Joiner;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

public class CallStackReconstructorTest extends TestCase {
    public void testBasicCallStack() {
        CallStackReconstructor reconstructor = new CallStackReconstructor();
        reconstructor.addTraceAction(0x1, TraceAction.METHOD_ENTER, 10, 10);
        reconstructor.addTraceAction(0x1, TraceAction.METHOD_EXIT, 15, 15);

        List<Call> callees = reconstructor.getTopLevelCallees();
        assertEquals(1, callees.size());
        assertEquals(0x1, callees.get(0).getMethodId());
    }

    public void testCallStack1() {
        CallStackReconstructor reconstructor = new CallStackReconstructor();

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

        String callStack = getCallStackInfo(reconstructor.getTopLevelCallees());
        String expectedCallStack =
                  " -> 1 -> 2 -> 3\n"
                + "           -> 3\n"
                + "      -> 5\n"
                + " -> 6";
        assertEquals(expectedCallStack, callStack);
    }

    public void testInvalidTrace() {
        CallStackReconstructor reconstructor = new CallStackReconstructor();

        try {
            reconstructor.addTraceAction(0x1, TraceAction.METHOD_ENTER, 1, 1);
            reconstructor.addTraceAction(0x2, TraceAction.METHOD_EXIT, 1, 1);
            fail("Runtime Exception should've been thrown by the previous statement");
        } catch (RuntimeException e) {
            // expected
        }
    }

    public void testMisMatchedCallStack() {
        CallStackReconstructor reconstructor = new CallStackReconstructor();

        reconstructor.addTraceAction(0x3, TraceAction.METHOD_EXIT, 1, 1);
        reconstructor.addTraceAction(0x2, TraceAction.METHOD_EXIT, 2, 2);
        reconstructor.addTraceAction(0x1, TraceAction.METHOD_EXIT, 3, 3);

        String callStack = getCallStackInfo(reconstructor.getTopLevelCallees());
        assertEquals(" -> 1 -> 2 -> 3", callStack);
    }

    private String getCallStackInfo(List<Call> calls) {
        List<String> callStacks = new ArrayList<String>(calls.size());

        for (Call c : calls) {
            callStacks.add(c.toString());
        }

        return Joiner.on('\n').join(callStacks);
    }


    public void testCallStackDepths() {
        CallStackReconstructor reconstructor = new CallStackReconstructor();

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

        List<Call> calls = reconstructor.getTopLevelCallees();
        assertEquals(1, calls.size());

        Call c = calls.get(0);
        String actualDepths = c.format(new Call.Formatter() {
            @Override
            public String format(Call c) {
                return Integer.toString(c.getDepth());
            }
        });

        String expected =
                  " -> 0 -> 1 -> 2\n"
                + "           -> 2\n"
                + "      -> 1";
        assertEquals(expected, actualDepths);
    }
}
