package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdDecoder$ChannelsContainer$$ChunkIO {
    static PsdDecoder.ChannelsContainer read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.ChannelsContainer channelsContainer = new PsdDecoder.ChannelsContainer();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(channelsContainer);

        int size = 0;
        long byteCount = 0;

        channelsContainer.imageData = new ArrayList<PsdDecoder.ChannelImageData>();
        {
            PsdDecoder.LayersList list = (PsdDecoder.LayersList) stack.get(1);
            size = list.layers.get(list.channels.size()).channels;
        }
        PsdDecoder.ChannelImageData channelImageData;
        for (int i = 0; i < size; i++) {
            channelImageData = PsdDecoder$ChannelImageData$$ChunkIO.read(in, stack);
            channelsContainer.imageData.add(channelImageData);
        }

        stack.removeFirst();
        return channelsContainer;
    }
}
