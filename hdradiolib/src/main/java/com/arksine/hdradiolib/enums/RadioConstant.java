package com.arksine.hdradiolib.enums;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;

/**
 * Enumeration containing HD Radio Constants and their respective byte values
 */

public enum RadioConstant {
    UP(new byte[]{(byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00}),
    DOWN(new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF}),
    ONE(new byte[]{(byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00}),
    ZERO(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00}),
    SEEK_REQUEST(new byte[]{(byte)0xA5, (byte)0x00, (byte)0x00, (byte)0x00});


    private static final String TAG = RadioConstant.class.getSimpleName();
    private final byte[] bytes;

    RadioConstant (byte[] inBytes) {
        this.bytes = inBytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public int getByteValueAsInt() {
        ByteBuffer buf = ByteBuffer.wrap(this.bytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getInt();
    }
}
