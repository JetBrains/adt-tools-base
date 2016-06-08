package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdFile$ChannelsContainer$$ChunkIO {
    static PsdFile.ChannelsContainer read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.ChannelsContainer channelsContainer = new PsdFile.ChannelsContainer();
        stack.addFirst(channelsContainer);

        int size = 0;
        long byteCount = 0;

        channelsContainer.imageData = new ArrayList<PsdFile.ChannelImageData>();
        {
            PsdFile.LayersList list = (PsdFile.LayersList) stack.get(1);
            size = list.layers.get(list.channels.size()).channels;
        }
        PsdFile.ChannelImageData channelImageData;
        for (int i = 0; i < size; i++) {
            channelImageData = PsdFile$ChannelImageData$$ChunkIO.read(in, stack);
            channelsContainer.imageData.add(channelImageData);
        }

        stack.removeFirst();
        return channelsContainer;
    }
}
