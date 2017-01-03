package com.arksine.hdradiolib;

import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioError;

/**
 * Interface callbacks that must be implemented by the Application/Service using this library.
 */

public interface HDRadioCallbacks {

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
    void onRadioDataReceived(RadioCommand key, Object value);
}
