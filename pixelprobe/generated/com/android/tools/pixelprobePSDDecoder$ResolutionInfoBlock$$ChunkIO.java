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
        resolutionInfoBlock.horizontalUnit = PSDDecoder.ResolutionUnit.values()[in.readUnsignedShort()];
        resolutionInfoBlock.widthUnit = PSDDecoder.DisplayUnit.values()[in.readUnsignedShort()];
        resolutionInfoBlock.verticalResolution = in.readInt();
        resolutionInfoBlock.verticalUnit = PSDDecoder.ResolutionUnit.values()[in.readUnsignedShort()];
        resolutionInfoBlock.heightUnit = PSDDecoder.DisplayUnit.values()[in.readUnsignedShort()];

        stack.removeFirst();
        return resolutionInfoBlock;
    }
}
