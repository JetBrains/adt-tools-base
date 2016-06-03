package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class LayersList$$ChunkIO {
    static LayersList read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        LayersList layersList = new LayersList();
        stack.addFirst(layersList);

        int size = 0;
        long byteCount = 0;

        layersList.length = in.readInt() & 0xffffffffL;
        layersList.count = in.readShort();
        layersList.layers = new ArrayList<RawLayer>();
        size = Math.abs(layersList.count);
        RawLayer rawLayer;
        for (int i = 0; i < size; i++) {
            rawLayer = RawLayer$$ChunkIO.read(in, stack);
            layersList.layers.add(rawLayer);
        }
        layersList.channels = new ArrayList<ChannelsContainer>();
        size = Math.abs(layersList.count);
        ChannelsContainer channelsContainer;
        for (int i = 0; i < size; i++) {
            channelsContainer = ChannelsContainer$$ChunkIO.read(in, stack);
            layersList.channels.add(channelsContainer);
        }

        stack.removeFirst();
        return layersList;
    }
}
