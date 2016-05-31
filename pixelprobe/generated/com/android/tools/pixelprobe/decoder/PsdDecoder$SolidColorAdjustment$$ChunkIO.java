package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$SolidColorAdjustment$$ChunkIO {
    static PsdDecoder.SolidColorAdjustment read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.SolidColorAdjustment solidColorAdjustment = new PsdDecoder.SolidColorAdjustment();
        stack.addFirst(solidColorAdjustment);

        int size = 0;
        long byteCount = 0;

        solidColorAdjustment.version = in.readInt();
        solidColorAdjustment.solidColor = PsdDecoder$Descriptor$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return solidColorAdjustment;
    }
}
