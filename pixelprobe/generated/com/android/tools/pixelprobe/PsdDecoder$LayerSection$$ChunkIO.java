package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdDecoder$LayerSection$$ChunkIO {
    static PsdDecoder.LayerSection read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.LayerSection layerSection = new PsdDecoder.LayerSection();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(layerSection);

        int size = 0;
        long byteCount = 0;

        {
            int index = in.readInt();
            if (index > PsdDecoder.LayerSection.Type.values().length) index = 0;
            layerSection.type = PsdDecoder.LayerSection.Type.values()[index];
        }
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
