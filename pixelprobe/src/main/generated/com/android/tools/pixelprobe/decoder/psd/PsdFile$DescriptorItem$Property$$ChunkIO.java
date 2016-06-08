package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$DescriptorItem$Property$$ChunkIO {
    static PsdFile.DescriptorItem.Property read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.DescriptorItem.Property property = new PsdFile.DescriptorItem.Property();
        stack.addFirst(property);

        int size = 0;
        long byteCount = 0;

        property.classType = PsdFile$DescriptorItem$ClassType$$ChunkIO.read(in, stack);
        property.keyId = PsdFile$MinimumString$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return property;
    }
}
