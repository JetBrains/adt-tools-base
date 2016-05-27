package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$ChannelInformation$$ChunkIO {
    static PSDDecoder.ChannelInformation read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.ChannelInformation channelInformation = new PSDDecoder.ChannelInformation();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(channelInformation);

        int size = 0;
        long byteCount = 0;

        channelInformation.id = in.readShort();
        channelInformation.dataLength = in.readInt() & 0xffffffffL;

        stack.removeFirst();
        return channelInformation;
    }
}
