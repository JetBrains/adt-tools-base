package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$LayersInformation$$ChunkIO {
    static PsdFile.LayersInformation read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.LayersInformation layersInformation = new PsdFile.LayersInformation();
        stack.addFirst(layersInformation);

        int size = 0;
        long byteCount = 0;

        layersInformation.length = in.readInt() & 0xffffffffL;
        if (layersInformation.length > 0) {
            byteCount = layersInformation.length;
            in.pushRange(byteCount);
            layersInformation.layers = PsdFile$LayersList$$ChunkIO.read(in, stack);
            in.popRange();
        }

        stack.removeFirst();
        return layersInformation;
    }
}
