package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$PathRecord$BezierKnot$$ChunkIO {
    static PsdDecoder.PathRecord.BezierKnot read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.PathRecord.BezierKnot bezierKnot = new PsdDecoder.PathRecord.BezierKnot();
        if (stack == null) stack = new LinkedList<Object>();
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
