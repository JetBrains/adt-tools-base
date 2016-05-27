package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$DescriptorItem$Property$$ChunkIO {
    static PSDDecoder.DescriptorItem.Property read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.DescriptorItem.Property property = new PSDDecoder.DescriptorItem.Property();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(property);

        int size = 0;
        long byteCount = 0;

        property.classType = PSDDecoder$DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        property.keyId = PSDDecoder$MinimumString$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return property;
    }
}
