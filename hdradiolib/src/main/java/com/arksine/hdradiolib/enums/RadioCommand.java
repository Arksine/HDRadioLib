package com.arksine.hdradiolib.enums;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Enumeration containing HD Radio commands, associated with their respective byte values
 * and expected data type.  This enumeration implements parcelable so it can be passed though
 * AIDL if required.
 *
 * NOTE: If passing a RadioCommand via intent, you must cast the command to (Parcelable) or
 * (Serializable).  This is because Java enums already implement Serializable, and the compiler
 * gets confused about which interface to use when calling putExtra.  See the example below
 *
 * To add a radio command to an intent, using parcelable:
 * intent.putExtra("Command", (Parcelable)RadioCommand.POWER);
 *
 * To retreive the intent:
 * RadioCommand cmd = (RadioCommand) intent.getParcelableExtra("Command");
 *
 * Alternatively you could cast to Serializable in putExtra, then use getSerializableExtra to
 * retrieve it.
 */

public enum RadioCommand implements Parcelable {
    POWER(new byte[]{(byte)0x01, (byte)0x00}, Type.BOOLEAN),
    MUTE(new byte[]{(byte)0x02, (byte)0x00}, Type.BOOLEAN),
    SIGNAL_STRENGTH(new byte[]{(byte)0x01, (byte)0x01}, Type.INT),
    TUNE(new byte[]{(byte)0x02, (byte)0x01}, Type.TUNEINFO),
    SEEK(new byte[]{(byte)0x03, (byte)0x01}, Type.TUNEINFO),
    HD_ACTIVE(new byte[]{(byte)0x01, (byte)0x02}, Type.BOOLEAN),
    HD_STREAM_LOCK(new byte[]{(byte)0x02, (byte)0x02}, Type.BOOLEAN),
    HD_SIGNAL_STRENGTH(new byte[]{(byte)0x03, (byte)0x02}, Type.INT),
    HD_SUBCHANNEL(new byte[]{(byte)0x04, (byte)0x02}, Type.INT),
    HD_SUBCHANNEL_COUNT(new byte[]{(byte)0x05, (byte)0x02}, Type.INT),
    HD_ENABLE_HD_TUNER(new byte[]{(byte)0x06, (byte)0x02}, Type.BOOLEAN),    // TODO: this appears to return an integer type
    HD_TITLE(new byte[]{(byte)0x07, (byte)0x02}, Type.HDSONGINFO),
    HD_ARTIST(new byte[]{(byte)0x08, (byte)0x02}, Type.HDSONGINFO),
    HD_CALLSIGN(new byte[]{(byte)0x09, (byte)0x02}, Type.STRING),
    HD_STATION_NAME(new byte[]{(byte)0x10, (byte)0x02}, Type.STRING),
    HD_UNIQUE_ID(new byte[]{(byte)0x11, (byte)0x02}, Type.STRING),
    HD_API_VERSION(new byte[]{(byte)0x12, (byte)0x02}, Type.STRING),
    HD_HW_VERSION(new byte[]{(byte)0x13, (byte)0x02}, Type.STRING),
    RDS_ENABLED(new byte[]{(byte)0x01, (byte)0x03}, Type.BOOLEAN),
    RDS_GENRE(new byte[]{(byte)0x07, (byte)0x03}, Type.STRING),
    RDS_PROGRAM_SERVICE(new byte[]{(byte)0x08, (byte)0x03}, Type.STRING),
    RDS_RADIO_TEXT(new byte[]{(byte)0x09, (byte)0x03}, Type.STRING),
    VOLUME(new byte[]{(byte)0x03, (byte)0x04}, Type.INT),
    BASS(new byte[]{(byte)0x04, (byte)0x04}, Type.INT),
    TREBLE(new byte[]{(byte)0x05, (byte)0x04}, Type.INT),
    COMPRESSION(new byte[]{(byte)0x06, (byte)0x04}, Type.INT); // TODO: need to test this, doesn't appear to retun an int

    private static final String TAG = RadioCommand.class.getSimpleName();

    // Cache an array of the command list
    private static final RadioCommand[] COMMAND_ARRAY = RadioCommand.values();
    public static final Creator<RadioCommand> CREATOR = new Creator<RadioCommand>() {
        @Override
        public RadioCommand createFromParcel(Parcel in) {
            return COMMAND_ARRAY[in.readInt()];
        }

        @Override
        public RadioCommand[] newArray(int size) {
            return new RadioCommand[size];
        }
    };

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(ordinal());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public enum Type {INT, BOOLEAN, STRING, TUNEINFO, HDSONGINFO}

    private final byte[] bytes;
    private final Type type;

    RadioCommand(byte[] inBytes, Type inType) {
        this.bytes = inBytes;
        this.type = inType;
    }



    public byte[] getBytes() {
        return bytes;
    }

    public Type getType() {
        return type;
    }

    public int getByteValueAsInt() {
        ByteBuffer buf = ByteBuffer.wrap(this.bytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getShort();
    }

    /**
     * Compares an incoming byte value to the integer representation of all the values
     * in the RadioCommand enum.  If found, the RadioCommand is returned.
     *
     * @param byteValue     The integer representation of the value to find
     * @return              The matching command if found, null if not found
     */
    public static RadioCommand getCommandFromValue(int byteValue) {

        for (RadioCommand cmd : COMMAND_ARRAY) {
            if (cmd.getByteValueAsInt() == byteValue) {
                return cmd;
            }
        }

        Log.i(TAG, "No matching Command found for value: " + byteValue);
        return null;
    }
}
