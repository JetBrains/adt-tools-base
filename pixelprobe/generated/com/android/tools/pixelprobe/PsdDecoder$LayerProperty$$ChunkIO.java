package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdDecoder$LayerProperty$$ChunkIO {
    static PsdDecoder.LayerProperty read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.LayerProperty layerProperty = new PsdDecoder.LayerProperty();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(layerProperty);

        int size = 0;
        long byteCount = 0;

        layerProperty.signature = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        layerProperty.key = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        layerProperty.length = in.readInt() & 0xffffffffL;
        byteCount = layerProperty.length;
        in.pushRange(byteCount);
        if (layerProperty.key.equals("lmfx")) {
            layerProperty.data = PsdDecoder$LayerEffects$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("lfx2")) {
            layerProperty.data = PsdDecoder$LayerEffects$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("lsct")) {
            layerProperty.data = PsdDecoder$LayerSection$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("luni")) {
            layerProperty.data = PsdDecoder$UnicodeString$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("SoCo")) {
            layerProperty.data = PsdDecoder$SolidColorAdjustment$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("TySh")) {
            layerProperty.data = PsdDecoder$TypeToolObject$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("vmsk")) {
            layerProperty.data = PsdDecoder$VectorMask$$ChunkIO.read(in, stack);
        }
        in.popRange();

        stack.removeFirst();
        return layerProperty;
    }
}
