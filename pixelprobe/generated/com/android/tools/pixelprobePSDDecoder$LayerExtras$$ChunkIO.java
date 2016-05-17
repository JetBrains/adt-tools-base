package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

final class PSDDecoder$LayerExtras$$ChunkIO {
    static PSDDecoder.LayerExtras read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.LayerExtras layerExtras = new PSDDecoder.LayerExtras();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(layerExtras);

        int size = 0;
        long byteCount = 0;

        layerExtras.maskAdjustment = PSDDecoder$MaskAdjustment$$ChunkIO.read(in, stack);
        layerExtras.blendRangesLength = in.readInt() & 0xffffffffL;
        layerExtras.layerBlendRanges = new ArrayList<PSDDecoder.BlendRange>();
        byteCount = layerExtras.blendRangesLength;
        in.pushRange(byteCount);
        PSDDecoder.BlendRange blendRange;
        while (in.available() > 0) {
            blendRange = PSDDecoder$BlendRange$$ChunkIO.read(in, stack);
            layerExtras.layerBlendRanges.add(blendRange);
        }
        in.popRange();
        layerExtras.nameLength = (short) (in.readByte() & 0xff);
        byteCount = layerExtras.nameLength;
        layerExtras.name = ChunkUtils.readString(in, byteCount, Charset.forName("ISO-8859-1"));
        byteCount = ((layerExtras.nameLength + 4) & ~3) - (layerExtras.nameLength + 1);
        /* layerExtras.namePadding */
        ChunkUtils.skip(in, byteCount);
        layerExtras.properties = new HashMap<String, PSDDecoder.LayerProperty>();
        PSDDecoder.LayerProperty layerProperty;
        while (in.available() > 0) {
            layerProperty = PSDDecoder$LayerProperty$$ChunkIO.read(in, stack);
            layerExtras.properties.put(String.valueOf(layerProperty.key), layerProperty);
        }

        stack.removeFirst();
        return layerExtras;
    }
}
