package com.arksine.hdradiolib.drivers;

import android.os.Handler;
import android.os.Message;

import com.arksine.hdradiolib.RadioDataHandler;
import com.arksine.hdradiolib.enums.RadioError;

import java.util.ArrayList;

/**
 * Created by eric on 1/27/17.
 */

public abstract class RadioDriver {

    public interface DriverEvents {
        void onOpened(boolean success);
        void onError(RadioError error);
        void onClosed();
    }


    protected RadioDataHandler mDataHandler;
    protected DriverEvents mDriverEvents;

    public RadioDriver() {
        this.mDataHandler = null;
        this.mDriverEvents = null;
    }

    public void initialize (RadioDataHandler dataHandler, DriverEvents events) {
        this.mDataHandler = dataHandler;
        this.mDriverEvents = events;
    }

    public boolean isInitialized () {
        return (this.mDataHandler != null && this.mDriverEvents != null);
    }

    protected void handleIncomingBytes(byte[] data) {
        Message msg = this.mDataHandler.obtainMessage();
        msg.obj = data;
        this.mDataHandler.sendMessage(msg);
    }


    public abstract <T> ArrayList<T> getDeviceList(Class<T> listType);
    public abstract String getIdentifier();
    public abstract boolean isOpen();
    public abstract void open();
    public abstract void openById(String identifier);
    public abstract void close();
    public abstract void raiseRts();
    public abstract void clearRts();
    public abstract void raiseDtr();
    public abstract void clearDtr();
    public abstract void writeData(byte[] data);


}
