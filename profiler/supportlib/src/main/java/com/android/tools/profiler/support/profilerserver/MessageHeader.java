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

import java.nio.ByteBuffer;

/**
 * This is the message header for every message to and from the ProfilerServer.
 *
 * When a component wishes to send or receive messages, the {@code ProfilerComponent} should use this
 * class to serialize/deserialize a header from the output/input streams of the connection.
 */
public class MessageHeader {
    public static final int MESSAGE_HEADER_LENGTH = 12;
    public static final byte REQUEST_FLAGS = (byte)0;
    public static final byte RESPONSE_MASK = (byte)0x80;

    public int length; // Total length of the message.
    public short id; // The ID of the message, to match between request and response.
    public short remainingChunks; // The number of data blocks left in the full message (each block also needs its own header).
    public byte flags; // The flags, such as RESPONSE_MASK.
    public byte type; // The ID of the target component.
    public short subType; // A target component-dependent ID.

    public MessageHeader() {
        length = -1;
        id = -1;
        remainingChunks = -1;
        flags = -1;
        type = -1;
        subType = -1;
    }

    public MessageHeader(int length, short id, short remainingChunks, byte flags, byte type, short subType) {
        this.length = length;
        this.id = id;
        this.remainingChunks = remainingChunks;
        this.flags = flags;
        this.type = type;
        this.subType = subType;
    }

    public MessageHeader(ByteBuffer input) {
        parseFromBuffer(input);
    }

    public void parseFromBuffer(ByteBuffer input) {
        length = input.getInt();
        id = input.getShort();
        remainingChunks = input.getShort();
        flags = input.get();
        type = input.get();
        subType = input.getShort();
    }

    public void writeToBuffer(ByteBuffer output) {
        writeToBuffer(output, length, id, remainingChunks, flags, type, subType);
    }

    public static ByteBuffer writeToBuffer(ByteBuffer output,
                                           int length,
                                           short id,
                                           short remainingChunks,
                                           byte flags,
                                           byte type,
                                           short subType) {
        output.putInt(length);
        output.putShort(id);
        output.putShort(remainingChunks);
        output.put(flags);
        output.put(type);
        output.putShort(subType);
        return output;
    }

    public void copyFrom(MessageHeader source) {
        length = source.length;
        id = source.id;
        remainingChunks = source.remainingChunks;
        flags = source.flags;
        type = source.type;
        subType = source.subType;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MessageHeader) {
            MessageHeader header = (MessageHeader)o;
            return length == header.length &&
                   id == header.id &&
                   remainingChunks == header.remainingChunks &&
                   flags == header.flags &&
                   type == header.type &&
                   subType == header.subType;
        }
        return false;
    }
}
