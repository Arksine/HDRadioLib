package com.arksine.hdradiolib;

import android.os.Handler;
import android.os.Message;

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
        this(null, null);
    }

    public RadioDriver(RadioDataHandler dataHandler, DriverEvents events) {
        this.mDataHandler = dataHandler;
        this.mDriverEvents = events;
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

    public abstract ArrayList<Object> getDeviceList();
    public abstract Object getIdentifier();
    public abstract boolean isOpen();
    public abstract void open();
    public abstract void openById(Object identifier);
    public abstract void close();
    public abstract void raiseRts();
    public abstract void clearRts();
    public abstract void raiseDtr();
    public abstract void clearDtr();
    public abstract void writeData(byte[] data);


}
