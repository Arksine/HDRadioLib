package com.arksine.hdradiolib.enums;

import android.os.Parcel;
import android.os.Parcelable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;

import timber.log.Timber;

/**
 * Enumeration containing Radio Bands and their respective byte values.  Implements Parcelable
 * so it may be passed though AIDL if required.
 *
 * Note: When sending via an intent, the same situation for RadioCommand applies to RadioBand.
 * See the RadioCommand class for examples on how to send and receive Parcelable Enums via intents.
 */

public enum RadioBand implements Parcelable {
    AM( new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00}),
    FM( new byte[]{(byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00});

    public static final Creator<RadioBand> CREATOR = new Creator<RadioBand>() {
        @Override
        public RadioBand createFromParcel(Parcel in) {
            return RadioBand.valueOf(in.readString());
        }

        @Override
        public RadioBand[] newArray(int size) {
            return new RadioBand[size];
        }
    };

    private final byte[] bytes;

    RadioBand (byte[] inBytes) {
        this.bytes = inBytes;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(this.toString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public int getByteValueAsInt() {
        ByteBuffer buf = ByteBuffer.wrap(this.bytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getInt();
    }

    /**
     * Compares an incoming byte value to the integer representation of all the values
     * in the RadioBand enum.  If found, the RadioBand is returned.
     *
     * @param byteValue     The integer representation of the value to find
     * @return              The matching band if found, null if not found
     */
    public static RadioBand getBandFromValue(int byteValue) {

        for (RadioBand band : EnumSet.allOf(RadioBand.class)) {
            if (band.getByteValueAsInt() == byteValue) {
                return band;
            }
        }

        Timber.i("No matching Band found for value: %#x", byteValue);
        return null;
    }
}
