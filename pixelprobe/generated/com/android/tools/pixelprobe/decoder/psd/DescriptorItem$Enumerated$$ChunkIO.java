package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class DescriptorItem$Enumerated$$ChunkIO {
    static DescriptorItem.Enumerated read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        DescriptorItem.Enumerated enumerated = new DescriptorItem.Enumerated();
        stack.addFirst(enumerated);

        int size = 0;
        long byteCount = 0;

        enumerated.type = MinimumString$$ChunkIO.read(in, stack);
        enumerated.value = MinimumString$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return enumerated;
    }
}
