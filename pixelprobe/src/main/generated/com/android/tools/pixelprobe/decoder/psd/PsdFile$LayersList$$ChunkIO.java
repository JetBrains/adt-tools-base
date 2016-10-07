package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdFile$LayersList$$ChunkIO {
    static PsdFile.LayersList read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.LayersList layersList = new PsdFile.LayersList();
        stack.addFirst(layersList);

        int size = 0;
        long byteCount = 0;

        layersList.count = in.readShort();
        layersList.layers = new ArrayList<PsdFile.RawLayer>();
        size = Math.abs(layersList.count);
        PsdFile.RawLayer rawLayer;
        for (int i = 0; i < size; i++) {
            rawLayer = PsdFile$RawLayer$$ChunkIO.read(in, stack);
            layersList.layers.add(rawLayer);
        }
        layersList.channels = new ArrayList<PsdFile.ChannelsContainer>();
        size = Math.abs(layersList.count);
        PsdFile.ChannelsContainer channelsContainer;
        for (int i = 0; i < size; i++) {
            channelsContainer = PsdFile$ChannelsContainer$$ChunkIO.read(in, stack);
            layersList.channels.add(channelsContainer);
        }

        stack.removeFirst();
        return layersList;
    }
}
