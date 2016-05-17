package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

final class PSDDecoder$Descriptor$$ChunkIO {
    static PSDDecoder.Descriptor read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.Descriptor descriptor = new PSDDecoder.Descriptor();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(descriptor);

        int size = 0;
        long byteCount = 0;

        descriptor.name = PSDDecoder$UnicodeString$$ChunkIO.read(in, stack);
        descriptor.classId = PSDDecoder$MinimumString$$ChunkIO.read(in, stack);
        descriptor.count = in.readInt();
        descriptor.items = new HashMap<String, PSDDecoder.DescriptorItem>();
        size = descriptor.count;
        PSDDecoder.DescriptorItem descriptorItem;
        for (int i = 0; i < size; i++) {
            descriptorItem = PSDDecoder$DescriptorItem$$ChunkIO.read(in, stack);
            descriptor.items.put(String.valueOf(descriptorItem.key), descriptorItem);
        }

        stack.removeFirst();
        return descriptor;
    }
}
