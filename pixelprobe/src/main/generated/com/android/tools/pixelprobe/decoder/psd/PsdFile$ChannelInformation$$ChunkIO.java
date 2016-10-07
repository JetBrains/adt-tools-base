package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$ChannelInformation$$ChunkIO {
    static PsdFile.ChannelInformation read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.ChannelInformation channelInformation = new PsdFile.ChannelInformation();
        stack.addFirst(channelInformation);

        int size = 0;
        long byteCount = 0;

        channelInformation.id = in.readShort();
        channelInformation.dataLength = in.readInt() & 0xffffffffL;

        stack.removeFirst();
        return channelInformation;
    }
}
