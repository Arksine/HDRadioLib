package com.arksine.hdradiolib;

import com.arksine.hdradiolib.enums.RadioCommand;

/**
 * Interface for Application to control the HD Radio
 */

public interface RadioController {
    void setSeekAll(final boolean seekAll);
    boolean getSeekAll();

    void powerOn();
    void powerOff();
    boolean getPowerStatus();

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
}
