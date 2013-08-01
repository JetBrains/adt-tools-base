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

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/** Reconstructs call stacks from a sequence of trace events (method entry/exit events) */
public class CallStackReconstructor {
    private List<Call> mTopLevelCallees = new ArrayList<Call>();
    private Stack<Call> mCallStack = new Stack<Call>();

    private boolean mFixupComplete;

    public void addTraceAction(long methodId, TraceAction action, int threadTime, int globalTime) {
        if (action == TraceAction.METHOD_ENTER) {
            enterMethod(methodId, threadTime, globalTime);
        } else {
            exitMethod(methodId, threadTime, globalTime);
        }
    }

    private void enterMethod(long methodId, int threadTime, int globalTime) {
        Call c = new Call(methodId);
        c.setMethodEntryTime(threadTime, globalTime);

        if (mCallStack.isEmpty()) {
            mTopLevelCallees.add(c);
        } else {
            Call caller = mCallStack.peek();
            caller.addCallee(c);
        }

        mCallStack.push(c);
    }

    private void exitMethod(long methodId, int threadTime, int globalTime) {
        if (!mCallStack.isEmpty()) {
            Call c = mCallStack.pop();
            if (c.getMethodId() != methodId) {
                String msg = String
                        .format("Error during call stack reconstruction. Attempt to exit from method 0x%1$x while in method 0x%2$x",
                                c.getMethodId(), methodId);
                throw new RuntimeException(msg);
            }

            c.setMethodExitTime(threadTime, globalTime);
        } else {
            // We are exiting out of a method that was entered into before tracing was started.
            // In such a case, create this method
            Call c = new Call(methodId);
            c.setMethodExitTime(threadTime, globalTime);

            // All the previous calls at the top level are now assumed to have been called from
            // this method. So mark this method as having called all of those methods, and reset
            // the top level to only include this method
            for (Call topLevelCallee : mTopLevelCallees) {
                c.addCallee(topLevelCallee);
            }
            mTopLevelCallees.clear();
            mTopLevelCallees.add(c);
        }
    }

    private void fixupCallStacks() {
        if (mFixupComplete) {
            return;
        }

        // fixup whatever needs fixing up
        // TODO: use global / thread times to infer context switches

        // Fix call stack depths
        for (Call c : mTopLevelCallees) {
            setStackDepthRecursive(c, 0);
        }

        mFixupComplete = true;
    }

    private void setStackDepthRecursive(@NonNull Call call, int i) {
        call.setDepth(i);

        for (Call c : call.getCallees()) {
            setStackDepthRecursive(c, i+1);
        }
    }

    public List<Call> getTopLevelCallees() {
        fixupCallStacks();
        return mTopLevelCallees;
    }
}
