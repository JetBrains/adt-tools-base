/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ddmlib;

import java.io.IOException;
import java.nio.ByteBuffer;

public class OpenGlTraceChunkHandler extends ChunkHandler {
    /** GL TRace Control: data in the packet enables or disables tracing. */
    public static final int CHUNK_GLTR = type("GLTR");

    private OpenGlTraceChunkHandler() {
    }

    @Override
    void clientReady(Client client) throws IOException {
    }

    @Override
    void clientDisconnected(Client client) {
    }

    @Override
    void handleChunk(Client client, int type, ByteBuffer data, boolean isReply, int msgId) {
        handleUnknownChunk(client, type, data, isReply, msgId);
    }

    public static void sendStartGlTracing(Client client) throws IOException {
        ByteBuffer buf = allocBuffer(4);
        JdwpPacket packet = new JdwpPacket(buf);

        ByteBuffer chunkBuf = getChunkDataBuf(buf);
        chunkBuf.putInt(1);
        finishChunkPacket(packet, CHUNK_GLTR, chunkBuf.position());

        client.sendAndConsume(packet);
    }

    public static void sendStopGlTracing(Client client) throws IOException {
        ByteBuffer buf = allocBuffer(4);
        JdwpPacket packet = new JdwpPacket(buf);

        ByteBuffer chunkBuf = getChunkDataBuf(buf);
        chunkBuf.putInt(0);
        finishChunkPacket(packet, CHUNK_GLTR, chunkBuf.position());

        client.sendAndConsume(packet);
    }
}
