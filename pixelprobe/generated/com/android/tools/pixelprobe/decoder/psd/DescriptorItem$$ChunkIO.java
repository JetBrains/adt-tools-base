package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class DescriptorItem$$ChunkIO {
    static DescriptorItem read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        DescriptorItem descriptorItem = new DescriptorItem();
        stack.addFirst(descriptorItem);

        int size = 0;
        long byteCount = 0;

        descriptorItem.key = MinimumString$$ChunkIO.read(in, stack);
        descriptorItem.value = DescriptorItem$Value$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return descriptorItem;
    }
}
