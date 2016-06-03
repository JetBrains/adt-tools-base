package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class LayerProperty$$ChunkIO {
    static LayerProperty read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        LayerProperty layerProperty = new LayerProperty();
        stack.addFirst(layerProperty);

        int size = 0;
        long byteCount = 0;

        layerProperty.signature = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        layerProperty.key = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        layerProperty.length = in.readInt() & 0xffffffffL;
        byteCount = layerProperty.length;
        in.pushRange(byteCount);
        if (layerProperty.key.equals("lmfx")) {
            layerProperty.data = LayerEffects$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("lfx2")) {
            layerProperty.data = LayerEffects$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("lsct")) {
            layerProperty.data = LayerSection$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("luni")) {
            layerProperty.data = UnicodeString$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("SoCo")) {
            layerProperty.data = SolidColorAdjustment$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("TySh")) {
            layerProperty.data = TypeToolObject$$ChunkIO.read(in, stack);
        } else if (layerProperty.key.equals("vmsk")) {
            layerProperty.data = VectorMask$$ChunkIO.read(in, stack);
        }
        in.popRange();

        stack.removeFirst();
        return layerProperty;
    }
}
