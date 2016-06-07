package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

final class PsdFile$ShapeMask$$ChunkIO {
    static PsdFile.ShapeMask read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.ShapeMask shapeMask = new PsdFile.ShapeMask();
        stack.addFirst(shapeMask);

        int size = 0;
        long byteCount = 0;

        shapeMask.version = in.readInt();
        shapeMask.flags = in.readInt();
        shapeMask.pathRecords = new ArrayList<PsdFile.PathRecord>();
        size = (int) Math.floor((((PsdFile.LayerProperty) stack.get(1)).length - 8) / 26);
        PsdFile.PathRecord pathRecord;
        for (int i = 0; i < size; i++) {
            pathRecord = PsdFile$PathRecord$$ChunkIO.read(in, stack);
            shapeMask.pathRecords.add(pathRecord);
        }

        stack.removeFirst();
        return shapeMask;
    }
}
