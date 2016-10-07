package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$PathRecord$$ChunkIO {
    static PsdFile.PathRecord read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.PathRecord pathRecord = new PsdFile.PathRecord();
        stack.addFirst(pathRecord);

        int size = 0;
        long byteCount = 0;

        pathRecord.selector = in.readShort();
        byteCount = 24;
        in.pushRange(byteCount);
        if (pathRecord.selector == 0 || pathRecord.selector == 3) {
            pathRecord.data = PsdFile$PathRecord$SubPath$$ChunkIO.read(in, stack);
        } else if (pathRecord.selector == 1 || pathRecord.selector == 2 || pathRecord.selector == 4 || pathRecord.selector == 5) {
            pathRecord.data = PsdFile$PathRecord$BezierKnot$$ChunkIO.read(in, stack);
        }
        in.popRange();

        stack.removeFirst();
        return pathRecord;
    }
}
