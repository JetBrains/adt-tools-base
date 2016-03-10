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

package com.android.tools.profiler.support.profilerserver;

import android.os.SystemClock;
import android.util.Log;

import com.android.tools.profiler.support.profilers.MemoryProfiler;
import com.android.tools.profiler.support.profilers.NetworkProfiler;
import com.android.tools.profiler.support.profilers.ProfilerRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profiler.support.profilerserver.MessageHeader.REQUEST_FLAGS;
import static com.android.tools.profiler.support.profilerserver.MessageHeader.RESPONSE_MASK;

/**
 * ProfilerServer is the main server service for the studio profiling feature.
 * Clients (usual Android Studio) make connections to the server in order to
 * retrieve profiling information about the underlying application.
 *
 * The server is a singleton, and is designed such that the {@link #start()}
 * and {@link #stop()} methods are reentrant.
 */
public class ProfilerServer implements Runnable {
    public static final String SERVER_NAME = "StudioProfiler";

    private static final long HEARTBEAT_INTERVAL = TimeUnit.NANOSECONDS.convert(2L, TimeUnit.SECONDS);
    private static final long HEARTBEAT_TIMEOUT = TimeUnit.NANOSECONDS.convert(5L, TimeUnit.SECONDS);
    private static final int INPUT_BUFFER_SIZE = 1024;
    private static final int OUTPUT_BUFFER_SIZE = 64 * 1024;

    private static final ProfilerServer INSTANCE = new ProfilerServer();

    private static volatile ServerSocketChannel ourServerSocketChannel;
    private static volatile Thread ourServerThread;

    private static final int LIVE_SERVER_PORT = 5044;
    private static final InetSocketAddress SOCKET_ADDRESS = new InetSocketAddress(LIVE_SERVER_PORT);
    private static final long CONNECTION_RETRY_TIME_MS = 100L;
    private static final long SERVER_UPDATE_TIME_NS = TimeUnit.NANOSECONDS.convert(1L, TimeUnit.SECONDS) / 60L; // 60FPS

    private static final int VERSION = 0;

    private static final short SUBTYPE_HANDSHAKE = 0;
    private static final short SUBTYPE_PING = 1;
    private static final short SUBTYPE_RESERVED = 2;
    private static final short SUBTYPE_ENABLE_BITS = 3;

    private static final int HANDSHAKE_HEADER_LENGTH = 16;

    private static final byte OK_RESPONSE = (byte)0;
    private static final byte ERROR_RESPONSE = (byte)1;
    private static final byte STRING_TERMINATOR = (byte)0;

    private static final Comparator<ProfilerComponent> COMPONENT_COMPARATOR = new Comparator<ProfilerComponent>() {
        @Override
        public int compare(ProfilerComponent a, ProfilerComponent b) {
            return a.getComponentId() - b.getComponentId();
        }
    };

