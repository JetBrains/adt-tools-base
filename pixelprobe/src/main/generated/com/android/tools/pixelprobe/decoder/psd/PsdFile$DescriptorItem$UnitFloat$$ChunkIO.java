package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdFile$DescriptorItem$UnitFloat$$ChunkIO {
    static PsdFile.DescriptorItem.UnitFloat read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.DescriptorItem.UnitFloat unitFloat = new PsdFile.DescriptorItem.UnitFloat();
        stack.addFirst(unitFloat);

        int size = 0;
        long byteCount = 0;

        unitFloat.unit = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        unitFloat.value = in.readFloat();

        stack.removeFirst();
        return unitFloat;
    }
}
