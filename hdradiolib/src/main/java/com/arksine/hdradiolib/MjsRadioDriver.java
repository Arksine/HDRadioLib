package com.arksine.hdradiolib;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Process;
import android.support.annotation.NonNull;
import android.util.Log;

import com.arksine.hdradiolib.enums.RadioError;
import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles serial connection to the MJS Cable / HD Radio
 */

class MjsRadioDriver extends RadioDriver {
    private static final String TAG = MjsRadioDriver.class.getSimpleName();

    private final Object OPEN_LOCK = new Object();
    private final Object READ_LOCK = new Object();

    private static final String ACTION_USB_PERMISSION = "com.arksine.hdradiolib.USB_PERMISSION";
    private static D2xxManager ftdiManager = null;

    private Context mContext;
    private volatile FT_Device mFtDevice = null;
    private ReadThread mReadThread;
    private String mSerialNumber;

    MjsRadioDriver(Context context) {
        this.mContext = context;
    }

    @Override
    public String getIdentifier() {
        return this.mSerialNumber;
    }

    @Override
    public boolean isOpen() {
        return mFtDevice != null && mFtDevice.isOpen();
    }

    /**
     * Opens default device, ie. the first MJS cable found on the Usb Bus
     */
    @Override
    public void open() {
        this.openById(null);
    }

    /**
     * Attempts to open a HD Radio device.  When the operation is finished the onOpened callback
     * is executed with the status of the open request.  If the request was successful, a reference
     * to the controller interface is also sent via the callback, otherwise the controller interface
     * is set to null.
     *
     * @param identifier   The Identifier for the MJS cable a String object containing the MJS
     *                     cable's unique serial number
     */
    @Override
    public void openById(String identifier) {
        ConnectionThread thread = new ConnectionThread(identifier);
        thread.start();
    }


    /**
     * Closes the connection.  Note that the call to ReadThread.stopReading is blocking, as it  will
     * wait for the reading thread to exit.  This should prevent a potential error by
     * closing the device in the middle of a read.
     */
    @Override
    public void close() {
        synchronized (OPEN_LOCK) {
            this.mReadThread.stopReading();


            if (this.isOpen()) {
                this.mFtDevice.clrDtr();   // clear DTR to power off before closing
                this.mFtDevice.close();
            }

            this.mFtDevice = null;
        }

        this.mDriverEvents.onClosed();
    }

    @Override
    public void writeData(@NonNull byte[] data) {
        if (this.mFtDevice == null) {
            Log.e(TAG, "Device not created, cannot write");
            return;
        }

        if (this.mFtDevice.write(data) < 0) {
            // device write error
            this.close();
            this.mDriverEvents.onError(RadioError.DATA_WRITE_ERROR);
        }
    }



    @Override
    public void raiseRts() {
        if (this.mFtDevice != null) {
            if (!this.mFtDevice.setRts()) {
                // Device Error
                this.close();
                this.mDriverEvents.onError(RadioError.RTS_RAISE_ERROR);
            }
        }
    }

    @Override
    public void clearRts() {
        if (this.mFtDevice != null) {
            if(!this.mFtDevice.clrRts()) {
                // Device Error
                this.close();
                this.mDriverEvents.onError(RadioError.RTS_CLEAR_ERROR);
            }
        }
    }


    @Override
    public void raiseDtr() {
        if (this.mFtDevice != null) {
            if(!this.mFtDevice.setDtr()){
                // Device Error
                this.close();
                this.mDriverEvents.onError(RadioError.DTR_RAISE_ERROR);
            }
        }
    }

    @Override
    public void clearDtr() {
        if (this.mFtDevice != null) {
            if(!this.mFtDevice.clrDtr()) {
                // Device Error
                this.close();
                this.mDriverEvents.onError(RadioError.DTR_CLEAR_ERROR);
            }
        }
    }

    /**
     * Searches connected USB Devices for the MJS Gadgets HD Radio Cable
     *
     * @return true if successful, false if not successful
     */
    @Override
    public <T> ArrayList<T> getDeviceList(Class<T> listType) {
        UsbManager manager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> usbDeviceList = manager.getDeviceList();
        ArrayList<T> hdDeviceList = new ArrayList<>(5);

        for (UsbDevice uDevice : usbDeviceList.values()) {
            if ((uDevice.getVendorId() == 1027) && (uDevice.getProductId() == 37752)) {
                Log.v(TAG, "MJS Gadgets HD Radio Cable found");
                hdDeviceList.add(listType.cast(uDevice));
            }
        }
        if (hdDeviceList.isEmpty()) {
            Log.v(TAG, "MJS Gadgets HD Radio Cable not found");
        }
        return hdDeviceList;

    }

