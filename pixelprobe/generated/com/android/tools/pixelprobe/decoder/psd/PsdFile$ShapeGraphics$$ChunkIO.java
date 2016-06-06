package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdFile$ShapeGraphics$$ChunkIO {
    static PsdFile.ShapeGraphics read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.ShapeGraphics shapeGraphics = new PsdFile.ShapeGraphics();
        stack.addFirst(shapeGraphics);

        int size = 0;
        long byteCount = 0;

        shapeGraphics.key = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        shapeGraphics.version = in.readInt() & 0xffffffffL;
        shapeGraphics.graphics = PsdFile$Descriptor$$ChunkIO.read(in, stack);

        stack.removeFirst();
        return shapeGraphics;
    }
}
