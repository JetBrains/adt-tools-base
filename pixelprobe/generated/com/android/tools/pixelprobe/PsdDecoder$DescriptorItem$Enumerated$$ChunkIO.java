package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$DescriptorItem$Enumerated$$ChunkIO {
    static PsdDecoder.DescriptorItem.Enumerated read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.DescriptorItem.Enumerated enumerated = new PsdDecoder.DescriptorItem.Enumerated();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(enumerated);

        int size = 0;
        long byteCount = 0;

        enumerated.type = PsdDecoder$MinimumString$$ChunkIO.read(in, stack);
        enumerated.value = PsdDecoder$MinimumString$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return enumerated;
    }
}