    private class ReadThread extends Thread {
        private static final int MAX_READ_SIZE = 256;
        private AtomicBoolean mIsReading = new AtomicBoolean(false);
        private AtomicBoolean mIsWaiting = new AtomicBoolean(false);

        @Override
        public void run() {
            this.mIsReading.set(true);

            while (this.mIsReading.get()  &&
                    MjsRadioDriver.this.mFtDevice != null) {

                // Sleep 50ms between reads
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                synchronized (READ_LOCK) {

                    // check the buffer for available
                    int available = MjsRadioDriver.this.mFtDevice.getQueueStatus();

                    if (available > 0) {

                        if (available > MAX_READ_SIZE) {
                            available = 256;
                        }

                        byte readBuffer[] = new byte[available];

                        if (MjsRadioDriver.this.mFtDevice.read(readBuffer) < 0) {
                            // Device read error
                            break;
                        }

                        MjsRadioDriver.this.handleIncomingBytes(readBuffer);

                    } else if (available < 0) {
                        // Device Read Error
                        break;
                    }
                }
            }

            if (this.mIsReading.compareAndSet(true, false)) {
                // Device Error, the loop was broken before reading was stopped by user
                MjsRadioDriver.this.mDriverEvents.onError(RadioError.DATA_READ_ERROR);

                if (MjsRadioDriver.this.isOpen()) {
                    MjsRadioDriver.this.mFtDevice.clrDtr();   // power off before closing
                    MjsRadioDriver.this.mFtDevice.close();
                }
            }

            synchronized (this) {
                if (this.mIsWaiting.compareAndSet(true, false)) {
                    notify();
                }
            }
        }

        synchronized void stopReading() {
            if (this.mIsReading.compareAndSet(true, false)) {
                this.mIsWaiting.set(true);
                try {
                    wait(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    this.mIsWaiting.set(false);
                }
            }
        }
    }

    // TODO: Implement Query Device / Open by Hardware ID (Should probably create a HDRadioManger class for this)

    /**
     * Class to connect to MJS Gadgets HD Radio cable.  It extends Thread because this thread needs
     * to run in the foreground.  All other threads launched in the HDRadio class can be Runnables
     * managed by the ExecutorService, which run in the background.
     */
    private class ConnectionThread extends Thread {

        private String mRequestedSerialNumber;

        private AtomicBoolean mIsWaiting = new AtomicBoolean(false);
        private AtomicBoolean mUsbRequestGranted = new AtomicBoolean(false);

