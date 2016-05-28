package com.android.tools.pixelprobe;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class PsdDecoder$FileHeader$$ChunkIO {
    static PsdDecoder.FileHeader read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.FileHeader fileHeader = new PsdDecoder.FileHeader();
        if (stack == null) stack = new LinkedList<Object>();
        stack.addFirst(fileHeader);

        int size = 0;
        long byteCount = 0;

        fileHeader.signature = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        ChunkUtils.checkState(fileHeader.signature.equals("8BPS"),
                "Value read in signature does not match expected value");
        fileHeader.version = in.readShort();
        ChunkUtils.checkState(fileHeader.version == (1),
                "Value read in version does not match expected value");
        /* fileHeader.reserved */
        ChunkUtils.skip(in, 6);
        fileHeader.channels = in.readUnsignedShort();
        fileHeader.height = in.readInt();
        fileHeader.width = in.readInt();
        fileHeader.depth = in.readShort();
        {
            int index = in.readUnsignedShort();
            if (index > ColorMode.values().length) index = 0;
            fileHeader.colorMode = ColorMode.values()[index];
        }
        ChunkUtils.checkState(fileHeader.colorMode == (ColorMode.RGB),
                "Value read in colorMode does not match expected value");

        stack.removeFirst();
        return fileHeader;
    }
}
