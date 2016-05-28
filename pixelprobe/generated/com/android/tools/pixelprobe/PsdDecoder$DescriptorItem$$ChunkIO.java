package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$DescriptorItem$$ChunkIO {
    static PsdDecoder.DescriptorItem read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.DescriptorItem descriptorItem = new PsdDecoder.DescriptorItem();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(descriptorItem);

        int size = 0;
        long byteCount = 0;

        descriptorItem.key = PsdDecoder$MinimumString$$ChunkIO.read(in, stack);
        descriptorItem.value = PsdDecoder$DescriptorItem$Value$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return descriptorItem;
    }
}
