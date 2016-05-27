package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PSDDecoder$LayersList$$ChunkIO {
    static PSDDecoder.LayersList read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.LayersList layersList = new PSDDecoder.LayersList();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(layersList);

        int size = 0;
        long byteCount = 0;

        layersList.length = in.readInt() & 0xffffffffL;
        layersList.count = in.readShort();
        layersList.layers = new ArrayList<PSDDecoder.RawLayer>();
        size = Math.abs(layersList.count);
        PSDDecoder.RawLayer rawLayer;
        for (int i = 0; i < size; i++) {
            rawLayer = PSDDecoder$RawLayer$$ChunkIO.read(in, stack);
            layersList.layers.add(rawLayer);
        }
        layersList.channels = new ArrayList<PSDDecoder.ChannelsContainer>();
        size = Math.abs(layersList.count);
        PSDDecoder.ChannelsContainer channelsContainer;
        for (int i = 0; i < size; i++) {
            channelsContainer = PSDDecoder$ChannelsContainer$$ChunkIO.read(in, stack);
            layersList.channels.add(channelsContainer);
        }

        stack.removeFirst();
        return layersList;
    }
}
