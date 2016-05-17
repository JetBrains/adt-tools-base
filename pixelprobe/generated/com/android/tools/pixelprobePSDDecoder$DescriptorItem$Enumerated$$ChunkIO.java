package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$DescriptorItem$Enumerated$$ChunkIO {
    static PSDDecoder.DescriptorItem.Enumerated read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.DescriptorItem.Enumerated enumerated = new PSDDecoder.DescriptorItem.Enumerated();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(enumerated);

        int size = 0;
        long byteCount = 0;

        enumerated.type = PSDDecoder$MinimumString$$ChunkIO.read(in, stack);
        enumerated.value = PSDDecoder$MinimumString$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return enumerated;
    }
}
