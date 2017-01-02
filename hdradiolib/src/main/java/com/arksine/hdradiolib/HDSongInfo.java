package com.arksine.hdradiolib;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Container class for song info received from the HD Radio.
 */

public class HDSongInfo implements Parcelable {

    private final String info;      // song information (either Artist or Title)
    private final int subchannel;   // HD Subchannel the information sent for

    public static final Parcelable.Creator<HDSongInfo> CREATOR =
            new Parcelable.Creator<HDSongInfo>() {

                @Override
                public HDSongInfo createFromParcel(Parcel in) {
                    return new HDSongInfo(in);
                }

                @Override
                public HDSongInfo[] newArray(int size) {
                    return new HDSongInfo[size];
                }
            };

    public HDSongInfo(String songInfo, int subCh) {
        this.info =  songInfo;
        this.subchannel = subCh;
    }

    private HDSongInfo(Parcel in) {
        this.info = in.readString();
        this.subchannel = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(this.info);
        out.writeInt(this.subchannel);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public String getInfo() {
        return info;
    }

    public int getSubchannel() {
        return subchannel;
    }
}
