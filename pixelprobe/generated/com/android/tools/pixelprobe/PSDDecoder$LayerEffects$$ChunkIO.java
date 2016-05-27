package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$LayerEffects$$ChunkIO {
    static PSDDecoder.LayerEffects read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.LayerEffects layerEffects = new PSDDecoder.LayerEffects();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(layerEffects);

        int size = 0;
        long byteCount = 0;

        layerEffects.version = in.readInt();
        ChunkUtils.checkState(layerEffects.version == (0),
                "Value read in version does not match expected value");
        layerEffects.descriptorVersion = in.readInt();
        layerEffects.effects = PSDDecoder$Descriptor$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return layerEffects;
    }
}
