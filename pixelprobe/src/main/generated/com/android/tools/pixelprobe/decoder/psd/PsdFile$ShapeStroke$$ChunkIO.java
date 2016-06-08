package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$ShapeStroke$$ChunkIO {
    static PsdFile.ShapeStroke read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.ShapeStroke shapeStroke = new PsdFile.ShapeStroke();
        stack.addFirst(shapeStroke);

        int size = 0;
        long byteCount = 0;

        shapeStroke.version = in.readInt() & 0xffffffffL;
        shapeStroke.stroke = PsdFile$Descriptor$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return shapeStroke;
    }
}
