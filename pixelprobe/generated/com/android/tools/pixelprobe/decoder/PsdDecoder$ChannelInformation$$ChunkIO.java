package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$ChannelInformation$$ChunkIO {
    static PsdDecoder.ChannelInformation read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.ChannelInformation channelInformation = new PsdDecoder.ChannelInformation();
        stack.addFirst(channelInformation);

        int size = 0;
        long byteCount = 0;

        channelInformation.id = in.readShort();
        channelInformation.dataLength = in.readInt() & 0xffffffffL;

        stack.removeFirst();
        return channelInformation;
    }
}
