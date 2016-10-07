package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$PathRecord$BezierKnot$$ChunkIO {
    static PsdFile.PathRecord.BezierKnot read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.PathRecord.BezierKnot bezierKnot = new PsdFile.PathRecord.BezierKnot();
        stack.addFirst(bezierKnot);

        int size = 0;
        long byteCount = 0;

        bezierKnot.controlEnterY = in.readInt();
        bezierKnot.controlEnterX = in.readInt();
        bezierKnot.anchorY = in.readInt();
        bezierKnot.anchorX = in.readInt();
        bezierKnot.controlExitY = in.readInt();
        bezierKnot.controlExitX = in.readInt();

        stack.removeFirst();
        return bezierKnot;
    }
}
