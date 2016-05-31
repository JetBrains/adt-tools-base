package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdDecoder$VectorMask$$ChunkIO {
    static PsdDecoder.VectorMask read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.VectorMask vectorMask = new PsdDecoder.VectorMask();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(vectorMask);

        int size = 0;
        long byteCount = 0;

        vectorMask.version = in.readInt();
        vectorMask.flags = in.readInt();
        vectorMask.pathRecords = new ArrayList<PsdDecoder.PathRecord>();
        size = (int) Math.floor((((PsdDecoder.LayerProperty) stack.get(1)).length - 8) / 26);
        PsdDecoder.PathRecord pathRecord;
        for (int i = 0; i < size; i++) {
            pathRecord = PsdDecoder$PathRecord$$ChunkIO.read(in, stack);
            vectorMask.pathRecords.add(pathRecord);
        }

        stack.removeFirst();
        return vectorMask;
    }
}
