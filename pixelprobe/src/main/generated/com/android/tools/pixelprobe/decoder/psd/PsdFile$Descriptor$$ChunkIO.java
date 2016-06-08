package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

final class PsdFile$Descriptor$$ChunkIO {
    static PsdFile.Descriptor read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.Descriptor descriptor = new PsdFile.Descriptor();
        stack.addFirst(descriptor);

        int size = 0;
        long byteCount = 0;

        descriptor.name = PsdFile$UnicodeString$$ChunkIO.read(in, stack);
        descriptor.classId = PsdFile$MinimumString$$ChunkIO.read(in, stack);
        descriptor.count = in.readInt();
        descriptor.items = new HashMap<String, PsdFile.DescriptorItem>();
        size = descriptor.count;
        PsdFile.DescriptorItem descriptorItem;
        for (int i = 0; i < size; i++) {
            descriptorItem = PsdFile$DescriptorItem$$ChunkIO.read(in, stack);
            descriptor.items.put(String.valueOf(descriptorItem.key), descriptorItem);
        }

        stack.removeFirst();
        return descriptor;
    }
}
