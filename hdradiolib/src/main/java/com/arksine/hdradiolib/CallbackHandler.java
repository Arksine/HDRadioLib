package com.arksine.hdradiolib;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioError;

/**
 * Created by Eric on 1/14/2017.
 */

class CallbackHandler extends Handler {
    private static final String TAG = CallbackHandler.class.getSimpleName();

    // Callback message codes (value of the "what" argument of a message sent/received from the cb handler
    public static final int CALLBACK_ON_OPENED = 0;
    public static final int CALLBACK_ON_CLOSED = 1;
    public static final int CALLBACK_DEVICE_ERROR = 2;
    public static final int CALLBACK_POWER_ON = 3;
    public static final int CALLBACK_POWER_OFF = 4;
    public static final int CALLBACK_DATA_RECEIVED = 5;

    private HDRadioCallbacks mCallbacks;

    CallbackHandler(@NonNull HDRadioCallbacks callbacks, Looper looper) {
        super(looper);
        this.mCallbacks = callbacks;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case CALLBACK_ON_OPENED:
                boolean success = (msg.arg1 == 1);
                this.mCallbacks.onOpened(success, (RadioController) msg.obj);
                break;
            case CALLBACK_ON_CLOSED:
                this.mCallbacks.onClosed();
                break;
            case CALLBACK_DEVICE_ERROR:
                RadioError error = RadioError.getErrorFromOrdinal(msg.arg1);
                if (error != null) {
                    this.mCallbacks.onDeviceError(error);
                } else {
                    Log.i(TAG, "Invalid RadioError received through message");
                }
                break;
            case CALLBACK_POWER_ON:
                this.mCallbacks.onRadioPowerOn();
                break;
            case CALLBACK_POWER_OFF:
                this.mCallbacks.onRadioPowerOff();
                break;
            case CALLBACK_DATA_RECEIVED:
                RadioCommand cmd = RadioCommand.getCommandFromOrdinal(msg.arg1);
                if (cmd != null) {
                    this.mCallbacks.onRadioDataReceived(cmd, msg.obj);
                } else {
                    Log.i(TAG, "Invalid RadioCommand received through message");
                }
                break;
            default:
                Log.v(TAG, "Unknown message code for 'what'");
        }
    }

}
