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
package com.android.tools.profiler.support.profilers;

import com.android.tools.profiler.support.profilerserver.MessageHeader;
import com.android.tools.profiler.support.profilerserver.ProfilerComponent;

import android.net.TrafficStats;
import android.os.Process;

import java.nio.ByteBuffer;

public class NetworkProfiler implements ProfilerComponent {

    private final int myUid = Process.myUid();

    @Override
    public byte getComponentId() {
        return ProfilerRegistry.NETWORKING;
    }

    @Override
    public void onClientConnection() {

    }

    @Override
    public void onClientDisconnection() {

    }

    @Override
    public String configure(byte flags) {
        return null;
    }

    @Override
    public int receiveMessage(long frameStartTime, MessageHeader header, ByteBuffer input,
            ByteBuffer output) {
        return RESPONSE_OK;
    }

    @Override
    public int update(long frameStartTime, ByteBuffer output) {
        // TODO: Change time to a customized world clock time when the world clock is ready.
        long time = System.currentTimeMillis();
        long txBytes = TrafficStats.getUidTxBytes(myUid);
        long rxBytes = TrafficStats.getUidRxBytes(myUid);
        MessageHeader.writeToBuffer(output, 36, (short) 0, (short) 0, (byte) 0x7f, ProfilerRegistry.NETWORKING, (short) 0);
        output.putLong(time);
        output.putLong(txBytes);
        output.putLong(rxBytes);
        return UPDATE_DONE;
    }
}
