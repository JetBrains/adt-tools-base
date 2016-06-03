package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$ChannelImageData$$ChunkIO {
    static PsdFile.ChannelImageData read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.ChannelImageData channelImageData = new PsdFile.ChannelImageData();
        stack.addFirst(channelImageData);

        int size = 0;
        long byteCount = 0;

        channelImageData.compression = PsdFile.CompressionMethod.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), PsdFile.CompressionMethod.values().length - 1))];
        {
            PsdFile.LayersList list = (PsdFile.LayersList) stack.get(2);
            PsdFile.RawLayer layer = list.layers.get(list.channels.size());
            PsdFile.ChannelsContainer container = (PsdFile.ChannelsContainer) stack.get(1);
            PsdFile.ChannelInformation info = layer.channelsInfo.get(container.imageData.size());
            byteCount = info.dataLength - 2;
        }
        channelImageData.data = ChunkUtils.readByteArray(in, byteCount, 4096);

        stack.removeFirst();
        return channelImageData;
    }
}
