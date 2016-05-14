package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$ChannelImageData$$ChunkIO {
    static PSDDecoder.ChannelImageData read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.ChannelImageData channelImageData = new PSDDecoder.ChannelImageData();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(channelImageData);

        int size = 0;
        long byteCount = 0;

        channelImageData.compression = PSDDecoder.CompressionMethod.values()[in.readUnsignedShort()];
        {
            PSDDecoder.LayersList list = (PSDDecoder.LayersList) stack.get(2);
            PSDDecoder.RawLayer layer = list.layers.get(list.channels.size());
            PSDDecoder.ChannelsContainer container = (PSDDecoder.ChannelsContainer) stack.get(1);
            PSDDecoder.ChannelInformation info = layer.channelsInfo.get(container.imageData.size());
            byteCount = info.dataLength - 2;
        }
        channelImageData.data = ChunkUtils.readByteArray(in, byteCount, 4096);

        stack.removeFirst();
        return channelImageData;
    }
}
