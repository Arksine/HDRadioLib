package com.arksine.hdradiolib;

import com.arksine.hdradiolib.enums.PowerStatus;
import com.arksine.hdradiolib.enums.RadioCommand;

/**
 * Interface for Application to control the HD Radio
 */

public interface RadioController {
    void setSeekAll(final boolean seekAll);


    void powerOn();
    void powerOff();

    void muteOn();
    void muteOff();

    void setVolume(final int volume);
    void setVolumeUp();
    void setVolumeDown();

    void setBass(final int bass);
    void setBassUp();
    void setBassDown();

    void setTreble(final int treble);
    void setTrebleUp();
    void setTrebleDown();


    void tune(final TuneInfo tuneInfo);
    void tuneUp();
    void tuneDown();

    void setHdSubChannel(final int subChannel);

    void seekUp();
    void seekDown();

    void requestUpdate(final RadioCommand command);

    // Value getters
    boolean getSeekAll();
    boolean isPoweredOn();
    PowerStatus getPowerStatus();
    boolean getMute();
    int getSignalStrength();
    TuneInfo getTune();
    boolean getHdActive();
    boolean getHdStreamLock();
    int getHdSignalStrength();
    int getHdSubchannel();
    int getHdSubchannelCount();
    // boolean getHdEnableHdTuner()  //TODO: not implementing yet because not sure what this is
    String getHdTitle();
    String getHdArtist();
    String getHdCallsign();
    String getHdStationName();
    String getUniqueId();
    String getApiVersion();
    String getHardwareVersion();
    boolean getRdsEnabled();
    String getRdsGenre();
    String getRdsProgramService();
    String getRdsRadioText();
    int getVolume();
    int getBass();
    int getTreble();
    int getCompression();

}
