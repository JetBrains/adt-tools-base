package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$ColorData$$ChunkIO {
    static PsdFile.ColorData read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.ColorData colorData = new PsdFile.ColorData();
        stack.addFirst(colorData);

        int size = 0;
        long byteCount = 0;

        colorData.length = in.readInt() & 0xffffffffL;
        byteCount = colorData.length;
        colorData.data = ChunkUtils.readByteArray(in, byteCount, 4096);

        stack.removeFirst();
        return colorData;
    }
}
