package com.android.tools.pixelprobe.decoder;

import com.android.tools.chunkio.RangedInputStream;
import java.io.IOException;
import java.util.LinkedList;

final class PsdDecoder$MaskAdjustment$$ChunkIO {
    static PsdDecoder.MaskAdjustment read(RangedInputStream in, LinkedList<Object> stack) throws IOException {
        PsdDecoder.MaskAdjustment maskAdjustment = new PsdDecoder.MaskAdjustment();
        stack.addFirst(maskAdjustment);

        int size = 0;
        long byteCount = 0;

        maskAdjustment.length = in.readInt() & 0xffffffffL;
        if (maskAdjustment.length == 0) {
            stack.removeFirst();
            return maskAdjustment;
        }
        maskAdjustment.top = in.readInt() & 0xffffffffL;
        maskAdjustment.left = in.readInt() & 0xffffffffL;
        maskAdjustment.bottom = in.readInt() & 0xffffffffL;
        maskAdjustment.right = in.readInt() & 0xffffffffL;
        maskAdjustment.defaultColor = (short) (in.readByte() & 0xff);
        maskAdjustment.flags = in.readByte();
        if ((maskAdjustment.flags & 0x10) != 0) {
            maskAdjustment.maskParameters = in.readByte();
        }
        if ((maskAdjustment.maskParameters & 0x1) != 0) {
            maskAdjustment.userMaskDensity = (short) (in.readByte() & 0xff);
        }
        if ((maskAdjustment.maskParameters & 0x2) != 0) {
            maskAdjustment.userMaskFeather = in.readDouble();
        }
        if ((maskAdjustment.maskParameters & 0x4) != 0) {
            maskAdjustment.vectorMaskDensity = (short) (in.readByte() & 0xff);
        }
        if ((maskAdjustment.maskParameters & 0x8) != 0) {
            maskAdjustment.vectorMaskFeather = in.readDouble();
        }
        if (maskAdjustment.length == 20) {
            maskAdjustment.padding = in.readShort();
            if (maskAdjustment.length == 20) {
                stack.removeFirst();
                return maskAdjustment;
            }
        }
        maskAdjustment.realFlags = in.readByte();
        maskAdjustment.userMaskBackground = (short) (in.readByte() & 0xff);
        maskAdjustment.realTop = in.readInt() & 0xffffffffL;
        maskAdjustment.realLeft = in.readInt() & 0xffffffffL;
        maskAdjustment.realBottom = in.readInt() & 0xffffffffL;
        maskAdjustment.realRight = in.readInt() & 0xffffffffL;

        stack.removeFirst();
        return maskAdjustment;
    }
}
