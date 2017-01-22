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

/**
 * Class containing values returned by the HD Radio, with methods to get and set them.  Calls
 * are synchronized to prevent reading/writing to the same value at the same time.
 */

public class HDRadioValues {
    private static final String TAG = HDRadioValues.class.getSimpleName();
    private static final boolean DEBUG = true;

    private final Object WRITE_LOCK = new Object();

    private volatile boolean mSeekAll;

    private SparseArray<String> mHdTitles = new SparseArray<>(5);
    private SparseArray<String> mHdArtists = new SparseArray<>(5);
    private final HashMap<RadioCommand, Object> mHdValues = new HashMap<>(26);

    public HDRadioValues(Context context) {

        // Restore persisted values
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        int frequency = globalPrefs.getInt("radio_pref_key_frequency", 879);
        RadioBand band = RadioBand.valueOf(globalPrefs.getString("radio_pref_key_band", "FM"));
        int subchannel = globalPrefs.getInt("radio_pref_key_subchannel", 0);
        TuneInfo info = new TuneInfo(band, frequency, subchannel);

        mSeekAll = globalPrefs.getBoolean("radio_pref_key_seekall", true);

        // TODO: Don't really need to store seek
        mHdValues.put(RadioCommand.POWER, false);
        mHdValues.put(RadioCommand.MUTE, false);
        mHdValues.put(RadioCommand.SIGNAL_STRENGTH, 0);
        mHdValues.put(RadioCommand.TUNE, info);
        mHdValues.put(RadioCommand.SEEK, 0);
        mHdValues.put(RadioCommand.HD_ACTIVE, false);
        mHdValues.put(RadioCommand.HD_STREAM_LOCK, false);
        mHdValues.put(RadioCommand.HD_SIGNAL_STRENGTH, 0);
        mHdValues.put(RadioCommand.HD_SUBCHANNEL, subchannel);
        mHdValues.put(RadioCommand.HD_SUBCHANNEL_COUNT, 0);
        mHdValues.put(RadioCommand.HD_ENABLE_HD_TUNER, true);
        mHdValues.put(RadioCommand.HD_TITLE, "");
        mHdValues.put(RadioCommand.HD_ARTIST, "");
        mHdValues.put(RadioCommand.HD_CALLSIGN, "");
        mHdValues.put(RadioCommand.HD_STATION_NAME, "");
        mHdValues.put(RadioCommand.HD_UNIQUE_ID, "");
        mHdValues.put(RadioCommand.HD_API_VERSION, "");
        mHdValues.put(RadioCommand.HD_HW_VERSION, "");
        mHdValues.put(RadioCommand.RDS_ENABLED, false);
        mHdValues.put(RadioCommand.RDS_GENRE, "");
        mHdValues.put(RadioCommand.RDS_PROGRAM_SERVICE, "");
        mHdValues.put(RadioCommand.RDS_RADIO_TEXT, "");
        mHdValues.put(RadioCommand.VOLUME, 0);
        mHdValues.put(RadioCommand.BASS, 0);
        mHdValues.put(RadioCommand.TREBLE, 0);
        mHdValues.put(RadioCommand.COMPRESSION, 0);
    }

    public Object getHdValue(RadioCommand key) {
        synchronized (WRITE_LOCK) {
            return mHdValues.get(key);
        }
    }

    public void setHdValue(RadioCommand key, Object value) {
        synchronized (WRITE_LOCK) {
            switch (key) {
                case HD_SUBCHANNEL:
                    int index = (int) value;
                    String title = mHdTitles.get(index);
                    String artist = mHdArtists.get(index);

                    // check for null values
                    if (title == null)
                        title = "";
                    if (artist == null)
                        artist = "";

                    mHdValues.put(RadioCommand.HD_TITLE, title);
                    mHdValues.put(RadioCommand.HD_ARTIST, artist);

                    // update the subchannel in tuneinfo
                    TuneInfo info = (TuneInfo)mHdValues.get(RadioCommand.TUNE);
                    if (info != null) {
                        info.setSubChannel((int)value);
                    }
                    break;
                case TUNE:
                    // reset values when we tune to a new channel
                    mHdValues.put(RadioCommand.HD_SUBCHANNEL, 0);
                    mHdValues.put(RadioCommand.HD_SUBCHANNEL_COUNT, 0);
                    mHdValues.put(RadioCommand.HD_ACTIVE, false);
                    mHdValues.put(RadioCommand.HD_STREAM_LOCK, false);
                    mHdValues.put(RadioCommand.RDS_ENABLED, false);
                    mHdValues.put(RadioCommand.RDS_GENRE, "");
                    mHdValues.put(RadioCommand.RDS_PROGRAM_SERVICE, "");
                    mHdValues.put(RadioCommand.RDS_RADIO_TEXT, "");
                    mHdValues.put(RadioCommand.HD_CALLSIGN, "");
                    mHdValues.put(RadioCommand.HD_STATION_NAME, "");
                    mHdValues.put(RadioCommand.HD_TITLE, "");
                    mHdValues.put(RadioCommand.HD_ARTIST, "");
                    mHdArtists.clear();
                    mHdTitles.clear();
                    break;
                case HD_TITLE:
                    HDSongInfo val = (HDSongInfo) value;
                    mHdTitles.put(val.getSubchannel(), val.getInfo());
                    value = val.getInfo();
                    break;
                case HD_ARTIST:
                    HDSongInfo val2 = (HDSongInfo) value;
                    mHdArtists.put(val2.getSubchannel(), val2.getInfo());
                    value = val2.getInfo();
                    break;
                case SEEK:
                    // Don't need to store seek value
                    return;
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


            mHdValues.put(key, value);
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
        TuneInfo tuneInfo = (TuneInfo)mHdValues.get(RadioCommand.TUNE);
        boolean hdActive = (boolean)mHdValues.get(RadioCommand.HD_ACTIVE);

        int subchannel = (hdActive) ? (int)mHdValues.get(RadioCommand.HD_SUBCHANNEL) : 0;
        int volume = (int)mHdValues.get(RadioCommand.VOLUME);
        int bass = (int)mHdValues.get(RadioCommand.BASS);
        int treble = (int)mHdValues.get(RadioCommand.TREBLE);

        globalPrefs.edit()
                .putInt("radio_pref_key_frequency", tuneInfo.getFrequency())
                .putString("radio_pref_key_band", tuneInfo.getBand().toString())
                .putInt("radio_pref_key_subchannel", subchannel)
                .putBoolean("radio_pref_key_seekall", mSeekAll)
                .apply();
    }
}
