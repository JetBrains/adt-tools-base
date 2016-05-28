package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$ResolutionInfoBlock$$ChunkIO {
    static PsdDecoder.ResolutionInfoBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.ResolutionInfoBlock resolutionInfoBlock = new PsdDecoder.ResolutionInfoBlock();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(resolutionInfoBlock);

        int size = 0;
        long byteCount = 0;

        resolutionInfoBlock.horizontalResolution = in.readInt();
        {
            int index = in.readUnsignedShort();
            if (index > PsdDecoder.ResolutionUnit.values().length) index = 0;
            resolutionInfoBlock.horizontalUnit = PsdDecoder.ResolutionUnit.values()[index];
        }
        {
            int index = in.readUnsignedShort();
            if (index > PsdDecoder.DisplayUnit.values().length) index = 0;
            resolutionInfoBlock.widthUnit = PsdDecoder.DisplayUnit.values()[index];
        }
        resolutionInfoBlock.verticalResolution = in.readInt();
        {
            int index = in.readUnsignedShort();
            if (index > PsdDecoder.ResolutionUnit.values().length) index = 0;
            resolutionInfoBlock.verticalUnit = PsdDecoder.ResolutionUnit.values()[index];
        }
        {
            int index = in.readUnsignedShort();
            if (index > PsdDecoder.DisplayUnit.values().length) index = 0;
            resolutionInfoBlock.heightUnit = PsdDecoder.DisplayUnit.values()[index];
        }

        stack.removeFirst();
        return resolutionInfoBlock;
    }
}
