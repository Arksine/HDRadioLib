package com.arksine.hdradiolib;

import android.os.Message;
import android.os.Process;
import android.support.annotation.NonNull;
import android.util.Log;

import com.arksine.hdradiolib.enums.RadioError;
import com.ftdi.j2xx.FT_Device;

/**
 * Handles serial connection to the MJS Cable / HD Radio
 */

class RadioConnection {
    private static final String TAG = RadioConnection.class.getSimpleName();

    private final Object READ_LOCK = new Object();

    private FT_Device mFtDevice;
    private RadioDataHandler mDataHandler;
    private HDRadioCallbacks mCallbacks;
    private ReadThread mReadThread;


    RadioConnection(FT_Device dev, RadioDataHandler dataHandler, HDRadioCallbacks cbs) {
        this.mFtDevice = dev;
        this.mDataHandler = dataHandler;
        this.mCallbacks = cbs;

        this.mReadThread = new ReadThread();
        this.mReadThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        this.mReadThread.start();
    }

    void setCallbacks(@NonNull HDRadioCallbacks callbacks) {
        this.mCallbacks = callbacks;
    }

    boolean isOpen() {
        return mFtDevice != null && mFtDevice.isOpen();
    }

    void writeData(@NonNull byte[] data) {
        if (mFtDevice == null) {
            Log.e(TAG, "Device not created, cannot write");
            return;
        }

        if (mFtDevice.write(data) < 0) {
            // device write error
            if (this.mCallbacks != null) {
                this.mCallbacks.onDeviceError(RadioError.DATA_WRITE_ERROR);
            }
        }
    }

    /**
     * Closes the connection.  Note that the call to ReadThread.stopReading is blocking, as it  will
     * wait for the reading thread to exit.  This should prevent a potential error by
     * closing the device in the middle of a read.
     */
    void close() {
        this.mReadThread.stopReading();


        if (this.isOpen()) {
            this.mFtDevice.clrDtr();   // power off before closing
            this.mFtDevice.close();
        }

        this.mFtDevice = null;
    }

    void raiseRTS() {
        if (this.mFtDevice != null) {
            if (!this.mFtDevice.setRts()) {
                // Device Error
                if (this.mCallbacks != null) {
                    this.mCallbacks.onDeviceError(RadioError.RTS_SET_ERROR);
                }
            }
        }
    }

    void clearRTS() {
        if (this.mFtDevice != null) {
            if(!this.mFtDevice.clrRts()) {
                // Device Error
                if (this.mCallbacks != null) {
                    this.mCallbacks.onDeviceError(RadioError.RTS_CLEAR_ERROR);
                }
            }
        }
    }


    void raiseDTR() {
        if (this.mFtDevice != null) {
            if(!this.mFtDevice.setDtr()){
                // Device Error
                if (this.mCallbacks != null) {
                    this.mCallbacks.onDeviceError(RadioError.DTR_SET_ERROR);
                }
            }
        }
    }

    void clearDTR() {
        if (this.mFtDevice != null) {
            if(!this.mFtDevice.clrDtr()) {
                // Device Error
                if (this.mCallbacks != null) {
                    this.mCallbacks.onDeviceError(RadioError.DTR_CLEAR_ERROR);
                }
            }
        }
    }


    private class ReadThread extends Thread {
        private static final int MAX_READ_SIZE = 256;
        private volatile boolean mIsReading;
        private volatile boolean mIsWaiting = false;

        @Override
        public void run() {
            this.mIsReading = true;

            while (this.mIsReading  &&
                    RadioConnection.this.mFtDevice != null) {

                // Sleep 50ms between reads
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                synchronized (READ_LOCK) {

                    // check the buffer for available
                    int available = RadioConnection.this.mFtDevice.getQueueStatus();

                    if (available > 0) {

                        if (available > MAX_READ_SIZE) {
                            available = 256;
                        }

                        byte readBuffer[] = new byte[available];

                        if (RadioConnection.this.mFtDevice.read(readBuffer) < 0) {
                            // Device read error
                            break;
                        }

                        Message msg = RadioConnection.this.mDataHandler.obtainMessage();
                        msg.obj = readBuffer;
                        RadioConnection.this.mDataHandler.sendMessage(msg);
                    } else if (available < 0) {
                        // Device Read Error
                        break;
                    }
                }
            }

            if (this.mIsReading) {
                // Device Error, the loop was broken before reading was stopped by user
                if (RadioConnection.this.mCallbacks != null) {
                    RadioConnection.this.mCallbacks.onDeviceError(RadioError.DATA_READ_ERROR);
                }
            }

            synchronized (this) {
                if (this.mIsWaiting) {
                    this.mIsWaiting = false;
                    notify();
                }
            }
        }

        synchronized void stopReading() {
            this.mIsWaiting = true;
            this.mIsReading = false;
            try {
                wait(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


    }

}
