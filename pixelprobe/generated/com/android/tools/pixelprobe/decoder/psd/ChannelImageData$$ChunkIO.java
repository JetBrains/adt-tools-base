package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class ChannelImageData$$ChunkIO {
    static ChannelImageData read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        ChannelImageData channelImageData = new ChannelImageData();
        stack.addFirst(channelImageData);

        int size = 0;
        long byteCount = 0;

        channelImageData.compression = CompressionMethod.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), CompressionMethod.values().length - 1))];
        {
            LayersList list = (LayersList) stack.get(2);
            RawLayer layer = list.layers.get(list.channels.size());
            ChannelsContainer container = (ChannelsContainer) stack.get(1);
            ChannelInformation info = layer.channelsInfo.get(container.imageData.size());
            byteCount = info.dataLength - 2;
        }
        channelImageData.data = ChunkUtils.readByteArray(in, byteCount, 4096);

        stack.removeFirst();
        return channelImageData;
    }
}
