package com.arksine.hdradiolib.basicexample;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;

import com.arksine.hdradiolib.HDSongInfo;
import com.arksine.hdradiolib.TuneInfo;
import com.arksine.hdradiolib.enums.RadioBand;
import com.arksine.hdradiolib.enums.RadioCommand;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class containing values returned by the HD Radio, with methods to get and set them.  Calls
 * are synchronized to prevent reading/writing to the same value at the same time.
 */

public class HDRadioValues {
    private static final String TAG = HDRadioValues.class.getSimpleName();
    private static final boolean DEBUG = true;

    private volatile boolean mSeekAll;

    private SparseArray<AtomicReference<String>> mHdTitles = new SparseArray<>(5);
    private SparseArray<AtomicReference<String>> mHdArtists = new SparseArray<>(5);

    private AtomicBoolean mPower;
    private AtomicBoolean mMute;
    private AtomicInteger mSignalStrength;
    private AtomicReference<TuneInfo> mTune;
    // Dont need to store tuneinfo received from SEEK command
    private AtomicBoolean mHdActive;
    private AtomicBoolean mHdStreamLock;
    private AtomicInteger mHdSignalStrength;
    private AtomicInteger mHdSubchannel;
    private AtomicInteger mHdSubchannelCount;
    private AtomicBoolean mHdEnableHdTuner;    // TODO: this variable's description makes little sense, and it seems to reuturn an integer value instead of boolean
    private AtomicReference<String> mHdTitle;
    private AtomicReference<String> mHdArtist;
    private AtomicReference<String> mHdCallsign;
    private AtomicReference<String> mHdStationName;
    private AtomicReference<String> mUniqueId;
    private AtomicReference<String> mApiVersion;
    private AtomicReference<String> mHwVersion;
    private AtomicBoolean mRdsEnabled;
    private AtomicReference<String> mRdsGenre;
    private AtomicReference<String> mRdsProgramService;
    private AtomicReference<String> mRdsRadioText;
    private AtomicInteger mVolume;
    private AtomicInteger mBass;
    private AtomicInteger mTreble;
    private AtomicInteger mCompression;  // TODO:  Don't know what this is, you can't seem to be able to change it.

    // TODO:  Move this class to the hd radio libary.  We'll store values there, and provide functions to retreive
    // each one.  It would be better to abstract enums from the user if possible.

