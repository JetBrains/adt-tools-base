package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$ResolutionInfoBlock$$ChunkIO {
    static PsdDecoder.ResolutionInfoBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.ResolutionInfoBlock resolutionInfoBlock = new PsdDecoder.ResolutionInfoBlock();
        stack.addFirst(resolutionInfoBlock);

        int size = 0;
        long byteCount = 0;

        resolutionInfoBlock.horizontalResolution = in.readInt();
        resolutionInfoBlock.horizontalUnit = PsdDecoder.ResolutionUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), PsdDecoder.ResolutionUnit.values().length - 1))];
        resolutionInfoBlock.widthUnit = PsdDecoder.DisplayUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), PsdDecoder.DisplayUnit.values().length - 1))];
        resolutionInfoBlock.verticalResolution = in.readInt();
        resolutionInfoBlock.verticalUnit = PsdDecoder.ResolutionUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), PsdDecoder.ResolutionUnit.values().length - 1))];
        resolutionInfoBlock.heightUnit = PsdDecoder.DisplayUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), PsdDecoder.DisplayUnit.values().length - 1))];

        stack.removeFirst();
        return resolutionInfoBlock;
    }
}
