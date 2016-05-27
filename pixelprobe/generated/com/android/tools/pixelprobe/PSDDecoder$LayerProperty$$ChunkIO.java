package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PSDDecoder$LayerProperty$$ChunkIO {
    static PSDDecoder.LayerProperty read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.LayerProperty layerProperty = new PSDDecoder.LayerProperty();
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
            layerProperty.data = PSDDecoder$LayerEffects$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("lfx2")) {
            layerProperty.data = PSDDecoder$LayerEffects$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("lsct")) {
            layerProperty.data = PSDDecoder$LayerSection$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("luni")) {
            layerProperty.data = PSDDecoder$UnicodeString$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("SoCo")) {
            layerProperty.data = PSDDecoder$SolidColorAdjustment$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("TySh")) {
            layerProperty.data = PSDDecoder$TypeToolObject$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("vmsk")) {
            layerProperty.data = PSDDecoder$VectorMask$$ChunkIO.read(in, stack);
        }
        in.popRange();

        stack.removeFirst();
        return layerProperty;
    }
}
