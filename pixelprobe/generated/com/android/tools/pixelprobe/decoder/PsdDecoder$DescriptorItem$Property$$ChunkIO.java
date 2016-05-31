package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$DescriptorItem$Property$$ChunkIO {
    static PsdDecoder.DescriptorItem.Property read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.DescriptorItem.Property property = new PsdDecoder.DescriptorItem.Property();
        stack.addFirst(property);

        int size = 0;
        long byteCount = 0;

        property.classType = PsdDecoder$DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        property.keyId = PsdDecoder$MinimumString$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return property;
    }
}
