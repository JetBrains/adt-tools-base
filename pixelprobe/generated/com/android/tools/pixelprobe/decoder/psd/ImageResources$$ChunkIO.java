package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

final class ImageResources$$ChunkIO {
    static ImageResources read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        ImageResources imageResources = new ImageResources();
        stack.addFirst(imageResources);

        int size = 0;
        long byteCount = 0;

        imageResources.length = in.readInt() & 0xffffffffL;
        imageResources.blocks = new HashMap<Integer, ImageResourceBlock>();
        byteCount = imageResources.length;
        in.pushRange(byteCount);
        ImageResourceBlock imageResourceBlock;
        while (in.available() > 0) {
            imageResourceBlock = ImageResourceBlock$$ChunkIO.read(in, stack);
            imageResources.blocks.put(imageResourceBlock.id, imageResourceBlock);
        }
        in.popRange();

        stack.removeFirst();
        return imageResources;
    }
}
