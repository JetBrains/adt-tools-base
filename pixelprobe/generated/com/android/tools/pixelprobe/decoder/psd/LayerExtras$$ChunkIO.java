package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

final class LayerExtras$$ChunkIO {
    static LayerExtras read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        LayerExtras layerExtras = new LayerExtras();
        stack.addFirst(layerExtras);

        int size = 0;
        long byteCount = 0;

        layerExtras.maskAdjustment = MaskAdjustment$$ChunkIO.read(in, stack);
        layerExtras.blendRangesLength = in.readInt() & 0xffffffffL;
        layerExtras.layerBlendRanges = new ArrayList<BlendRange>();
        byteCount = layerExtras.blendRangesLength;
        in.pushRange(byteCount);
        BlendRange blendRange;
        while (in.available() > 0) {
            blendRange = BlendRange$$ChunkIO.read(in, stack);
            layerExtras.layerBlendRanges.add(blendRange);
        }
        in.popRange();
        layerExtras.nameLength = (short) (in.readByte() & 0xff);
        byteCount = layerExtras.nameLength;
        layerExtras.name = ChunkUtils.readString(in, byteCount, Charset.forName("ISO-8859-1"));
        byteCount = ((layerExtras.nameLength + 4) & ~3) - (layerExtras.nameLength + 1);
        /* layerExtras.namePadding */
        ChunkUtils.skip(in, byteCount);
        layerExtras.properties = new HashMap<String, LayerProperty>();
        LayerProperty layerProperty;
        while (in.available() > 0) {
            layerProperty = LayerProperty$$ChunkIO.read(in, stack);
            layerExtras.properties.put(layerProperty.key, layerProperty);
        }

        stack.removeFirst();
        return layerExtras;
    }
}
