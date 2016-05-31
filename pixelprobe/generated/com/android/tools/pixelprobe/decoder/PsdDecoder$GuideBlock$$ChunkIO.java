package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$GuideBlock$$ChunkIO {
    static PsdDecoder.GuideBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.GuideBlock guideBlock = new PsdDecoder.GuideBlock();
        stack.addFirst(guideBlock);

        int size = 0;
        long byteCount = 0;

        guideBlock.location = in.readInt();
        guideBlock.orientation = PsdDecoder.Orientation.values()[
                Math.max(0, Math.min(in.readUnsignedByte(), PsdDecoder.Orientation.values().length - 1))];

        stack.removeFirst();
        return guideBlock;
    }
}
