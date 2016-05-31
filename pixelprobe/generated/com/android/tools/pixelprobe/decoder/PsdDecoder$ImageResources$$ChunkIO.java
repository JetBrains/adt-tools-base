package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdDecoder$ImageResources$$ChunkIO {
    static PsdDecoder.ImageResources read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.ImageResources imageResources = new PsdDecoder.ImageResources();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(imageResources);

        int size = 0;
        long byteCount = 0;

        imageResources.length = in.readInt() & 0xffffffffL;
        imageResources.blocks = new ArrayList<PsdDecoder.ImageResourceBlock>();
        byteCount = imageResources.length;
        in.pushRange(byteCount);
        PsdDecoder.ImageResourceBlock imageResourceBlock;
        while (in.available() > 0) {
            imageResourceBlock = PsdDecoder$ImageResourceBlock$$ChunkIO.read(in, stack);
            imageResources.blocks.add(imageResourceBlock);
        }
        in.popRange();

        stack.removeFirst();
        return imageResources;
    }
}
