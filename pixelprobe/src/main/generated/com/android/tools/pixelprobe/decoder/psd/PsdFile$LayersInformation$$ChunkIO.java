package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

final class PsdFile$LayersInformation$$ChunkIO {
    static PsdFile.LayersInformation read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.LayersInformation layersInformation = new PsdFile.LayersInformation();
        stack.addFirst(layersInformation);

        int size = 0;
        long byteCount = 0;

        layersInformation.length = in.readInt() & 0xffffffffL;
        layersInformation.listLength = in.readInt() & 0xffffffffL;
        if (layersInformation.listLength > 0) {
            byteCount = layersInformation.listLength;
            in.pushRange(byteCount);
            layersInformation.layers = PsdFile$LayersList$$ChunkIO.read(in, stack);
            in.popRange();
        }
        layersInformation.globalMaskInfoLength = in.readInt() & 0xffffffffL;
        byteCount = layersInformation.globalMaskInfoLength;
        /* layersInformation.globalMaskInfo */
        ChunkUtils.skip(in, byteCount);
        layersInformation.extras = new HashMap<String, PsdFile.LayerProperty>();
        byteCount = layersInformation.length - layersInformation.listLength - layersInformation.globalMaskInfoLength - 8;
        in.pushRange(byteCount);
        PsdFile.LayerProperty layerProperty;
        while (in.available() > 0) {
            layerProperty = PsdFile$LayerProperty$$ChunkIO.read(in, stack);
            layersInformation.extras.put(layerProperty.key, layerProperty);
        }
        in.popRange();

        stack.removeFirst();
        return layersInformation;
    }
}
