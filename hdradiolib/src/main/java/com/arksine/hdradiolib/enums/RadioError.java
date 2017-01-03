package com.arksine.hdradiolib.enums;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Enumeration sent to the onDeviceError callback, providing the type of error received.  This
 * class implements Parcelable so the type may be passed via AIDL.  See the note in the RadioCommand
 * enum for issues pertianing to sending via intent.
 */

public enum RadioError implements Parcelable {
    DATA_READ_ERROR,
    DATA_WRITE_ERROR,
    RTS_SET_ERROR,
    RTS_CLEAR_ERROR,
    DTR_SET_ERROR,
    DTR_CLEAR_ERROR,
    CONNECTION_ERROR;

    public static final RadioError[] ERROR_ARRAY = RadioError.values();
    public static final Creator<RadioError> CREATOR = new Creator<RadioError>() {
        @Override
        public RadioError createFromParcel(Parcel in) {
            return ERROR_ARRAY[in.readInt()];
        }

        @Override
        public RadioError[] newArray(int size) {
            return new RadioError[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(ordinal());
    }
}
