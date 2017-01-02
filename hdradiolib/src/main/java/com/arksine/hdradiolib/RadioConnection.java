package com.arksine.hdradiolib;

import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.annotation.NonNull;
import android.util.Log;

import com.ftdi.j2xx.FT_Device;

/**
 * Handles serial connection to the MJS Cable / HD Radio
 */

class RadioConnection {
    private static final String TAG = RadioConnection.class.getSimpleName();

    private final Object READ_LOCK = new Object();

    private FT_Device mFtDevice;
    private RadioDataHandler mDataHandler;
    private ReadThread mReadThread;


    RadioConnection(FT_Device dev, RadioDataHandler dataHandler) {
        this.mFtDevice = dev;
        this.mDataHandler = dataHandler;

        this.mReadThread = new ReadThread();
        this.mReadThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        this.mReadThread.start();
    }

    boolean isOpen() {
        return mFtDevice != null && mFtDevice.isOpen();
    }

    void writeData(@NonNull byte[] data) {
        if (mFtDevice == null || !mFtDevice.isOpen()) {
            Log.e(TAG, "Device not open, cannot write");
        }

        mFtDevice.write(data);
    }

    void close() {
        mReadThread.stopReading();

        // TODO: should I delay or wait for the readthread to stop?

        if (this.isOpen()) {
            mFtDevice.clrDtr();   // power off before closing
            mFtDevice.close();
        }

        mFtDevice = null;
    }

    void raiseRTS() {
        if (mFtDevice != null) {
            mFtDevice.setRts();
        }
    }

    void clearRTS() {
        if (mFtDevice != null) {
            mFtDevice.clrRts();
        }
    }


    void raiseDTR() {
        if (mFtDevice != null) {
            mFtDevice.setDtr();
        }
    }

    void clearDTR() {
        if (mFtDevice != null) {
            mFtDevice.clrDtr();
        }
    }


    private class ReadThread extends Thread {
        private static final int MAX_READ_SIZE = 256;
        private volatile boolean mIsReading;

        @Override
        public void run() {
            this.mIsReading = true;

            while (this.mIsReading  &&
                    RadioConnection.this.mFtDevice != null &&
                    RadioConnection.this.mFtDevice.isOpen()) {

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

                        RadioConnection.this.mFtDevice.read(readBuffer);

                        Message msg = RadioConnection.this.mDataHandler.obtainMessage();
                        msg.obj = readBuffer;
                        RadioConnection.this.mDataHandler.sendMessage(msg);
                    }
                }
            }
        }

        void stopReading() {
            this.mIsReading = false;
        }
    }

}
