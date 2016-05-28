package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

final class PsdDecoder$Descriptor$$ChunkIO {
    static PsdDecoder.Descriptor read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.Descriptor descriptor = new PsdDecoder.Descriptor();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(descriptor);

        int size = 0;
        long byteCount = 0;

        descriptor.name = PsdDecoder$UnicodeString$$ChunkIO.read(in, stack);
        descriptor.classId = PsdDecoder$MinimumString$$ChunkIO.read(in, stack);
        descriptor.count = in.readInt();
        descriptor.items = new HashMap<String, PsdDecoder.DescriptorItem>();
        size = descriptor.count;
        PsdDecoder.DescriptorItem descriptorItem;
        for (int i = 0; i < size; i++) {
            descriptorItem = PsdDecoder$DescriptorItem$$ChunkIO.read(in, stack);
            descriptor.items.put(String.valueOf(descriptorItem.key), descriptorItem);
        }

        stack.removeFirst();
        return descriptor;
    }
}
