package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class ResolutionInfoBlock$$ChunkIO {
    static ResolutionInfoBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        ResolutionInfoBlock resolutionInfoBlock = new ResolutionInfoBlock();
        stack.addFirst(resolutionInfoBlock);

        int size = 0;
        long byteCount = 0;

        resolutionInfoBlock.horizontalResolution = in.readInt();
        resolutionInfoBlock.horizontalUnit = ResolutionUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), ResolutionUnit.values().length - 1))];
        resolutionInfoBlock.widthUnit = DisplayUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), DisplayUnit.values().length - 1))];
        resolutionInfoBlock.verticalResolution = in.readInt();
        resolutionInfoBlock.verticalUnit = ResolutionUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), ResolutionUnit.values().length - 1))];
        resolutionInfoBlock.heightUnit = DisplayUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), DisplayUnit.values().length - 1))];

        stack.removeFirst();
        return resolutionInfoBlock;
    }
}
