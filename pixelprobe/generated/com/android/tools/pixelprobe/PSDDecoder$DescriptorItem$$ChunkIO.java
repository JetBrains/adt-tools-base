package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$DescriptorItem$$ChunkIO {
    static PSDDecoder.DescriptorItem read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.DescriptorItem descriptorItem = new PSDDecoder.DescriptorItem();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(descriptorItem);

        int size = 0;
        long byteCount = 0;

        descriptorItem.key = PSDDecoder$MinimumString$$ChunkIO.read(in, stack);
        descriptorItem.value = PSDDecoder$DescriptorItem$Value$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return descriptorItem;
    }
}
