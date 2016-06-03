package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$DescriptorItem$ClassType$$ChunkIO {
    static PsdFile.DescriptorItem.ClassType read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.DescriptorItem.ClassType classType = new PsdFile.DescriptorItem.ClassType();
        stack.addFirst(classType);

        int size = 0;
        long byteCount = 0;

        classType.name = PsdFile$UnicodeString$$ChunkIO.read(in, stack);
        classType.classId = PsdFile$MinimumString$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return classType;
    }
}
