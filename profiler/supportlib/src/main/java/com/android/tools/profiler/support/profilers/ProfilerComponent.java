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

import java.nio.ByteBuffer;

public interface ProfilerComponent extends Comparable<ProfilerComponent> {
    int RESPONSE_OK = 0;
    int RESPONSE_RECONNECT = 1; // Request that the server terminates and waits for reconnection from client.

    int UPDATE_ERROR_RECONNECT = -1; // Signals that something went wrong during the update, and the server needs to terminate the connection.
    int UPDATE_DONE = 0; // Signals that no more updates are required.
    int UPDATE_AGAIN = 1; // Signals that the component requires more updates.

    void initialize();

    /**
     * @return the unique ID associated with this ProfilerComponent.
     */
    byte getComponentId();

    /**
     * Configures the component according to the component-defined flags.
     *
     * @param flags a byte bit-array of enable bits (1 == enable, 0 == disable)
     * @return a null value if success, an error string otherwise
     */
    String configure(byte flags);

    /**
     * Called by the server when the client (usually Android Studio) connects.
     */
    void onClientConnection();

    /**
     * Called by the server when the client (usually Android Studio) disconnects.
     */
    void onClientDisconnection();

    /**
     * Called by the server to notify the components when a message is received from the client.
     * Note every component receive all messages. So the components are responsible for filtering out messages.
     *
     * @param frameStartTime  The start time of the current frame. Useful for well-behaving components maintaining liveliness.
     * @param header          The pre-parsed header of the message.
     * @param input           The payload of the message. Note that the position is already at the offset of the payload.
     * @param output          The output channel for components that wish to respond to the message.
     * @return RESPONSE_* for how the component wishes the server to behave
     */
    int receiveMessage(long frameStartTime, MessageHeader header, ByteBuffer input, ByteBuffer output);

    /**
     * Allows the components to perform any spontaneous processing it needs.
     *
     * @param frameStartTime  The start time of the current frame. Useful for well-behaving components maintaining liveliness.
     * @param output          The output channel for components that wish to send a message to the client.
     * @return UPDATE_* for how the component wishes the server to behave
     */
    int update(long frameStartTime, ByteBuffer output);
}
