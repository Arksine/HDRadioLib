package com.arksine.hdradiolib;

import com.arksine.hdradiolib.enums.RadioError;

/**
 * Interface events / callbacks that must be implemented by the Application/Service using this library.
 */

public interface HDRadioEvents {

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
    void onRadioMute(boolean muteStatus);
    void onRadioSignalStrength(int signalStrength);
    void onRadioTune(TuneInfo tuneInfo);
    void onRadioSeek(TuneInfo seekInfo);
    void onRadioHdActive(boolean hdActive);
    void onRadioHdStreamLock(boolean hdStreamLock);
    void onRadioHdSignalStrength(int hdSignalStrength);
    void onRadioHdSubchannel(int subchannel);
    void onRadioHdSubchannelCount(int subchannelCount);
    // void onRadioHdEnableTunerEnabled(boolean status);  // TODO: this isn't a boolean, need to reverse radio to see what it really is
    void onRadioHdTitle(HDSongInfo hdTitle);
    void onRadioHdArtist(HDSongInfo hdArtist);
    void onRadioHdCallsign(String hdCallsign);
    void onRadioHdStationName(String hdStationName);
    void onRadioRdsEnabled(boolean rdsEnabled);
    void onRadioRdsGenre(String rdsGenre);
    void onRadioRdsProgramService(String rdsProgramService);
    void onRadioRdsRadioText(String rdsRadioText);
    void onRadioVolume(int volume);
    void onRadioBass(int bass);
    void onRadioTreble(int treble);
    void onRadioCompression(int compression);  // TODO: not sure this is an integer value
}
