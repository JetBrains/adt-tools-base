package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdFile$VectorMask$$ChunkIO {
    static PsdFile.VectorMask read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.VectorMask vectorMask = new PsdFile.VectorMask();
        stack.addFirst(vectorMask);

        int size = 0;
        long byteCount = 0;

        vectorMask.version = in.readInt();
        vectorMask.flags = in.readInt();
        vectorMask.pathRecords = new ArrayList<PsdFile.PathRecord>();
        size = (int) Math.floor((((PsdFile.LayerProperty) stack.get(1)).length - 8) / 26);
        PsdFile.PathRecord pathRecord;
        for (int i = 0; i < size; i++) {
            pathRecord = PsdFile$PathRecord$$ChunkIO.read(in, stack);
            vectorMask.pathRecords.add(pathRecord);
        }

        stack.removeFirst();
        return vectorMask;
    }
}
