package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class VectorMask$$ChunkIO {
    static VectorMask read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        VectorMask vectorMask = new VectorMask();
        stack.addFirst(vectorMask);

        int size = 0;
        long byteCount = 0;

        vectorMask.version = in.readInt();
        vectorMask.flags = in.readInt();
        vectorMask.pathRecords = new ArrayList<PathRecord>();
        size = (int) Math.floor((((LayerProperty) stack.get(1)).length - 8) / 26);
        PathRecord pathRecord;
        for (int i = 0; i < size; i++) {
            pathRecord = PathRecord$$ChunkIO.read(in, stack);
            vectorMask.pathRecords.add(pathRecord);
        }

        stack.removeFirst();
        return vectorMask;
    }
}
