package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdFile$ResolutionInfoBlock$$ChunkIO {
    static PsdFile.ResolutionInfoBlock read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdFile.ResolutionInfoBlock resolutionInfoBlock = new PsdFile.ResolutionInfoBlock();
        stack.addFirst(resolutionInfoBlock);

        int size = 0;
        long byteCount = 0;

        resolutionInfoBlock.horizontalResolution = in.readInt();
        resolutionInfoBlock.horizontalUnit = PsdFile.ResolutionUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), PsdFile.ResolutionUnit.values().length - 1))];
        resolutionInfoBlock.widthUnit = PsdFile.DisplayUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), PsdFile.DisplayUnit.values().length - 1))];
        resolutionInfoBlock.verticalResolution = in.readInt();
        resolutionInfoBlock.verticalUnit = PsdFile.ResolutionUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), PsdFile.ResolutionUnit.values().length - 1))];
        resolutionInfoBlock.heightUnit = PsdFile.DisplayUnit.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), PsdFile.DisplayUnit.values().length - 1))];

        stack.removeFirst();
        return resolutionInfoBlock;
    }
}
