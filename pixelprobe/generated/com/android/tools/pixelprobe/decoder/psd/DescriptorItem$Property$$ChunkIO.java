package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class DescriptorItem$Property$$ChunkIO {
    static DescriptorItem.Property read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        DescriptorItem.Property property = new DescriptorItem.Property();
        stack.addFirst(property);

        int size = 0;
        long byteCount = 0;

        property.classType = DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        property.keyId = MinimumString$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return property;
    }
}
