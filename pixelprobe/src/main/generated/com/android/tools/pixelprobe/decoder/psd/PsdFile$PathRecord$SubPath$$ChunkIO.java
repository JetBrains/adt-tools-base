package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$PathRecord$SubPath$$ChunkIO {
    static PsdFile.PathRecord.SubPath read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.PathRecord.SubPath subPath = new PsdFile.PathRecord.SubPath();
        stack.addFirst(subPath);

        int size = 0;
        long byteCount = 0;

        subPath.knotCount = in.readUnsignedShort();
        subPath.op = in.readUnsignedShort();

        stack.removeFirst();
        return subPath;
    }
}