    public HDRadioValues(Context context) {

        // Restore persisted values
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        int frequency = globalPrefs.getInt("radio_pref_key_frequency", 879);
        RadioBand band = RadioBand.valueOf(globalPrefs.getString("radio_pref_key_band", "FM"));
        int subchannel = globalPrefs.getInt("radio_pref_key_subchannel", 0);
        TuneInfo info = new TuneInfo(band, frequency, subchannel);

        mSeekAll = globalPrefs.getBoolean("radio_pref_key_seekall", true);


        mPower = new AtomicBoolean(false);
        mMute = new AtomicBoolean(false);
        mSignalStrength = new AtomicInteger(0);
        mTune = new AtomicReference<>(info);
        mHdActive = new AtomicBoolean(false);
        mHdStreamLock = new AtomicBoolean(false);
        mHdSignalStrength = new AtomicInteger(0);
        mHdSubchannel = new AtomicInteger(subchannel);
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

    public Object getHdValue(RadioCommand key) {

        switch (key) {
            case POWER:
                return mPower.get();
            case MUTE:
                return mMute.get();
            case SIGNAL_STRENGTH:
                return mSignalStrength.get();
            case TUNE:
                return mTune.get();
            case HD_ACTIVE:
                return mHdActive.get();
            case HD_STREAM_LOCK:
                return mHdStreamLock.get();
            case HD_SIGNAL_STRENGTH:
                return mHdSignalStrength.get();
            case HD_SUBCHANNEL:
                return mHdSubchannel.get();
            case HD_SUBCHANNEL_COUNT:
                return mHdSubchannelCount.get();
            case HD_ENABLE_HD_TUNER:
                return mHdEnableHdTuner.get();
            case HD_TITLE:
                return mHdTitle.get();
            case HD_ARTIST:
                return mHdArtist.get();
            case HD_CALLSIGN:
                return mHdCallsign.get();
            case HD_STATION_NAME:
                return mHdStationName.get();
            case HD_UNIQUE_ID:
                return mUniqueId.get();
            case HD_API_VERSION:
                return mApiVersion.get();
            case HD_HW_VERSION:
                return mHwVersion.get();
            case RDS_ENABLED:
                return mRdsEnabled.get();
            case RDS_GENRE:
                return mRdsGenre.get();
            case RDS_PROGRAM_SERVICE:
                return mRdsProgramService.get();
            case RDS_RADIO_TEXT:
                return mRdsRadioText.get();
            case VOLUME:
                return mVolume.get();
            case BASS:
                return mBass.get();
            case TREBLE:
                return mTreble.get();
            case COMPRESSION:
                return mCompression.get();
            default:
                Log.i(TAG, "Invalid key");
                return null;
        }

    }

    public void setHdValue(RadioCommand key, Object value) {

        switch (key) {
            case POWER:
                mPower.set((boolean)value);
                break;
            case MUTE:
                mMute.set((boolean)value);
                break;
            case SIGNAL_STRENGTH:
                mSignalStrength.set((int)value);
                break;
            case TUNE:
                // reset values when we tune to a new channel
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

                mTune.set((TuneInfo)value);
                break;
            case SEEK:
                // Don't need to store seek value
                return;
            case HD_ACTIVE:
                mHdActive.set((boolean)value);
                break;
            case HD_STREAM_LOCK:
                mHdStreamLock.set((boolean)value);
                break;
            case HD_SIGNAL_STRENGTH:
                mHdSignalStrength.set((int)value);
                break;
            case HD_SUBCHANNEL: {
                int index = (int) value;
                AtomicReference<String> titleRef = mHdTitles.get(index);

                AtomicReference<String> artistRef = mHdArtists.get(index);

                // check for null values
                if (titleRef == null)
                    titleRef = new AtomicReference<>("");
                if (artistRef == null)
                    artistRef = new AtomicReference<>("");

                mHdTitle.set(titleRef.get());
                mHdArtist.set(artistRef.get());

                // update the subchannel in tuneinfo
                TuneInfo info = mTune.get();
                if (info != null) {
                    info.setSubChannel((int) value);
                    mTune.set(info);
                }

                mHdSubchannel.set((int) value);
                break;
            }
            case HD_SUBCHANNEL_COUNT:
                mHdSubchannelCount.set((int)value);
                break;
            case HD_ENABLE_HD_TUNER:
                mHdEnableHdTuner.set((boolean)value);
                break;
            case HD_TITLE: {
                HDSongInfo val = (HDSongInfo) value;
                AtomicReference<String> titleRef = mHdTitles.get(val.getSubchannel());
                if (titleRef != null)
                    titleRef.set(val.getInfo());
                else {
                    titleRef = new AtomicReference<>(val.getInfo());
                }
                mHdTitles.put(val.getSubchannel(), titleRef);
                value = val.getInfo();

                mHdTitle.set(val.getInfo());
                break;
            }
            case HD_ARTIST: {
                HDSongInfo val = (HDSongInfo) value;
                AtomicReference<String> artistRef = mHdArtists.get(val.getSubchannel());
                if (artistRef != null)
                    artistRef.set(val.getInfo());
                else {
                    artistRef = new AtomicReference<>(val.getInfo());
                }
                mHdArtists.put(val.getSubchannel(), artistRef);
                value = val.getInfo();

                mHdArtist.set(val.getInfo());
                break;
            }
            case HD_CALLSIGN:
                mHdCallsign.set((String)value);
                break;
            case HD_STATION_NAME:
                mHdStationName.set((String)value);
                break;
            case HD_UNIQUE_ID:
                mUniqueId.set((String)value);
                break;
            case HD_API_VERSION:
                mApiVersion.set((String)value);
                break;
            case HD_HW_VERSION:
                mHwVersion.set((String)value);
                break;
            case RDS_ENABLED:
                mRdsEnabled.set((boolean)value);
                break;
            case RDS_GENRE:
                mRdsGenre.set((String)value);
                break;
            case RDS_PROGRAM_SERVICE:
                mRdsProgramService.set((String)value);
                break;
            case RDS_RADIO_TEXT:
                mRdsRadioText.set((String)value);
                break;
            case VOLUME:
                mVolume.set((int)value);
                break;
            case BASS:
                mBass.set((int)value);
                break;
            case TREBLE:
                mTreble.set((int)value);
                break;
            case COMPRESSION:
                mCompression.set((int)value);
                break;
            default:
        }

        if (DEBUG) {
            if (value instanceof TuneInfo) {
                Log.v(TAG, "Stored " + key.toString() + ": "
                        + ((TuneInfo) value).getFrequency() + " "
                        + ((TuneInfo) value).getBand().toString());
            } else {
                Log.v(TAG, "Stored " + key.toString() + ": " + value);
            }
        }

    }

    public boolean getSeekAll() {
        return mSeekAll;
    }

    public void setSeekAll(boolean seekAll) {
        this.mSeekAll = seekAll;
    }

    public void savePersistentPrefs(Context context) {
        // Persist changeable values
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        TuneInfo tuneInfo = mTune.get();
        boolean hdActive = mHdActive.get();

        int subchannel = (hdActive) ? mHdSubchannel.get() : 0;

        // TODO: Should I persist these?
        int volume =  mVolume.get();
        int bass = mBass.get();
        int treble = mTreble.get();

        globalPrefs.edit()
                .putInt("radio_pref_key_frequency", tuneInfo.getFrequency())
                .putString("radio_pref_key_band", tuneInfo.getBand().toString())
                .putInt("radio_pref_key_subchannel", subchannel)
                .putBoolean("radio_pref_key_seekall", mSeekAll)
                .apply();
    }
}
