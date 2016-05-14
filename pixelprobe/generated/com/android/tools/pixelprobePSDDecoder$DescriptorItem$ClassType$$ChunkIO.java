package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$DescriptorItem$ClassType$$ChunkIO {
    static PSDDecoder.DescriptorItem.ClassType read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.DescriptorItem.ClassType classType = new PSDDecoder.DescriptorItem.ClassType();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(classType);

        int size = 0;
        long byteCount = 0;

        classType.name = PSDDecoder$UnicodeString$$ChunkIO.read(in, stack);
        classType.classId = PSDDecoder$MinimumString$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return classType;
    }
}
