package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$LayersInformation$$ChunkIO {
    static PSDDecoder.LayersInformation read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.LayersInformation layersInformation = new PSDDecoder.LayersInformation();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(layersInformation);

        int size = 0;
        long byteCount = 0;

        layersInformation.length = in.readInt() & 0xffffffffL;
        if (layersInformation.length > 0) {
            byteCount = layersInformation.length;
            in.pushRange(byteCount);
            layersInformation.layers = PSDDecoder$LayersList$$ChunkIO.read(in, stack);
            in.popRange();
        }

        stack.removeFirst();
        return layersInformation;
    }
}
