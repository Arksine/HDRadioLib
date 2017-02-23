package com.arksine.hdradiolib.drivers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import com.arksine.hdradiolib.enums.RadioError;
import com.arksine.deviceids.CH34xIds;
import com.arksine.usbserialex.UsbSerialDevice;
import com.arksine.usbserialex.UsbSerialInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Driver for Arduino connected to HD Radio, uploaded with the hdradiodriver sketch
 */

public class ArduinoDriver extends RadioDriver {
    private static final String TAG = ArduinoDriver.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.arksine.hdradiolib.USB_PERMISSION";
    private static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private final Object OPEN_LOCK = new Object();

    private Context mContext;
    private String mSketchId = "";

    private AtomicBoolean mIsConnected = new AtomicBoolean(false);
    private volatile UsbDevice mUsbDevice;
    private UsbSerialDevice mSerialPort;
    private volatile boolean mDisconnectReceiverRegistered = false;

    private BroadcastReceiver mDisconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_USB_DETACHED)) {
                synchronized (this) {
                    UsbDevice uDev = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (uDev.equals(ArduinoDriver.this.mUsbDevice)) {

                        Toast.makeText(context, "USB Device Disconnected",
                                Toast.LENGTH_SHORT).show();

                        // Disconnect from a new thread so we don't block the UI thread
                        Thread errorThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                ArduinoDriver.this.mDriverEvents.onError(RadioError.CONNECTION_ERROR);
                                ArduinoDriver.this.close();
                            }
                        });
                        errorThread.start();
                    }
                }
            }
        }
    };

    // Serial Read callback
    private UsbSerialInterface.UsbReadCallback mReadCallback = new UsbSerialInterface.UsbReadCallback() {

        @Override
        public void onReceivedData(byte[] buffer)
        {
            if (buffer.length > 0) {
                // Send the data back to the instantiating class via callback
                ArduinoDriver.this.handleIncomingBytes(buffer);
            }
        }
    };


    public ArduinoDriver(Context context) {
        this.mContext = context;
    }

    @Override
    public boolean isOpen() {
        return this.mSerialPort != null && this.mIsConnected.get();
    }

    @Override
    public void open() {
        this.openById(null);
    }

    @Override
    public void openById(String identifier) {
        ConnectionThread thread = new ConnectionThread(identifier);
        thread.start();
    }

    @Override
    public void close() {
        synchronized (OPEN_LOCK) {
            if (this.isOpen()) {
                this.clearDtr();
                this.clearRts();
                this.mSerialPort.close();
                this.mIsConnected.set(false);
                this.mSerialPort = null;

                if (this.mDisconnectReceiverRegistered) {
                    this.mContext.unregisterReceiver(mDisconnectReceiver);
                    mDisconnectReceiverRegistered = false;
                }
            }
        }

        this.mDriverEvents.onClosed();
    }

    @Override
    public void writeData(byte[] data) {
        this.mSerialPort.write(data);
    }

    @Override
    public String getIdentifier() {
        return mSketchId;
    }

    @Override
    public <T> ArrayList<T> getDeviceList(Class<T> listType) {
        UsbManager manager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> usbDeviceList = manager.getDeviceList();
        ArrayList<T> deviceList = new ArrayList<>(5);

        for (UsbDevice uDevice : usbDeviceList.values()) {

            if (UsbSerialDevice.isSupported(uDevice)) {

                /**
                 * Don't add the MJS HD Radio cable to the list if its connected.  Its an FTDI serial
                 * comm device, but its specialized, not for MCU use
                 *
                 * MJS Cable - VID 0x0403 (1027), PID 0x9378 (37752)
                 *
                 *  TODO: I bet the 3rd ID (sub pid ) is 937C
                 */
                if ((uDevice.getVendorId() == 1027) && (uDevice.getProductId() == 37752)) {
                    Log.v(TAG, "MJS Cable found, skipping from list");
                    break;
                }

                deviceList.add(listType.cast(uDevice));
            }
        }

        return deviceList;
    }

    /**
     * The functions below handle RTS/DTR functionality. Because the Arduino doesn't have access
     * to check handshake state of the serial connection, I need to write packets that can
     * be quickly and easily parsed.  We assume that no Packet will ever by 255 bytes long (IIRC the
     * longest packet sent is 27 bytes), so a Length byte of FF indicates that I am changing RTS
     * or DTS state.  The Arduino then raises/lowers the appropriate pin
     */
    @Override
    public void raiseRts() {
        byte[] packet = {(byte)0xA4, (byte)0xFF, (byte)0x09, (byte)0x01};
        this.writeData(packet);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void clearRts() {
        byte[] packet = {(byte)0xA4, (byte)0xFF, (byte)0x09, (byte)0x00};
        this.writeData(packet);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void raiseDtr() {
        byte[] packet = {(byte)0xA4, (byte)0xFF, (byte)0x08, (byte)0x01};
        this.writeData(packet);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void clearDtr() {
        byte[] packet = {(byte)0xA4, (byte)0xFF, (byte)0x08, (byte)0x00};
        this.writeData(packet);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Class to connect to MJS Gadgets HD Radio cable.  It extends Thread because this thread needs
     * to run in the foreground.  All other threads launched in the HDRadio class can be Runnables
     * managed by the ExecutorService, which run in the background.
     */
    private class ConnectionThread extends Thread {

        private String mRequestedId;
        private UsbManager mUsbManager;
        private AtomicBoolean mIsWaiting = new AtomicBoolean(false);
        private AtomicBoolean mUsbPermissonGranted = new AtomicBoolean(false);
        private AtomicBoolean mIdValid = new AtomicBoolean(false);
        private String mIncomingId;

        private BroadcastReceiver usbPermissonReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(ACTION_USB_PERMISSION)) {
                    synchronized (this) {
                        ConnectionThread.this.mUsbPermissonGranted.set(intent
                                .getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false));
                        ConnectionThread.this.resumeThread();
                    }
                }
            }
        };

        private UsbSerialInterface.UsbReadCallback mCheckIdCallback = new UsbSerialInterface.UsbReadCallback() {
            @Override
            public void onReceivedData(byte[] bytes) {
                for (byte b : bytes) {
                    if ((char)b == '<') {
                        ConnectionThread.this.mIncomingId = "";
                    } else if ((char)b == '>') {
                        // Verify the ID
                        if (ConnectionThread.this.mIncomingId.length() == 8 &&
                                ConnectionThread.this.mIncomingId.startsWith("HD"))
                        {
                            mIdValid.set(true);
                            ConnectionThread.this.resumeThread();
                        }
                    } else {
                        ConnectionThread.this.mIncomingId += (char)b;
                    }
                }
            }
        };

        ConnectionThread(String requestedId) {
            this.mRequestedId = requestedId;
        }

        private synchronized void resumeThread() {
            if (this.mIsWaiting.compareAndSet(true, false)) {
                notify();
            }
        }

        private UsbSerialDevice getSerialDevice(UsbDevice device) {
            if (!this.mUsbManager.hasPermission(device)) {

                this.mUsbPermissonGranted.set(false);
                // request permission and wait
                PendingIntent pi = PendingIntent.getBroadcast(ArduinoDriver.this.mContext,
                        0, new Intent(ACTION_USB_PERMISSION), 0);
                this.mUsbManager.requestPermission(device, pi);

                synchronized (this) {
                    try {
                        this.mIsWaiting.set(true);
                        wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (!this.mUsbPermissonGranted.get()) {
                    Log.e(TAG, "Usb Permission not granted to device: " + device.getDeviceName());
                    // Dispatch On Opened Callback

                    return null;
                }
            }

            UsbDeviceConnection deviceConnection = mUsbManager.openDevice(device);
            if (deviceConnection == null) {
                Log.e(TAG, "Unable to open Usb device");
            }
            // Open Serial device with DTR/RTS high
            UsbSerialDevice serialDevice = UsbSerialDevice
                    .createUsbSerialDevice(device, deviceConnection);


            if (serialDevice != null) {
                if (serialDevice.open()) {
                    // Open success
                    serialDevice.setBaudRate(115200);
                    serialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8);
                    serialDevice.setStopBits(UsbSerialInterface.STOP_BITS_1);
                    serialDevice.setParity(UsbSerialInterface.PARITY_NONE);
                    serialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                    serialDevice.read(ConnectionThread.this.mCheckIdCallback);

                    // Some micro controllers need time to initialize before you can communicate.
                    // CH34x is one such device, others need to be tested.
                    long sleeptime = 200;
                    if (CH34xIds.isDeviceSupported(device.getVendorId(), device.getProductId())) {
                        sleeptime = 2000;
                    }
                    try {
                        Thread.sleep(sleeptime);
                    } catch (InterruptedException e) {
                        Log.w(TAG, e.getMessage());
                    }

                    // Write init string to device
                    byte[] idReqPacket = {(byte)0xA4, (byte)0xFF, (byte)0x10, (byte)0x00 };
                    serialDevice.write(idReqPacket);

                    synchronized (this) {
                        try {
                            this.mIsWaiting.set(true);
                            wait(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    if (!this.mIdValid.get()) {
                        Log.e(TAG, "No Valid Id returned from device");
                        serialDevice.close();
                        return null;
                    }

                    return serialDevice;

                } else {
                    Log.e(TAG, "Usb Device not a supported serial device");
                    return null;
                }
            } else {
                Log.e(TAG, "Usb Device not a supported serial device");
                return null;

            }

        }


        @Override
        public void run() {
            synchronized (OPEN_LOCK) {
                if (ArduinoDriver.this.isOpen()) {
                    Log.i(TAG, "Radio already open");
                    // Dispatch On Opened Callback
                    // ArduinoDriver.this.mDriverEvents.onOpened(true);
                    return;
                }

                this.mUsbManager = (UsbManager)(ArduinoDriver.this.mContext)
                        .getSystemService(Context.USB_SERVICE);
                ArrayList<UsbDevice> hdDeviceList = ArduinoDriver.this.getDeviceList(UsbDevice.class);

                if (hdDeviceList.isEmpty()) {
                    // no mjs cable found, exit
                    ArduinoDriver.this.mDriverEvents.onOpened(false);
                    return;
                }

                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                ArduinoDriver.this.mContext.registerReceiver(usbPermissonReceiver, filter);
                UsbDevice requestedDevice = null;


                if (this.mRequestedId == null) {
                    // find the first mjs cable since a null value was passed
                    requestedDevice = hdDeviceList.get(0);
                    ArduinoDriver.this.mSerialPort = this.getSerialDevice(requestedDevice);

                } else {
                    // iterate through device list searching for serial number
                    int count = 0;
                    for (UsbDevice uDev : hdDeviceList) {
                        ArduinoDriver.this.mSerialPort = this.getSerialDevice(uDev);
                        if (ArduinoDriver.this.mSerialPort != null) {

                            // The serial port has a valid ID, compare with requested ID
                            if (this.mIncomingId.equals(this.mRequestedId)) {
                                requestedDevice = uDev;
                                break;
                            } else {
                                ArduinoDriver.this.mSerialPort.close();
                            }
                        }
                        ArduinoDriver.this.mSerialPort = null;
                        count++;
                    }

                    if (count == hdDeviceList.size() && ArduinoDriver.this.mSerialPort == null) {
                        Log.e(TAG, "Unable to find MCU matching requested ID: " +
                                this.mRequestedId);
                    }
                }

                ArduinoDriver.this.mContext.unregisterReceiver(usbPermissonReceiver);

                // Didn't get a valid device connection
                if (ArduinoDriver.this.mSerialPort == null) {
                    Log.e(TAG, "Unable to open Usb serial port");
                    ArduinoDriver.this.mDriverEvents.onOpened(false);
                    return;
                }

                // sleep for 100ms
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Set Read callback to standard parsing
                ArduinoDriver.this.mSerialPort.read(ArduinoDriver.this.mReadCallback);
                ArduinoDriver.this.mUsbDevice = requestedDevice;
                ArduinoDriver.this.mSketchId = this.mIncomingId;
                ArduinoDriver.this.raiseRts();

                // Register the Broadcast receiver to listen for Radio Disconnections
                if (!ArduinoDriver.this.mDisconnectReceiverRegistered) {
                    IntentFilter disconnectFilter = new IntentFilter(ACTION_USB_DETACHED);
                    ArduinoDriver.this.mContext
                            .registerReceiver(ArduinoDriver.this.mDisconnectReceiver,
                                    disconnectFilter);
                    ArduinoDriver.this.mDisconnectReceiverRegistered = true;
                }


                // Open success
                ArduinoDriver.this.mIsConnected.set(true);
                ArduinoDriver.this.mDriverEvents.onOpened(true);

            }
        }
    }

}
