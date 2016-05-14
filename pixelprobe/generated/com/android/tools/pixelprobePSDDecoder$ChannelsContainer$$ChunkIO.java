package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PSDDecoder$ChannelsContainer$$ChunkIO {
    static PSDDecoder.ChannelsContainer read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.ChannelsContainer channelsContainer = new PSDDecoder.ChannelsContainer();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(channelsContainer);

        int size = 0;
        long byteCount = 0;

        channelsContainer.imageData = new ArrayList<PSDDecoder.ChannelImageData>();
        {
            PSDDecoder.LayersList list = (PSDDecoder.LayersList) stack.get(1);
            size = list.layers.get(list.channels.size()).channels;
        }
        PSDDecoder.ChannelImageData channelImageData;
        for (int i = 0; i < size; i++) {
            channelImageData = PSDDecoder$ChannelImageData$$ChunkIO.read(in, stack);
            channelsContainer.imageData.add(channelImageData);
        }

        stack.removeFirst();
        return channelsContainer;
    }
}
