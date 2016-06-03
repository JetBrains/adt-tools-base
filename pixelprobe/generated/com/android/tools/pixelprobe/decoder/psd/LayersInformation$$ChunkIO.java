package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class LayersInformation$$ChunkIO {
    static LayersInformation read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        LayersInformation layersInformation = new LayersInformation();
        stack.addFirst(layersInformation);

        int size = 0;
        long byteCount = 0;

        layersInformation.length = in.readInt() & 0xffffffffL;
        if (layersInformation.length > 0) {
            byteCount = layersInformation.length;
            in.pushRange(byteCount);
            layersInformation.layers = LayersList$$ChunkIO.read(in, stack);
            in.popRange();
        }

        stack.removeFirst();
        return layersInformation;
    }
}
