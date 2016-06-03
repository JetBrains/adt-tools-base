package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

final class PsdFile$ImageResources$$ChunkIO {
    static PsdFile.ImageResources read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.ImageResources imageResources = new PsdFile.ImageResources();
        stack.addFirst(imageResources);

        int size = 0;
        long byteCount = 0;

        imageResources.length = in.readInt() & 0xffffffffL;
        imageResources.blocks = new HashMap<Integer, PsdFile.ImageResourceBlock>();
        byteCount = imageResources.length;
        in.pushRange(byteCount);
        PsdFile.ImageResourceBlock imageResourceBlock;
        while (in.available() > 0) {
            imageResourceBlock = PsdFile$ImageResourceBlock$$ChunkIO.read(in, stack);
            imageResources.blocks.put(imageResourceBlock.id, imageResourceBlock);
        }
        in.popRange();

        stack.removeFirst();
        return imageResources;
    }
}
