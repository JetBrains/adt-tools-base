package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$ChannelImageData$$ChunkIO {
    static PsdDecoder.ChannelImageData read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.ChannelImageData channelImageData = new PsdDecoder.ChannelImageData();
        stack.addFirst(channelImageData);

        int size = 0;
        long byteCount = 0;

        channelImageData.compression = PsdDecoder.CompressionMethod.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), PsdDecoder.CompressionMethod.values().length - 1))];
        {
            PsdDecoder.LayersList list = (PsdDecoder.LayersList) stack.get(2);
            PsdDecoder.RawLayer layer = list.layers.get(list.channels.size());
            PsdDecoder.ChannelsContainer container = (PsdDecoder.ChannelsContainer) stack.get(1);
            PsdDecoder.ChannelInformation info = layer.channelsInfo.get(container.imageData.size());
            byteCount = info.dataLength - 2;
        }
        channelImageData.data = ChunkUtils.readByteArray(in, byteCount, 4096);

        stack.removeFirst();
        return channelImageData;
    }
}
