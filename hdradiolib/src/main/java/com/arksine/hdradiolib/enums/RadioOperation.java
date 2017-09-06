package com.arksine.hdradiolib.enums;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;

import timber.log.Timber;

/**
 *  Enumeration containing valid HD Radio operations, and their respective byte values
 */

public enum RadioOperation {
    SET(new byte[]{(byte)0x00, (byte)0x00}),
    GET(new byte[]{(byte)0x01, (byte)0x00}),
    REPLY(new byte[]{(byte)0x02, (byte)0x00});

    private final byte[] bytes;

    RadioOperation (byte[] inBytes) {
        this.bytes = inBytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public int getByteValueAsInt() {
        ByteBuffer buf = ByteBuffer.wrap(this.bytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getShort();
    }

    /**
     * Compares an incoming byte value to the integer representation of all the values
     * in the RadioOperation enum.  If found, the RadioOperation is returned.
     *
     * @param byteValue     The integer representation of the value to find
     * @return              The matching operation if found, null if not found
     */
    public static RadioOperation getOperationFromValue(int byteValue) {

        for (RadioOperation op : EnumSet.allOf(RadioOperation.class)) {
            if (op.getByteValueAsInt() == byteValue) {
                return op;
            }
        }

        Timber.i("No matching Operation found for value: %#x", byteValue);
        return null;
    }
}
