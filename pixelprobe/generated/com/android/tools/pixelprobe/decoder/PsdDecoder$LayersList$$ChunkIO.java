package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdDecoder$LayersList$$ChunkIO {
    static PsdDecoder.LayersList read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.LayersList layersList = new PsdDecoder.LayersList();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(layersList);

        int size = 0;
        long byteCount = 0;

        layersList.length = in.readInt() & 0xffffffffL;
        layersList.count = in.readShort();
        layersList.layers = new ArrayList<PsdDecoder.RawLayer>();
        size = Math.abs(layersList.count);
        PsdDecoder.RawLayer rawLayer;
        for (int i = 0; i < size; i++) {
            rawLayer = PsdDecoder$RawLayer$$ChunkIO.read(in, stack);
            layersList.layers.add(rawLayer);
        }
        layersList.channels = new ArrayList<PsdDecoder.ChannelsContainer>();
        size = Math.abs(layersList.count);
        PsdDecoder.ChannelsContainer channelsContainer;
        for (int i = 0; i < size; i++) {
            channelsContainer = PsdDecoder$ChannelsContainer$$ChunkIO.read(in, stack);
            layersList.channels.add(channelsContainer);
        }

        stack.removeFirst();
        return layersList;
    }
}
