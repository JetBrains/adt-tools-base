package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$BlendRange$$ChunkIO {
    static PsdDecoder.BlendRange read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.BlendRange blendRange = new PsdDecoder.BlendRange();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(blendRange);

        int size = 0;
        long byteCount = 0;

        blendRange.srcBlackIn = (short) (in.readByte() & 0xff);
        blendRange.srcWhiteIn = (short) (in.readByte() & 0xff);
        blendRange.srcBlackOut = (short) (in.readByte() & 0xff);
        blendRange.srcWhiteOut = (short) (in.readByte() & 0xff);
        blendRange.dstBlackIn = (short) (in.readByte() & 0xff);
        blendRange.dstWhiteIn = (short) (in.readByte() & 0xff);
        blendRange.dstBlackOut = (short) (in.readByte() & 0xff);
        blendRange.dstWhiteOut = (short) (in.readByte() & 0xff);

        stack.removeFirst();
        return blendRange;
    }
}
