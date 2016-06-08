package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$SolidColorAdjustment$$ChunkIO {
    static PsdFile.SolidColorAdjustment read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.SolidColorAdjustment solidColorAdjustment = new PsdFile.SolidColorAdjustment();
        stack.addFirst(solidColorAdjustment);

        int size = 0;
        long byteCount = 0;

        solidColorAdjustment.version = in.readInt();
        solidColorAdjustment.solidColor = PsdFile$Descriptor$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return solidColorAdjustment;
    }
}
