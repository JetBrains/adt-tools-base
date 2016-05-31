package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdDecoder$LayerSection$$ChunkIO {
    static PsdDecoder.LayerSection read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.LayerSection layerSection = new PsdDecoder.LayerSection();
        stack.addFirst(layerSection);

        int size = 0;
        long byteCount = 0;

        layerSection.type = PsdDecoder.LayerSection.Type.values()[
                Math.max(0, Math.min(in.readInt(), PsdDecoder.LayerSection.Type.values().length - 1))];
        if (((PsdDecoder.LayerProperty) stack.get(1)).length >= 12) {
            layerSection.signature = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        }
        if (((PsdDecoder.LayerProperty) stack.get(1)).length >= 12) {
            layerSection.blendMode = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        }
        if (((PsdDecoder.LayerProperty) stack.get(1)).length >= 16) {
            layerSection.subType = in.readInt();
        }

        stack.removeFirst();
        return layerSection;
    }
}
