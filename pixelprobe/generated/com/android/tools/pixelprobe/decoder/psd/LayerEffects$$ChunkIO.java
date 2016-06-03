package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class LayerEffects$$ChunkIO {
    static LayerEffects read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        LayerEffects layerEffects = new LayerEffects();
        stack.addFirst(layerEffects);

        int size = 0;
        long byteCount = 0;

        layerEffects.version = in.readInt();
        ChunkUtils.checkState(layerEffects.version == (0),
                "Value read in version does not match expected value");
        layerEffects.descriptorVersion = in.readInt();
        layerEffects.effects = Descriptor$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return layerEffects;
    }
}
