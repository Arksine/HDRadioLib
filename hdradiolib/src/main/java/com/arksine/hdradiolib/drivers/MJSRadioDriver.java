package com.arksine.hdradiolib.drivers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import com.arksine.hdradiolib.enums.RadioError;
import com.arksine.deviceids.CH34xIds;
import com.arksine.usbserialex.UsbSerialDevice;
import com.arksine.usbserialex.UsbSerialInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;


/**
 * Test driver to control the MJS cable with the USBSerial Library instead of the D2XX Library
 */

public class MJSRadioDriver extends RadioDriver {
    private static final String ACTION_USB_PERMISSION = "com.arksine.hdradiolib.USB_PERMISSION";
    private static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private final Object OPEN_LOCK = new Object();

    private Context mContext;
    private String mSerialNumber;

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
                    if (uDev.equals(MJSRadioDriver.this.mUsbDevice)) {

                        Toast.makeText(context, "USB Device Disconnected",
                                Toast.LENGTH_SHORT).show();

                        // Disconnect from a new thread so we don't block the UI thread
                        Thread errorThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                MJSRadioDriver.this.mDriverEvents.onError(RadioError.CONNECTION_ERROR);
                                MJSRadioDriver.this.close();
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
            // Send the data back to the instantiating class via callback
            MJSRadioDriver.this.handleIncomingBytes(buffer);
        }
    };

    public MJSRadioDriver(Context context) {
        this.mContext = context;
    }

    @Override
    public void writeData(byte[] data) {
        this.mSerialPort.write(data);
    }

    @Override
    public <T> ArrayList<T> getDeviceList(Class<T> listType) {
        UsbManager manager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> usbDeviceList = manager.getDeviceList();
        ArrayList<T> hdDeviceList = new ArrayList<>(5);

        for (UsbDevice uDevice : usbDeviceList.values()) {
            if ((uDevice.getVendorId() == 1027) && (uDevice.getProductId() == 37752)) {
                Timber.v("MJS Gadgets HD Radio Cable found");
                hdDeviceList.add(listType.cast(uDevice));
            }
        }
        if (hdDeviceList.isEmpty()) {
            Timber.v("MJS Gadgets HD Radio Cable not found");
        }
        return hdDeviceList;
    }

    public static void addSupportedDevice(String vendorId, String productId) {
        // TODO:
    }


    @Override
    public String getIdentifier() {
        return this.mSerialNumber;
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
                mSerialPort.setDTR(false);
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
    public void raiseRts() {
        if (!this.mSerialPort.setRTS(true)) {
            this.close();
            this.mDriverEvents.onError(RadioError.RTS_RAISE_ERROR);
        }
    }

    @Override
    public void clearRts() {
        if(!this.mSerialPort.setRTS(false)) {
            this.close();
            this.mDriverEvents.onError(RadioError.RTS_CLEAR_ERROR);
        }
    }

    @Override
    public void raiseDtr() {
        if(!this.mSerialPort.setDTR(true)) {
            this.close();
            this.mDriverEvents.onError(RadioError.DTR_RAISE_ERROR);
        }
    }

    @Override
    public void clearDtr() {
        if(!this.mSerialPort.setDTR(false)) {
            this.close();
            this.mDriverEvents.onError(RadioError.DTR_CLEAR_ERROR);
        }
    }

    /**
     * Class to connect to MJS Gadgets HD Radio cable.  It extends Thread because this thread needs
     * to run in the foreground.  All other threads launched in the HDRadio class can be Runnables
     * managed by the ExecutorService, which run in the background.
     */
    private class ConnectionThread extends Thread {

        private String mRequestedSerialNumber;
        private UsbManager mUsbManager;

        // TODO: make the below booleans atomic
        private volatile boolean mIsWaiting = false;
        private volatile boolean mUsbPermissonGranted;

        private BroadcastReceiver usbPermissonReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(ACTION_USB_PERMISSION)) {
                    synchronized (this) {
                        ConnectionThread.this.mUsbPermissonGranted =
                                intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                        ConnectionThread.this.resumeThread();
                    }
                }
            }
        };

        ConnectionThread(String serialNumber) {
            this.mRequestedSerialNumber = serialNumber;
        }

        private synchronized void resumeThread() {
            if (this.mIsWaiting) {
                this.mIsWaiting = false;
                notify();
            }
        }

        private UsbDeviceConnection getDeviceConnection(UsbDevice device) {
            if (!this.mUsbManager.hasPermission(device)) {

                this.mUsbPermissonGranted = false;
                // request permission and wait
                PendingIntent pi = PendingIntent.getBroadcast(MJSRadioDriver.this.mContext,
                        0, new Intent(ACTION_USB_PERMISSION), 0);
                this.mUsbManager.requestPermission(device, pi);

                synchronized (this) {
                    try {
                        this.mIsWaiting = true;
                        wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (!this.mUsbPermissonGranted) {
                    Timber.w("Usb Permission not granted to device: %s", device.getDeviceName());
                    // Dispatch On Opened Callback

                    return null;
                }

            }
            return this.mUsbManager.openDevice(device);
        }


        @Override
        public void run() {
            synchronized (OPEN_LOCK) {
                if (MJSRadioDriver.this.isOpen()) {
                    Timber.i("Radio already open");
                    // Dispatch On Opened Callback
                    // MJSRadioDriver.this.mDriverEvents.onOpened(true);
                    return;
                }

                this.mUsbManager = (UsbManager)(MJSRadioDriver.this.mContext)
                        .getSystemService(Context.USB_SERVICE);
                ArrayList<UsbDevice> hdDeviceList = MJSRadioDriver.this.getDeviceList(UsbDevice.class);

                if (hdDeviceList.isEmpty()) {
                    // no mjs cable found, exit
                    MJSRadioDriver.this.mDriverEvents.onOpened(false);
                    return;
                }

                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                MJSRadioDriver.this.mContext.registerReceiver(usbPermissonReceiver, filter);
                UsbDevice requestedDevice = null;
                UsbDeviceConnection usbConnection = null;

                if (this.mRequestedSerialNumber == null) {
                    // find the first mjs cable since a null value was passed
                    requestedDevice = hdDeviceList.get(0);
                    usbConnection = getDeviceConnection(requestedDevice);

                } else {
                    // iterate through device list searching for serial number
                    int count = 0;
                    for (UsbDevice uDev : hdDeviceList) {
                        usbConnection = getDeviceConnection(uDev);
                        if (usbConnection != null) {
                            if (usbConnection.getSerial().equals(this.mRequestedSerialNumber)) {
                                requestedDevice = uDev;
                                break;
                            } else {
                                usbConnection.close();
                            }
                        }
                        usbConnection = null;
                        count++;
                    }
                    if (count == hdDeviceList.size() && usbConnection == null) {
                        Timber.e("Unable to find Usb Device matching serial: %s",
                                this.mRequestedSerialNumber);
                    }
                }

                MJSRadioDriver.this.mContext.unregisterReceiver(usbPermissonReceiver);

                // Didn't get a valid device connection
                if (usbConnection == null) {
                    Timber.e("Unable to open Usb connection");
                    MJSRadioDriver.this.mDriverEvents.onOpened(false);
                    return;
                }

                // We have a usb device connection, open the serial port with dtr and rts to default low
                MJSRadioDriver.this.mSerialPort = UsbSerialDevice
                        .createUsbSerialDevice(requestedDevice, usbConnection, false);
                if (MJSRadioDriver.this.mSerialPort != null) {
                    if (MJSRadioDriver.this.mSerialPort.open()) {
                        // Open success
                        mSerialPort.setRTS(true);
                        mSerialPort.setBaudRate(115200);
                        mSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                        mSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                        mSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
                        mSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                        mSerialPort.read(MJSRadioDriver.this.mReadCallback);

                        // Set the radio's Usb device
                        MJSRadioDriver.this.mUsbDevice = requestedDevice;
                        MJSRadioDriver.this.mSerialNumber = usbConnection.getSerial();
                        Timber.d("Device Serial Number: %s", mSerialNumber);

                        // Register the Broadcast receiver to listen for Radio Disconnections
                        if (!MJSRadioDriver.this.mDisconnectReceiverRegistered) {
                            IntentFilter disconnectFilter = new IntentFilter(ACTION_USB_DETACHED);
                            MJSRadioDriver.this.mContext
                                    .registerReceiver(MJSRadioDriver.this.mDisconnectReceiver,
                                            disconnectFilter);
                            MJSRadioDriver.this.mDisconnectReceiverRegistered = true;
                        }

                        // Some micro controllers need time to initialize before you can communicate.
                        // CH34x is one such device, others need to be tested.
                        if (CH34xIds.isDeviceSupported(mUsbDevice.getVendorId(), mUsbDevice.getProductId())) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                Timber.w(e);
                            }
                        }

                        // Open success
                        MJSRadioDriver.this.mIsConnected.set(true);
                        MJSRadioDriver.this.mDriverEvents.onOpened(true);
                    } else {
                        MJSRadioDriver.this.mDriverEvents.onOpened(false);
                    }
                } else {
                    Timber.i("Usb Device not a supported serial device");
                    // Dispatch On Opened Callback
                    MJSRadioDriver.this.mDriverEvents.onOpened(false);
                }
            }
        }
    }
}
