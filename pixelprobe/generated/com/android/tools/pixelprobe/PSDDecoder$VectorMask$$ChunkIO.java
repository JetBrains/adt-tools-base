package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PSDDecoder$VectorMask$$ChunkIO {
    static PSDDecoder.VectorMask read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.VectorMask vectorMask = new PSDDecoder.VectorMask();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(vectorMask);

        int size = 0;
        long byteCount = 0;

        vectorMask.version = in.readInt();
        vectorMask.flags = in.readInt();
        vectorMask.pathRecords = new ArrayList<PSDDecoder.PathRecord>();
        size = (int) Math.floor((((PSDDecoder.LayerProperty) stack.get(1)).length - 8) / 26);
        PSDDecoder.PathRecord pathRecord;
        for (int i = 0; i < size; i++) {
            pathRecord = PSDDecoder$PathRecord$$ChunkIO.read(in, stack);
            vectorMask.pathRecords.add(pathRecord);
        }

        stack.removeFirst();
        return vectorMask;
    }
}
