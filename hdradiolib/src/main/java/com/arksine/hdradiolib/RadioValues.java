package com.arksine.hdradiolib;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.SparseArray;

import com.arksine.hdradiolib.enums.RadioBand;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by eric on 1/29/17.
 */

public class RadioValues {
    private static final String TAG = RadioValues.class.getSimpleName();
    private static final boolean DEBUG = true;

    // These don't need to be atomic since get/set only happen via the data handler
    private SparseArray<String> mHdTitles = new SparseArray<>(5);
    private SparseArray<String> mHdArtists = new SparseArray<>(5);

    // The variables below are atomic, as they are set by the data handler, but can be retreived
    // from other threads
    final AtomicBoolean mPower;
    final AtomicBoolean mMute;
    final AtomicInteger mSignalStrength;
    final AtomicReference<TuneInfo> mTune;
    // Dont need to store tuneinfo received from SEEK command
    final AtomicBoolean mHdActive;
    final AtomicBoolean mHdStreamLock;
    final AtomicInteger mHdSignalStrength;
    final AtomicInteger mHdSubchannel;
    final AtomicInteger mHdSubchannelCount;
    final AtomicBoolean mHdEnableHdTuner;    // TODO: this variable's description makes little sense, and it seems to reuturn an integer value instead of boolean
    final AtomicReference<String> mHdTitle;
    final AtomicReference<String> mHdArtist;
    final AtomicReference<String> mHdCallsign;
    final AtomicReference<String> mHdStationName;
    final AtomicReference<String> mUniqueId;
    final AtomicReference<String> mApiVersion;
    final AtomicReference<String> mHwVersion;
    final AtomicBoolean mRdsEnabled;
    final AtomicReference<String> mRdsGenre;
    final AtomicReference<String> mRdsProgramService;
    final AtomicReference<String> mRdsRadioText;
    final AtomicInteger mVolume;
    final AtomicInteger mBass;
    final AtomicInteger mTreble;
    final AtomicInteger mCompression;  // TODO:  Don't know what this is, you can't seem to be able to change it.

    public RadioValues() {

        // TODO: should I persist and retreive values in this class?

        mPower = new AtomicBoolean(false);
        mMute = new AtomicBoolean(false);
        mSignalStrength = new AtomicInteger(0);
        mTune = new AtomicReference<>(new TuneInfo(RadioBand.FM, 879, 0));
        mHdActive = new AtomicBoolean(false);
        mHdStreamLock = new AtomicBoolean(false);
        mHdSignalStrength = new AtomicInteger(0);
        mHdSubchannel = new AtomicInteger(0);
        mHdSubchannelCount = new AtomicInteger(0);
        mHdEnableHdTuner = new AtomicBoolean(true);
        mHdTitle = new AtomicReference<>("");
        mHdArtist = new AtomicReference<>("");
        mHdCallsign = new AtomicReference<>("");
        mHdStationName = new AtomicReference<>("");
        mUniqueId = new AtomicReference<>("");
        mApiVersion = new AtomicReference<>("");
        mHwVersion = new AtomicReference<>("");
        mRdsEnabled = new AtomicBoolean(false);
        mRdsGenre = new AtomicReference<>("");
        mRdsProgramService = new AtomicReference<>("");
        mRdsRadioText = new AtomicReference<>("");
        mVolume = new AtomicInteger(0);
        mBass = new AtomicInteger(0);
        mTreble = new AtomicInteger(0);
        mCompression = new AtomicInteger(0);

    }

    void setTune(TuneInfo tune) {
        // When tune is set, the variables below must be cleared
        mHdSubchannel.set(0);
        mHdSubchannelCount.set(0);
        mHdActive.set(false);
        mHdStreamLock.set(false);
        mRdsEnabled.set(false);
        mRdsGenre.set("");
        mRdsProgramService.set("");
        mRdsRadioText.set("");
        mHdCallsign.set("");
        mHdStationName.set("");
        mHdTitle.set("");
        mHdArtist.set("");
        mHdArtists.clear();
        mHdTitles.clear();

        mTune.set(tune);
    }

    void setHdSubchannel(int subchannel) {
        // when the subchannel is changed, the HD Title and Artist must be changed as well
        String title = mHdTitles.get(subchannel);
        String artist = mHdArtists.get(subchannel);

        // Make sure they exist in the SparseArray
        if (title == null) {
            title = "";
        }
        if (artist == null) {
            artist = "";
        }

        // update the subchannel in tuneinfo
        TuneInfo info = mTune.get();
        if (info != null) {
            info.setSubChannel(subchannel);
            mTune.set(info);
        }

        mHdTitle.set(title);
        mHdArtist.set(artist);
        mHdSubchannel.set(subchannel);
    }

    void setHdTitle(HDSongInfo hdTitle) {
        mHdTitles.put(hdTitle.getSubchannel(), hdTitle.getInfo());
        mHdTitle.set(hdTitle.getInfo());
    }

    void setHdArtist(HDSongInfo hdArtist) {
        mHdArtists.put(hdArtist.getSubchannel(), hdArtist.getInfo());
        mHdArtist.set(hdArtist.getInfo());
    }

    /*public void savePersistentPrefs(SharedPreferences preferences) {
        // Persist changeable values
        TuneInfo tuneInfo = mTune.get();
        boolean hdActive = mHdActive.get();

        int subchannel = (hdActive) ? mHdSubchannel.get() : 0;

        // TODO: Should I persist these?
        int volume =  mVolume.get();
        int bass = mBass.get();
        int treble = mTreble.get();

        preferences.edit()
                .putInt("radio_pref_key_frequency", tuneInfo.getFrequency())
                .putString("radio_pref_key_band", tuneInfo.getBand().toString())
                .putInt("radio_pref_key_subchannel", subchannel)
                .putBoolean("radio_pref_key_seekall", mSeekAll)
                .apply();
    }*/

}
