package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

final class Descriptor$$ChunkIO {
    static Descriptor read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        Descriptor descriptor = new Descriptor();
        stack.addFirst(descriptor);

        int size = 0;
        long byteCount = 0;

        descriptor.name = UnicodeString$$ChunkIO.read(in, stack);
        descriptor.classId = MinimumString$$ChunkIO.read(in, stack);
        descriptor.count = in.readInt();
        descriptor.items = new HashMap<String, DescriptorItem>();
        size = descriptor.count;
        DescriptorItem descriptorItem;
        for (int i = 0; i < size; i++) {
            descriptorItem = DescriptorItem$$ChunkIO.read(in, stack);
            descriptor.items.put(String.valueOf(descriptorItem.key), descriptorItem);
        }

        stack.removeFirst();
        return descriptor;
    }
}
