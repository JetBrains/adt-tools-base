package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkUtils;
import com.android.tools.chunkio.RangedInputStream;
import com.android.tools.pixelprobe.ColorMode;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

final class Header$$ChunkIO {
    static Header read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        Header header = new Header();
        stack.addFirst(header);

        int size = 0;
        long byteCount = 0;

        header.signature = ChunkUtils.readString(in, 4, Charset.forName("ISO-8859-1"));
        ChunkUtils.checkState(header.signature.equals("8BPS"),
                "Value read in signature does not match expected value");
        header.version = in.readShort();
        ChunkUtils.checkState(header.version == (1),
                "Value read in version does not match expected value");
        /* header.reserved */
        ChunkUtils.skip(in, 6);
        header.channels = in.readUnsignedShort();
        header.height = in.readInt();
        header.width = in.readInt();
        header.depth = in.readShort();
        header.colorMode = ColorMode.values()[
                Math.max(0, Math.min(in.readUnsignedShort(), ColorMode.values().length - 1))];

        stack.removeFirst();
        return header;
    }
}
