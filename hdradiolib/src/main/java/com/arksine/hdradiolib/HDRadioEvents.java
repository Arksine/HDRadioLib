package com.arksine.hdradiolib;

import com.arksine.hdradiolib.enums.RadioError;

/**
 * Interface events / callbacks that must be implemented by the Application/Service using this library.
 */

public interface HDRadioEvents {

    // TODO: I don't have an event for software power off because it isn't consistent.  When powering
    // on it gives the correct item, however when powering off it doesn't deliver a message until AFTER
    // it powers on again.
    //  I could attempt to power off without lowering DTR to see if I get a reply.

    /**
     * Callback executed after the HDRadio.open function is complete.
     *
     * @param openSuccess   true if open was successful, false if unsuccessful
     * @param controller    reference to the interface necessary to control the HD Radio
     */
    void onOpened(boolean openSuccess, RadioController controller);
    void onClosed();
    void onDeviceError(RadioError error);
    void onRadioPowerOn();
    void onRadioPowerOff();
    void onRadioMute(final boolean muteStatus);
    void onRadioSignalStrength(final int signalStrength);
    void onRadioTune(final TuneInfo tuneInfo);
    void onRadioSeek(final TuneInfo seekInfo);
    void onRadioHdActive(final boolean hdActive);
    void onRadioHdStreamLock(final boolean hdStreamLock);
    void onRadioHdSignalStrength(final int hdSignalStrength);
    void onRadioHdSubchannel(final int subchannel);
    void onRadioHdSubchannelCount(final int subchannelCount);
    // void onRadioHdEnableTunerEnabled(final boolean status);  // TODO: this isn't a boolean, need to reverse radio to see what it really is
    void onRadioHdTitle(final HDSongInfo hdTitle);
    void onRadioHdArtist(final HDSongInfo hdArtist);
    void onRadioHdCallsign(final String hdCallsign);
    void onRadioHdStationName(final String hdStationName);
    void onRadioRdsEnabled(final boolean rdsEnabled);
    void onRadioRdsGenre(final String rdsGenre);
    void onRadioRdsProgramService(final String rdsProgramService);
    void onRadioRdsRadioText(final String rdsRadioText);
    void onRadioVolume(final int volume);
    void onRadioBass(final int bass);
    void onRadioTreble(final int treble);
    void onRadioCompression(final int compression);  // TODO: not sure this is an integer value
}
