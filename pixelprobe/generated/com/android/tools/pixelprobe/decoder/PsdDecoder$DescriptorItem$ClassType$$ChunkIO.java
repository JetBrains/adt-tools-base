package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$DescriptorItem$ClassType$$ChunkIO {
    static PsdDecoder.DescriptorItem.ClassType read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.DescriptorItem.ClassType classType = new PsdDecoder.DescriptorItem.ClassType();
        stack.addFirst(classType);

        int size = 0;
        long byteCount = 0;

        classType.name = PsdDecoder$UnicodeString$$ChunkIO.read(in, stack);
        classType.classId = PsdDecoder$MinimumString$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return classType;
    }
}
