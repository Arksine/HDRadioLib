package com.arksine.hdradiolib;

import com.arksine.hdradiolib.enums.RadioCommand;

/**
 * Created by Eric on 12/28/2016.
 */

public interface HDRadioCallbacks {
    void onOpened(boolean openSuccess, RadioController controller);
    void onClosed();
    void onError();

    void onRadioPowerOn();
    void onRadioPowerOff();
    void onRadioDataReceived(RadioCommand key, Object value);
}
