package com.android.tools.pixelprobe;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PSDDecoder$ResolutionInfoBlock$$ChunkIO {
    static PSDDecoder.ResolutionInfoBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PSDDecoder.ResolutionInfoBlock resolutionInfoBlock = new PSDDecoder.ResolutionInfoBlock();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(resolutionInfoBlock);

        int size = 0;
        long byteCount = 0;

        resolutionInfoBlock.horizontalResolution = in.readInt();
        {
            int index = in.readUnsignedShort();
            if (index > PSDDecoder.ResolutionUnit.values().length) index = 0;
            resolutionInfoBlock.horizontalUnit = PSDDecoder.ResolutionUnit.values()[index];
        }
        {
            int index = in.readUnsignedShort();
            if (index > PSDDecoder.DisplayUnit.values().length) index = 0;
            resolutionInfoBlock.widthUnit = PSDDecoder.DisplayUnit.values()[index];
        }
        resolutionInfoBlock.verticalResolution = in.readInt();
        {
            int index = in.readUnsignedShort();
            if (index > PSDDecoder.ResolutionUnit.values().length) index = 0;
            resolutionInfoBlock.verticalUnit = PSDDecoder.ResolutionUnit.values()[index];
        }
        {
            int index = in.readUnsignedShort();
            if (index > PSDDecoder.DisplayUnit.values().length) index = 0;
            resolutionInfoBlock.heightUnit = PSDDecoder.DisplayUnit.values()[index];
        }

        stack.removeFirst();
        return resolutionInfoBlock;
    }
}
