package com.arksine.hdradiolib;

import android.os.Parcel;
import android.os.Parcelable;

import com.arksine.hdradiolib.enums.RadioBand;

/**
 * Class container for tune information.  Implements Parcelable so it can be
 * sent via Android intents and AIDL
 */

public class TuneInfo implements Parcelable {

    private final RadioBand mBand;
    private final int mFrequency;
    private final int mSubChannel;

    public static final Parcelable.Creator<TuneInfo> CREATOR =
            new Parcelable.Creator<TuneInfo>() {
                @Override
                public TuneInfo createFromParcel(Parcel in) {
                    return new TuneInfo(in);
                }

                @Override
                public TuneInfo[] newArray(int size) {
                    return new TuneInfo[size];
                }
            };

    TuneInfo(RadioBand band, int freq, int subchannel ) {
        this.mBand = band;
        this.mFrequency = freq;
        this.mSubChannel = subchannel;
    }

    TuneInfo(Parcel in) {
        this.mBand = RadioBand.valueOf(in.readString());
        this.mFrequency = in.readInt();
        this.mSubChannel = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(this.mBand.toString());
        out.writeInt(this.mFrequency);
        out.writeInt(this.getSubChannel());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public int getSubChannel() {
        return mSubChannel;
    }

    public int getFrequency() {
        return mFrequency;
    }

    public RadioBand getBand() {
        return mBand;
    }
}
