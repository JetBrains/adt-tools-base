/*
 * Copyright (C) 2007 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final public class HandleViewDebug extends ChunkHandler {
    /** Enable/Disable tracing of OpenGL calls. */
    public static final int CHUNK_VUGL = type("VUGL");

    /** List {@link ViewRootImpl}'s of this process. */
    public static final int CHUNK_VULW = type("VULW");

    /** Operation on view root, first parameter in packet should be one of VURT_* constants */
    public static final int CHUNK_VURT = type("VURT");

    /** Dump view hierarchy. */
    private static final int VURT_DUMP_HIERARCHY = 1;

    /** Capture View Layers. */
    private static final int VURT_CAPTURE_LAYERS = 2;

    /**
     * Generic View Operation, first parameter in the packet should be one of the
     * VUOP_* constants below.
     */
    public static final int CHUNK_VUOP = type("VUOP");

    /** Capture View. */
    private static final int VUOP_CAPTURE_VIEW = 1;

    /** Obtain the Display List corresponding to the view. */
    private static final int VUOP_DUMP_DISPLAYLIST = 2;

    /** Invalidate View. */
    private static final int VUOP_INVALIDATE_VIEW = 3;

    /** Re-layout given view. */
    private static final int VUOP_LAYOUT_VIEW = 4;

    /** Profile a view. */
    private static final int VUOP_PROFILE_VIEW = 5;

    private HandleViewDebug() {}

    public static void register(MonitorThread mt) {
        // TODO: add chunk type for auto window updates
        // and register here
    }

    @Override
    public void clientReady(Client client) throws IOException {}

    @Override
    public void clientDisconnected(Client client) {}

    public static abstract class ViewDumpHandler extends ChunkHandler {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final int mChunkType;

        public ViewDumpHandler(int chunkType) {
            mChunkType = chunkType;
        }

        @Override
        void clientReady(Client client) throws IOException {
        }

        @Override
        void clientDisconnected(Client client) {
        }

        @Override
        void handleChunk(Client client, int type, ByteBuffer data,
                boolean isReply, int msgId) {
            if (type != mChunkType) {
                handleUnknownChunk(client, type, data, isReply, msgId);
                return;
            }

            handleViewDebugResult(data);
            mLatch.countDown();
        }

        protected abstract void handleViewDebugResult(ByteBuffer data);

        protected void waitForResult(long timeout, TimeUnit unit) {
            try {
                mLatch.await(timeout, unit);
            } catch (InterruptedException e) {
                // pass
            }
        }
    }

    public static void listViewRoots(Client client, ViewDumpHandler replyHandler)
            throws IOException {
        ByteBuffer buf = allocBuffer(8);
        JdwpPacket packet = new JdwpPacket(buf);
        ByteBuffer chunkBuf = getChunkDataBuf(buf);
        chunkBuf.putInt(1);
        finishChunkPacket(packet, CHUNK_VULW, chunkBuf.position());
        client.sendAndConsume(packet, replyHandler);
    }

    public static void dumpViewHierarchy(@NonNull Client client, @NonNull String viewRoot,
            boolean skipChildren, boolean includeProperties, @NonNull ViewDumpHandler handler)
                    throws IOException {
        ByteBuffer buf = allocBuffer(4      // opcode
                + 4                         // view root length
                + viewRoot.length() * 2     // view root
                + 4                         // skip children
                + 4);                       // include view properties
        JdwpPacket packet = new JdwpPacket(buf);
        ByteBuffer chunkBuf = getChunkDataBuf(buf);

        chunkBuf.putInt(VURT_DUMP_HIERARCHY);
        chunkBuf.putInt(viewRoot.length());
        putString(chunkBuf, viewRoot);
        chunkBuf.putInt(skipChildren ? 1 : 0);
        chunkBuf.putInt(includeProperties ? 1 : 0);

        finishChunkPacket(packet, CHUNK_VURT, chunkBuf.position());
        client.sendAndConsume(packet, handler);
    }

    public static void captureLayers(@NonNull Client client, @NonNull String viewRoot,
            @NonNull ViewDumpHandler handler) throws IOException {
        int bufLen = 8 + viewRoot.length() * 2;

        ByteBuffer buf = allocBuffer(bufLen);
        JdwpPacket packet = new JdwpPacket(buf);
        ByteBuffer chunkBuf = getChunkDataBuf(buf);

        chunkBuf.putInt(VURT_CAPTURE_LAYERS);
        chunkBuf.putInt(viewRoot.length());
        putString(chunkBuf, viewRoot);

        finishChunkPacket(packet, CHUNK_VURT, chunkBuf.position());
        client.sendAndConsume(packet, handler);
    }

    private static void sendViewOpPacket(@NonNull Client client, int op, @NonNull String viewRoot,
            @NonNull String view, @Nullable ViewDumpHandler handler) throws IOException {
        int bufLen = 4 +                        // opcode
                4 + viewRoot.length() * 2 +     // view root strlen + view root
                4 + view.length() * 2;          // view strlen + view

        ByteBuffer buf = allocBuffer(bufLen);
        JdwpPacket packet = new JdwpPacket(buf);
        ByteBuffer chunkBuf = getChunkDataBuf(buf);

        chunkBuf.putInt(op);
        chunkBuf.putInt(viewRoot.length());
        putString(chunkBuf, viewRoot);

        chunkBuf.putInt(view.length());
        putString(chunkBuf, view);

        finishChunkPacket(packet, CHUNK_VUOP, chunkBuf.position());
        if (handler != null) {
            client.sendAndConsume(packet, handler);
        } else {
            client.sendAndConsume(packet);
        }
    }

    public static void profileView(@NonNull Client client, @NonNull String viewRoot,
            @NonNull String view, @NonNull ViewDumpHandler handler) throws IOException {
        sendViewOpPacket(client, VUOP_PROFILE_VIEW, viewRoot, view, handler);
    }

    public static void captureView(@NonNull Client client, @NonNull String viewRoot,
            @NonNull String view, @NonNull ViewDumpHandler handler) throws IOException {
        sendViewOpPacket(client, VUOP_CAPTURE_VIEW, viewRoot, view, handler);
    }

    public static void invalidateView(@NonNull Client client, @NonNull String viewRoot,
            @NonNull String view) throws IOException {
        sendViewOpPacket(client, VUOP_INVALIDATE_VIEW, viewRoot, view, null);
    }

    public static void requestLayout(@NonNull Client client, @NonNull String viewRoot,
            @NonNull String view) throws IOException {
        sendViewOpPacket(client, VUOP_LAYOUT_VIEW, viewRoot, view, null);
    }

    public static void dumpDisplayList(@NonNull Client client, @NonNull String viewRoot,
            @NonNull String view) throws IOException {
        sendViewOpPacket(client, VUOP_DUMP_DISPLAYLIST, viewRoot, view, null);
    }

    @Override
    public void handleChunk(Client client, int type, ByteBuffer data,
            boolean isReply, int msgId) {
    }

    public static void sendStartGlTracing(Client client) throws IOException {
        ByteBuffer buf = allocBuffer(4);
        JdwpPacket packet = new JdwpPacket(buf);

        ByteBuffer chunkBuf = getChunkDataBuf(buf);
        chunkBuf.putInt(1);
        finishChunkPacket(packet, CHUNK_VUGL, chunkBuf.position());

        client.sendAndConsume(packet);
    }

    public static void sendStopGlTracing(Client client) throws IOException {
        ByteBuffer buf = allocBuffer(4);
        JdwpPacket packet = new JdwpPacket(buf);

        ByteBuffer chunkBuf = getChunkDataBuf(buf);
        chunkBuf.putInt(0);
        finishChunkPacket(packet, CHUNK_VUGL, chunkBuf.position());

        client.sendAndConsume(packet);
    }
}

