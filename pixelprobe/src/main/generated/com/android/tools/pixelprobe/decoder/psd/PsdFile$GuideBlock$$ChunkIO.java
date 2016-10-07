package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$GuideBlock$$ChunkIO {
    static PsdFile.GuideBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.GuideBlock guideBlock = new PsdFile.GuideBlock();
        stack.addFirst(guideBlock);

        int size = 0;
        long byteCount = 0;

        guideBlock.location = in.readInt();
        guideBlock.orientation = PsdFile.Orientation.values()[
                Math.max(0, Math.min(in.readUnsignedByte(), PsdFile.Orientation.values().length - 1))];

        stack.removeFirst();
        return guideBlock;
    }
}
