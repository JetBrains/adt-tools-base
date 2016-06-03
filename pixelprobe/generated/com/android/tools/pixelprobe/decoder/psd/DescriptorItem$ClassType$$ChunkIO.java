package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class DescriptorItem$ClassType$$ChunkIO {
    static DescriptorItem.ClassType read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        DescriptorItem.ClassType classType = new DescriptorItem.ClassType();
        stack.addFirst(classType);

        int size = 0;
        long byteCount = 0;

        classType.name = UnicodeString$$ChunkIO.read(in, stack);
        classType.classId = MinimumString$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return classType;
    }
}
