package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$DescriptorItem$$ChunkIO {
    static PsdFile.DescriptorItem read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.DescriptorItem descriptorItem = new PsdFile.DescriptorItem();
        stack.addFirst(descriptorItem);

        int size = 0;
        long byteCount = 0;

        descriptorItem.key = PsdFile$MinimumString$$ChunkIO.read(in, stack);
        descriptorItem.value = PsdFile$DescriptorItem$Value$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return descriptorItem;
    }
}