    private final List<ProfilerComponent> mRegisteredComponents = new ArrayList<ProfilerComponent>(ProfilerRegistry.TOTAL);
    private final MessageHeader mInputMessageHeader = new MessageHeader();
    private final MessageHeader mOutputMessageHeader = new MessageHeader();
    private final ByteBuffer mInputBuffer = ByteBuffer.allocateDirect(INPUT_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer mOutputBuffer = ByteBuffer.allocateDirect(OUTPUT_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    private CountDownLatch mContinueRunningLatch;
    private long mLastHeartbeatTime = 0;
    private short mHeartbeat = 0;
    private short mOutstandingHeartbeat = mHeartbeat;
    private boolean mDoHeartBeat = false;

    public static ProfilerServer getInstance() {
        return INSTANCE;
    }

    private ProfilerServer() {
        registerComponent(new ServerComponent());
        registerComponent(new MemoryProfiler());
        registerComponent(new NetworkProfiler());
    }

    public static synchronized void start() throws IOException {
        if (ourServerThread == null) {
            Log.v(SERVER_NAME, "Starting server");
            ourServerSocketChannel = ServerSocketChannel.open();
            ourServerSocketChannel.socket().setReuseAddress(true);
            ourServerSocketChannel.socket().bind(SOCKET_ADDRESS);
            ourServerSocketChannel.configureBlocking(false);
            INSTANCE.mContinueRunningLatch = new CountDownLatch(1);
            ourServerThread = new Thread(INSTANCE, SERVER_NAME);
            ourServerThread.start();
            Log.v(SERVER_NAME, "Started server");
        }
    }

    public static synchronized void stop() throws IOException {
        if (ourServerThread != null) {
            Log.v(SERVER_NAME, "Stopping server");
            INSTANCE.mContinueRunningLatch.countDown();
            ourServerThread.interrupt();
            try {
                ourServerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.v(SERVER_NAME, Log.getStackTraceString(e));
            }
            ourServerThread = null;
            ourServerSocketChannel.close();
            ourServerSocketChannel = null;
            Log.v(SERVER_NAME, "Stopped server");
        }
    }

    public void registerComponent(ProfilerComponent profilerComponent) {
        byte componentId = profilerComponent.getComponentId();
        if (componentId < 0 || componentId >= ProfilerRegistry.TOTAL) {
            throw new IllegalArgumentException("Component with ID " + componentId + " is invalid");
        }

        for (int i = 0; i < mRegisteredComponents.size(); ++i) {
            if (mRegisteredComponents.get(i).getComponentId() == componentId) {
                throw new IllegalArgumentException("Component with ID " + componentId + " has already been registered");
            }
        }
        mRegisteredComponents.add(profilerComponent);
        Collections.sort(mRegisteredComponents, COMPONENT_COMPARATOR);
    }

    private static void flushBuffer(SocketChannel socketChannel, ByteBuffer output) throws IOException {
        output.flip();
        while (output.hasRemaining()) {
            int bytesWritten = socketChannel.write(output);
            Log.v(SERVER_NAME, "Wrote " + bytesWritten + " bytes");
        }
        output.clear();
    }

    @Override
    public void run() {
        Log.v(SERVER_NAME, "Started profiling server");
        SocketChannel socketChannel = null;

        while (mContinueRunningLatch.getCount() > 0) {
            try {
                socketChannel = acceptConnection();
                if (socketChannel == null) {
                    mContinueRunningLatch.countDown();
                    break;
                }

                socketChannel.configureBlocking(false);
                mHeartbeat = 0;
                mOutstandingHeartbeat = mHeartbeat;
                mLastHeartbeatTime = SystemClock.elapsedRealtimeNanos();

                try {
                    for (int i = 0; i < mRegisteredComponents.size(); i++) {
                        mRegisteredComponents.get(i).onClientConnection();
                        flushBuffer(socketChannel, mOutputBuffer);
                    }
                } catch (IOException e) {
                    Log.e(SERVER_NAME, e.toString());
                    break;
                }

                while (mContinueRunningLatch.getCount() > 0) {
                    long startTime = SystemClock.elapsedRealtimeNanos();

                    try {
                        processMessages(startTime, socketChannel);
                        updateComponents(startTime, socketChannel);
                    }
                    catch (IOException e) {
                        Log.e(SERVER_NAME, Log.getStackTraceString(e));
                        break;
                    }

                    try {
                        long sleepTime = SERVER_UPDATE_TIME_NS - (SystemClock.elapsedRealtimeNanos() - startTime);
                        if (sleepTime > 0) {
                            mContinueRunningLatch.await(sleepTime, TimeUnit.NANOSECONDS);
                        }
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                for (int i = 0; i < mRegisteredComponents.size(); i++) {
                    mRegisteredComponents.get(i).onClientDisconnection();
                }
            } catch (Exception e) {
                Log.e(SERVER_NAME, Log.getStackTraceString(e));
            } finally {
                if (socketChannel != null) {
                    try {
                        socketChannel.close();
                        Log.v(SERVER_NAME, "Aborted profiling server");
                    } catch (IOException ignored) {}
                    finally {
                        socketChannel = null;
                    }
                }
            }
        }
        Log.v(SERVER_NAME, "Stopped profiling server");
    }

    private SocketChannel acceptConnection() {
        SocketChannel socketChannel = null;

        try {
            while (mContinueRunningLatch.getCount() > 0 && socketChannel == null) {
                socketChannel = ourServerSocketChannel.accept();
                if (socketChannel == null) {
                    mContinueRunningLatch.await(CONNECTION_RETRY_TIME_MS, TimeUnit.MILLISECONDS);
                }
            }
            Log.v(SERVER_NAME, "Socket accepted");
            mInputBuffer.clear();
            mOutputBuffer.clear();
            return socketChannel;
        } catch (IOException e) {
            Log.e(SERVER_NAME, e.toString());
        } catch (InterruptedException e) {
            Log.e(SERVER_NAME, e.toString());
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private void processMessages(long frameStartTime, SocketChannel socketChannel) throws IOException {
        socketChannel.read(mInputBuffer);
        mInputBuffer.flip();
        while (mInputBuffer.remaining() >= MessageHeader.MESSAGE_HEADER_LENGTH) {
            mLastHeartbeatTime = frameStartTime;

            mInputBuffer.mark();
            mInputMessageHeader.parseFromBuffer(mInputBuffer);
            if (mInputBuffer.remaining() < mInputMessageHeader.length - MessageHeader.MESSAGE_HEADER_LENGTH) {
                // Reset (but don't set the position of) the buffer so that
                // compacting will preserve the incomplete message.
                Log.v(SERVER_NAME, "Incomplete message");
                mInputBuffer.reset();
                break;
            }
            int payloadPosition = mInputBuffer.position();

            // Update the last heartbeat time to the current time.
            // We got a message from the client, so it means the client is still alive.
            mLastHeartbeatTime = frameStartTime;

            Log.v(SERVER_NAME, "Processing message");
            for (int i = 0; i < mRegisteredComponents.size(); i++) {
                try {
                    mRegisteredComponents.get(i).receiveMessage(frameStartTime, mInputMessageHeader, mInputBuffer, mOutputBuffer);
                }
                catch (Exception e) {
                    Log.e("ProfileServer", Log.getStackTraceString(e));
                }
                flushBuffer(socketChannel, mOutputBuffer);
                mInputBuffer.position(payloadPosition);
            }

            // Manually advance the position past the end of the message.
            if (mInputMessageHeader.length < MessageHeader.MESSAGE_HEADER_LENGTH ||
                mInputMessageHeader.length > mInputBuffer.capacity()) {
                throw new RuntimeException("Unexpected message length.");
            }

            Log.v(SERVER_NAME, "Processed message");
            mInputBuffer.reset();
            mInputBuffer.position(mInputBuffer.position() + mInputMessageHeader.length);
        }
        mInputBuffer.compact();
    }

    private void updateComponents(long frameStartTime, SocketChannel socketChannel) throws IOException {
        int updatesRequired;
        do {
            updatesRequired = 0;
            for (int i = 0; i < mRegisteredComponents.size(); i++) {
                try {
                    ProfilerComponent component = mRegisteredComponents.get(i);
                    int result = component.update(frameStartTime, mOutputBuffer);
                    if (result == ProfilerComponent.UPDATE_ERROR_RECONNECT) {
                        throw new IOException("Error encountered when updating component with ID " + component.getComponentId());
                    }
                    updatesRequired += result;
                    flushBuffer(socketChannel, mOutputBuffer);
                }
                catch (RuntimeException e) {
                    Log.e(SERVER_NAME, Log.getStackTraceString(e));
                }
            }
        } while (updatesRequired > 0);
    }

    private class ServerComponent implements ProfilerComponent {
        @Override
        public byte getComponentId() {
            return ProfilerRegistry.SERVER;
        }

        @Override
        public String configure(byte flags) {
            return null;
        }

        @Override
        public void onClientConnection() {}

        @Override
        public void onClientDisconnection() {}

        @Override
        public int receiveMessage(long frameStartTime, MessageHeader header, ByteBuffer input, ByteBuffer output) {
            switch (mInputMessageHeader.subType) {
                case SUBTYPE_HANDSHAKE: // Process handshake and send confirmation.
                    mOutputMessageHeader.copyFrom(mInputMessageHeader);
                    mOutputMessageHeader.length = MessageHeader.MESSAGE_HEADER_LENGTH + 1; // 1 extra byte for the response.
                    mOutputMessageHeader.flags = RESPONSE_MASK;
                    mOutputMessageHeader.writeToBuffer(output);
                    if (mInputMessageHeader.length != HANDSHAKE_HEADER_LENGTH || input.getInt() != VERSION) {
                        output.put(ERROR_RESPONSE);
                        if (mInputMessageHeader.length != HANDSHAKE_HEADER_LENGTH) {
                            Log.e(SERVER_NAME, "Invalid handshake message.");
                        } else {
                            Log.e(SERVER_NAME, "Incompatible client version.");
                        }
                        return RESPONSE_RECONNECT;
                    }
                    output.put(OK_RESPONSE);
                    mDoHeartBeat = true;
                    Log.v(SERVER_NAME, "Handshake Done");
                    break;
                case SUBTYPE_PING:
                    // Process heartbeat.
                    if ((mInputMessageHeader.flags & RESPONSE_MASK) == 0) {
                        mOutputMessageHeader.copyFrom(mInputMessageHeader);
                        mOutputMessageHeader.flags |= RESPONSE_MASK;
                        mOutputMessageHeader.writeToBuffer(output);
                        Log.v(SERVER_NAME, "Pong");
                    } else if ((mInputMessageHeader.flags & RESPONSE_MASK) == RESPONSE_MASK) {
                        if (mInputMessageHeader.id == mOutstandingHeartbeat) {
                            mOutstandingHeartbeat = mHeartbeat;
                        }
                    }
                    break;
                case SUBTYPE_RESERVED:
                    // TODO TBD
                    assert false;
                    break;
                case SUBTYPE_ENABLE_BITS: // Process enable bits.
                    byte targetComponent = input.get();
                    byte flags = input.get();
                    String result = null;
                    for (int i = 0; i < mRegisteredComponents.size(); i++) {
                        ProfilerComponent component = mRegisteredComponents.get(i);
                        if (component.getComponentId() == targetComponent) {
                            result = component.configure(flags);
                            break;
                        }
                    }
                    if (result != null && result.isEmpty()) {
                        throw new AssertionError("Empty result returned by component ID: " + targetComponent);
                    }
                    mOutputMessageHeader.copyFrom(mInputMessageHeader);
                    mOutputMessageHeader.length = MessageHeader.MESSAGE_HEADER_LENGTH + (result == null ? 1 : result.length());
                    mOutputMessageHeader.flags |= RESPONSE_MASK;
                    mOutputMessageHeader.writeToBuffer(output);
                    if (result == null) {
                        output.put(STRING_TERMINATOR);
                    } else {
                        output.put(result.getBytes(StandardCharsets.US_ASCII));
                    }
                    break;
            }
            return RESPONSE_OK;
        }

        @Override
        public int update(long frameStartTime, ByteBuffer output) {
            if (mDoHeartBeat) {
                if (mHeartbeat == mOutstandingHeartbeat) {
                    if (frameStartTime - mLastHeartbeatTime > HEARTBEAT_INTERVAL) {
                        MessageHeader.writeToBuffer(
                            output,
                            MessageHeader.MESSAGE_HEADER_LENGTH,
                            mHeartbeat,
                            (short)0,
                            REQUEST_FLAGS,
                            ProfilerRegistry.SERVER,
                            SUBTYPE_PING);
                        Log.v(SERVER_NAME, "Ping");

                        mLastHeartbeatTime = frameStartTime;
                        mHeartbeat++;
                    }
                } else {
                    if (frameStartTime - mLastHeartbeatTime > HEARTBEAT_TIMEOUT) {
                        Log.i(SERVER_NAME, "Connection timed out");
                        return UPDATE_ERROR_RECONNECT;
                    }
                }
            }

            return UPDATE_DONE;
        }
    }
}
