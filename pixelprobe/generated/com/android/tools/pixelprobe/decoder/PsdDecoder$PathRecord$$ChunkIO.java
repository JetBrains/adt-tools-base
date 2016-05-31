package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$PathRecord$$ChunkIO {
    static PsdDecoder.PathRecord read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.PathRecord pathRecord = new PsdDecoder.PathRecord();
        stack.addFirst(pathRecord);

        int size = 0;
        long byteCount = 0;

        pathRecord.selector = in.readShort();
        byteCount = 24;
        in.pushRange(byteCount);
        if (pathRecord.selector == 0 || pathRecord.selector == 3) {
            pathRecord.data = in.readInt();
            ChunkUtils.skip(in, 20);
        } else if (pathRecord.selector == 1 || pathRecord.selector == 2 || pathRecord.selector == 4 || pathRecord.selector == 5) {
            pathRecord.data = PsdDecoder$PathRecord$BezierKnot$$ChunkIO.read(in, stack);
        }
        in.popRange();

        stack.removeFirst();
        return pathRecord;
    }
}
