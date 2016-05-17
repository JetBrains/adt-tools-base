package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$SolidColorAdjustment$$ChunkIO {
    static PSDDecoder.SolidColorAdjustment read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.SolidColorAdjustment solidColorAdjustment = new PSDDecoder.SolidColorAdjustment();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(solidColorAdjustment);

        int size = 0;
        long byteCount = 0;

        solidColorAdjustment.version = in.readInt();
        solidColorAdjustment.solidColor = PSDDecoder$Descriptor$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return solidColorAdjustment;
    }
}