        private BroadcastReceiver usbRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(ACTION_USB_PERMISSION)) {
                    synchronized (this) {
                        ConnectionThread.this.mUsbRequestGranted.set(
                                intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false));
                        ConnectionThread.this.resumeThread();
                    }
                }

            }
        };

        ConnectionThread(String serialNumber) {
            this.mRequestedSerialNumber = serialNumber;
        }

        private synchronized void resumeThread() {
            if (this.mIsWaiting.compareAndSet(true, false)) {
                notify();
            }
        }

        @Override
        public void run() {
            synchronized (OPEN_LOCK) {
                // TODO: should possibly put this somewhere else
                if (ftdiManager == null) {
                    try {
                        ftdiManager = D2xxManager.getInstance(MjsRadioDriver.this.mContext);
                    } catch (D2xxManager.D2xxException e) {
                        Log.e(TAG, "Unable to retreive instance of FTDI Manager");

                        MjsRadioDriver.this.mDriverEvents.onOpened(false);
                        // Dispatch On Opened Callback

                        return;
                    }
                    // Add MJS Gadgets cable to the D2XX driver's compatible device list
                    ftdiManager.setVIDPID(1027, 37752);

                }

                if (MjsRadioDriver.this.isOpen()) {
                    Log.i(TAG, "Radio already open");
                    MjsRadioDriver.this.mDriverEvents.onOpened(true);
                    return;
                }

                // Register USB permission receiver
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                MjsRadioDriver.this.mContext.registerReceiver(usbRequestReceiver, filter);

                UsbManager usbManager = (UsbManager) (MjsRadioDriver.this.mContext)
                        .getSystemService(Context.USB_SERVICE);
                ArrayList<UsbDevice> hdDeviceList = MjsRadioDriver.this.getDeviceList(UsbDevice.class);
                if (hdDeviceList.isEmpty()) {
                    // Dispatch On Opened Callback
                    MjsRadioDriver.this.mDriverEvents.onOpened(false);
                } else {

                    MjsRadioDriver.this.mFtDevice = null;
                    // Iterate through the list of compatible radio devices, comparing serial numbers if necessary
                    for (UsbDevice hdRadioDev : hdDeviceList) {
                        if (!usbManager.hasPermission(hdRadioDev)) {
                            this.mUsbRequestGranted.set(false);
                            // request permission and wait
                            PendingIntent pi = PendingIntent.getBroadcast(MjsRadioDriver.this.mContext,
                                    0, new Intent(ACTION_USB_PERMISSION), 0);
                            usbManager.requestPermission(hdRadioDev, pi);

                            synchronized (this) {
                                try {
                                    this.mIsWaiting.set(true);
                                    wait();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }

                            if (!this.mUsbRequestGranted.get()) {
                                Log.i(TAG, "Usb Permission not granted to device: " +
                                        (hdRadioDev).getDeviceName());
                                break;
                            }
                        }

                        // Need to add the usb device to the D2xxManager.  This happens automatically
                        // with plug events while the app is running.  Otherwise the need to either
                        // be enumerated via D2xxManager.createDeviceInfoList() or added directly
                        // like below
                        //
                        // If its already there it wont' be duplicated.
                        if (ftdiManager.addUsbDevice(hdRadioDev) < 1) {
                            Log.i(TAG, "Device was not added");
                        }

                        MjsRadioDriver.this.mFtDevice = ftdiManager.openByUsbDevice(MjsRadioDriver.this.mContext,
                                hdRadioDev);
                        if (MjsRadioDriver.this.mFtDevice != null &&
                                MjsRadioDriver.this.mFtDevice.isOpen()) {
                            if (mRequestedSerialNumber == null) {
                                break;
                            } else {
                                String devSerialNumber = MjsRadioDriver.this.mFtDevice
                                        .getDeviceInfo().serialNumber;
                                if (mRequestedSerialNumber.equals(devSerialNumber)) {
                                    break;
                                } else {
                                    MjsRadioDriver.this.mFtDevice.close();
                                    MjsRadioDriver.this.mFtDevice = null;
                                    Log.i(TAG, "Requested serial number " + mRequestedSerialNumber + " does not " +
                                            "match device serial number " + devSerialNumber);
                                }
                            }
                        } else {
                            Log.i(TAG, "Unable to open device: " +
                                    hdRadioDev.getDeviceName());
                            MjsRadioDriver.this.mFtDevice = null;
                        }

                    }

                    MjsRadioDriver.this.mContext.unregisterReceiver(usbRequestReceiver);

                    // If the device was found and opened, initialize and create connection
                    if (MjsRadioDriver.this.mFtDevice != null) {

                        MjsRadioDriver.this.mSerialNumber = MjsRadioDriver.this.mFtDevice
                                .getDeviceInfo().serialNumber;
                        MjsRadioDriver.this.mFtDevice.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);
                        MjsRadioDriver.this.mFtDevice.setBaudRate(115200);
                        MjsRadioDriver.this.mFtDevice.setDataCharacteristics(D2xxManager.FT_DATA_BITS_8,
                                D2xxManager.FT_STOP_BITS_1, D2xxManager.FT_PARITY_NONE);
                        MjsRadioDriver.this.mFtDevice.setFlowControl(D2xxManager.FT_FLOW_NONE, (byte) 0x0b, (byte) 0x0d);
                        MjsRadioDriver.this.mFtDevice.clrDtr(); // Don't power on
                        MjsRadioDriver.this.mFtDevice.setRts(); // Raise the RTS to turn on hardware mute

                        // Start read thread
                        MjsRadioDriver.this.mReadThread = new ReadThread();
                        MjsRadioDriver.this.mReadThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        MjsRadioDriver.this.mReadThread.start();

                        // Open success, dispatch callback
                        MjsRadioDriver.this.mDriverEvents.onOpened(true);
                    } else {
                        // Open failed, dispatch callback
                        MjsRadioDriver.this.mDriverEvents.onOpened(false);
                    }
                }
            }
        }
    }
}
