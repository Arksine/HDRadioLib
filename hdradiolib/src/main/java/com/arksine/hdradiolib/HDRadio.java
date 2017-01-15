package com.arksine.hdradiolib;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioConstant;
import com.arksine.hdradiolib.enums.RadioError;
import com.arksine.hdradiolib.enums.RadioOperation;
import com.felhr.deviceids.CH34xIds;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.ArrayList;
import java.util.HashMap;

// TODO: Create a HDRadioDevice abstract class that handles the connection.  There should be 3
// descendents: MJSDevice, MCUDevice, BluetoothDevice (or call it something else).  Each one
// provides functions for connection, disconnection, power toggle (DTR for MJS, custom for the others),
// and write.  I'll need to provide a static function to enumerate the devices, either all of them,
// Or 3 functions that enumerate depending on type (perhaps they can return HDRadioDevices).

/**
 * Library to communicate with a DirectedHD DMHD-1000 HD Radio via USB using the MJS Gadgets
 * interface cable
 */

public class HDRadio {
    private static final String TAG = HDRadio.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final String ACTION_USB_PERMISSION = "com.arksine.hdradiolib.USB_PERMISSION";
    private static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";


    private static final int STREAM_LOCK_TIMEOUT = 10000;
    private static final int POST_TUNE_DELAY = 1000;
    private static final int POWER_TOGGLE_DELAY = 2000;

    // Only allow one thread to open a device at a time, regardless of the instance
    private static final Object OPEN_LOCK = new Object();
    private static final Object POWER_LOCK = new Object();

    private Context mContext;
    private String mDeviceSerialNumber = "";
    private String mRadioHardwareId;

    private volatile UsbDevice mUsbDevice;
    private UsbSerialDevice mSerialPort;
    private RadioDataHandler mDataHandler;
    private CallbackHandler mCallbackHandler;

    private volatile long mPreviousTuneTime = 0;
    private volatile long mPreviousPowerTime = 0;

    private volatile boolean mIsPoweredOn = false;
    private volatile boolean mIsConnected = false;
    private volatile boolean mIsWaiting = false;
    private volatile boolean mSeekAll = true;

    private Handler mControlHandler;
    private RadioController mController = new RadioController() {
        @Override
        public void setSeekAll(final boolean seekAll) {
            synchronized (this) {
                HDRadio.this.mSeekAll = seekAll;
            }
        }

        @Override
        public boolean getSeekAll() {
            synchronized (this) {
                return HDRadio.this.mSeekAll;
            }
        }

        @Override
        public void powerOn() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.powerOnRadio();
                }
            });
        }

        @Override
        public void powerOff() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.powerOffRadio();
                }
            });
        }

        @Override
        public boolean getPowerStatus() {
            synchronized (POWER_LOCK) {
                return HDRadio.this.mIsPoweredOn;
            }
        }

        @Override
        public void muteOn() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(RadioCommand.MUTE, RadioOperation.SET, true);
                }
            });
        }

        @Override
        public void muteOff() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(RadioCommand.MUTE, RadioOperation.SET, false);
                }
            });
        }

        @Override
        public void setVolume(final int volume) {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(RadioCommand.VOLUME, RadioOperation.SET, volume);
                }
            });
        }

        @Override
        public void setVolumeUp() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    //sendRadioCommand(RadioCommand.VOLUME, RadioOperation.SET, RadioConstant.UP);
                    int volume = HDRadio.this.mDataHandler.getTrackingVariable(RadioCommand.VOLUME);
                    volume++;

                    if (volume <= 90) {
                        HDRadio.this.sendRadioCommand(RadioCommand.VOLUME, RadioOperation.SET, volume);
                    }
                }
            });
        }

        @Override
        public void setVolumeDown() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    int volume = HDRadio.this.mDataHandler.getTrackingVariable(RadioCommand.VOLUME);
                    volume--;

                    if (volume >= 0) {
                        HDRadio.this.sendRadioCommand(RadioCommand.VOLUME, RadioOperation.SET, volume);
                    }
                }
            });
        }

        @Override
        public void setBass(final int bass) {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(RadioCommand.BASS, RadioOperation.SET, bass);
                }
            });
        }

        @Override
        public void setBassUp() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    int bass = HDRadio.this.mDataHandler.getTrackingVariable(RadioCommand.BASS);
                    bass++;

                    if (bass <= 90) {
                        HDRadio.this.sendRadioCommand(RadioCommand.BASS, RadioOperation.SET, bass);
                    }
                }
            });
        }

        @Override
        public void setBassDown() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    int bass =  HDRadio.this.mDataHandler.getTrackingVariable(RadioCommand.TREBLE);
                    bass--;

                    if (bass >= 0) {
                        HDRadio.this.sendRadioCommand(RadioCommand.BASS, RadioOperation.SET, bass);
                    }
                }
            });
        }

        @Override
        public void setTreble(final int treble) {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(RadioCommand.TREBLE, RadioOperation.SET, treble);
                }
            });
        }

        @Override
        public void setTrebleUp() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    int treble = HDRadio.this.mDataHandler.getTrackingVariable(RadioCommand.TREBLE);
                    treble++;

                    if (treble <= 90) {
                        HDRadio.this.sendRadioCommand(RadioCommand.TREBLE, RadioOperation.SET, treble);
                    }
                }
            });
        }

        @Override
        public void setTrebleDown() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    int treble = HDRadio.this.mDataHandler.getTrackingVariable(RadioCommand.TREBLE);
                    treble--;

                    if (treble >= 0) {
                        HDRadio.this.sendRadioCommand(RadioCommand.TREBLE, RadioOperation.SET, treble);
                    }
                }
            });
        }

        @Override
        public void tune(final TuneInfo tuneInfo) {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(RadioCommand.TUNE, RadioOperation.SET, tuneInfo);

                    // TODO: not sure that this should be done here
                    final int subchannel = tuneInfo.getSubChannel();
                    if (subchannel > 0) {
                        HDRadio.this.sendRadioCommand(RadioCommand.HD_SUBCHANNEL, RadioOperation.SET, subchannel);

                        Thread checkHDStreamLockThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                long startTime = SystemClock.elapsedRealtime();

                                // HD Streamlock can take time, retry every 100ms for 10 seconds
                                // to set the subchannel.
                                while (subchannel != HDRadio.this.mDataHandler
                                        .getTrackingVariable(RadioCommand.HD_SUBCHANNEL)) {

                                    if (SystemClock.elapsedRealtime() > (STREAM_LOCK_TIMEOUT + startTime)) {
                                        Log.i(TAG, "Unable to Tune to HD Subchannel: " + subchannel);
                                        break;
                                    }

                                    // Try resetting every 200 ms
                                    try {
                                        Thread.sleep(200);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    HDRadio.this.sendRadioCommand(RadioCommand.HD_SUBCHANNEL, RadioOperation.SET, subchannel);
                                }
                            }
                        });
                        checkHDStreamLockThread.start();
                    }
                }
            });
        }

        @Override
        public void tuneUp() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(RadioCommand.TUNE, RadioOperation.SET, RadioConstant.UP);
                }
            });
        }

        @Override
        public void tuneDown() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(RadioCommand.TUNE, RadioOperation.SET, RadioConstant.DOWN);
                }
            });
        }

        @Override
        public void setHdSubChannel(final int subChannel) {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(RadioCommand.HD_SUBCHANNEL, RadioOperation.SET, subChannel);
                }
            });
        }

        @Override
        public void seekUp() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(RadioCommand.SEEK, RadioOperation.SET, RadioConstant.UP);
                }
            });
        }

        @Override
        public void seekDown() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(RadioCommand.SEEK, RadioOperation.SET, RadioConstant.DOWN);
                }
            });
        }

        @Override
        public void requestUpdate(final RadioCommand command) {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.sendRadioCommand(command, RadioOperation.GET, null);
                }
            });
        }
    };

    /**
     * Constructor for the HDRadio class.
     *
     * @param context       Calling application context
     * @param callbacks     User provided callbacks the callback handler executes
     */
    public HDRadio(@NonNull Context context, @NonNull HDRadioCallbacks callbacks) {

        this.mContext = context;

        // Callback Handler
        HandlerThread callbackHandlerThread = new HandlerThread("CallbackHandlerThread");
        callbackHandlerThread.start();
        Looper callbackLooper = callbackHandlerThread.getLooper();
        this.mCallbackHandler = new CallbackHandler(callbacks, callbackLooper);

        // Radio Control Handler
        HandlerThread controlHandlerThread = new HandlerThread("ControlHandlerThread");
        controlHandlerThread.start();
        Looper ctrLooper = controlHandlerThread.getLooper();
        this.mControlHandler = new Handler(ctrLooper);

        // Radio data handler
        HandlerThread dataHandlerThread = new HandlerThread("RadioDataHandlerThread",
                Process.THREAD_PRIORITY_BACKGROUND);
        dataHandlerThread.start();
        Looper dataLooper = dataHandlerThread.getLooper();
        RadioDataHandler.PowerNotifyCallback powerCb = new RadioDataHandler.PowerNotifyCallback() {
            @Override
            public void onPowerOnReceived() {
                HDRadio.this.notifyPowerOn();
            }
        };
        this.mDataHandler = new RadioDataHandler(dataLooper, this.mCallbackHandler, powerCb);

    }

    /**
     * Attempts to open a HD Radio device.  When the operation is finished the onOpened callback
     * is executed with the status of the open request.  If the request was successful, a reference
     * to the controller interface is also sent via the callback, otherwise the controller interface
     * is set to null.
     *
     * @param  dev      The usb device to open
     */
    public void open(final UsbDevice dev) {
        ConnectionThread thread = new ConnectionThread(dev);
        thread.start();
    }

    /**
     * Open First HDRadioDevice encountered, should only be used if there is only one radio device
     */
    public void open(){
        this.open(null);
    }

    public void close() {

        // Run this on the control handler's thread so any remaining requests in the queue
        // are executed
        this.mControlHandler.post(new Runnable() {
            @Override
            public void run() {
                // TODO: add any other cleanup necessary
                synchronized (OPEN_LOCK) {
                    if (HDRadio.this.isOpen()) {
                        if (HDRadio.this.mIsPoweredOn) {
                            HDRadio.this.powerOffRadio();
                        }
                        HDRadio.this.mSerialPort.close();
                        HDRadio.this.mIsConnected = false;
                        HDRadio.this.mSerialPort = null;

                        if (HDRadio.this.mDisconnectReceiverRegistered) {
                            HDRadio.this.mContext.unregisterReceiver(mDisconnectReceiver);
                            mDisconnectReceiverRegistered = false;
                        }
                    }
                }

                // Post On Closed Callback
                Message msg = HDRadio.this.mCallbackHandler.obtainMessage(CallbackHandler.CALLBACK_ON_CLOSED);
                HDRadio.this.mCallbackHandler.sendMessage(msg);
            }
        });
    }

    public boolean isOpen() {
        return this.mSerialPort != null && mIsConnected;
    }

    // TODO: for now we only enumerate  HD Radio cables, but in the future I should allow
    // a way to identify MCUs that are used for HDR communication
    /**
     * Searches connected USB Devices for the MJS Gadgets HD Radio Cable
     *
     * @return The matching UsbDevice instance if found, null if not found
     */
    public ArrayList<UsbDevice> getUsbRadioDevices() {
        UsbManager manager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> usbDeviceList = manager.getDeviceList();
        ArrayList<UsbDevice> hdDeviceList = new ArrayList<>(5);

        for (UsbDevice uDevice : usbDeviceList.values()) {
            if ((uDevice.getVendorId() == 1027) && (uDevice.getProductId() == 37752)) {
                Log.v(TAG, "MJS Gadgets HD Radio Cable found");
                hdDeviceList.add(uDevice);
            }
        }
        if (hdDeviceList.isEmpty()) {
            Log.v(TAG, "MJS Gadgets HD Radio Cable not found");
        }
        return hdDeviceList;
    }

    private synchronized void notifyPowerOn() {
        if (this.mIsWaiting) {
            this.mIsWaiting = false;
            notify();
        }
    }

    /**
     * Powers on the radio.  Although it should only be called from mControlHandler's looper,
     * it remains synchronized so a call to the RadioController's getPowerStatus() function is accurate
     */
    private void powerOnRadio() {
        synchronized (POWER_LOCK) {
            // make sure that the device is open
            if (!this.isOpen()) {
                Log.e(TAG, "Device not open, cannot power on");
                return;
            }

            long powerDelay = (this.mPreviousPowerTime + POWER_TOGGLE_DELAY) - SystemClock.elapsedRealtime();
            if (powerDelay > 0) {
                // sleep
                try {
                    Thread.sleep(powerDelay);
                    if (DEBUG)
                        Log.v(TAG, "Power delay, slept for: " + powerDelay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (!this.mIsPoweredOn) {

                // Set the hardware mute so speakers dont get blown by the initial power on
                this.mSerialPort.setRTS(true);

                // Raise DTR to power on
                this.mSerialPort.setDTR(true);

                // Wait until the radio gives a power on response, with a 10 second timeout
                synchronized (this) {
                    try {
                        this.mIsWaiting = true;
                        wait(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                this.mIsPoweredOn = true;

                // sleep for 1s after receiving power on confirmation
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.w(TAG, e.getMessage());
                }

                // release RTS (hardware mute)
                this.mSerialPort.setRTS(false);

                if (DEBUG) {
                    sendRadioCommand(RadioCommand.HD_UNIQUE_ID, RadioOperation.GET, null);
                    sendRadioCommand(RadioCommand.HD_HW_VERSION, RadioOperation.GET, null);
                    sendRadioCommand(RadioCommand.HD_API_VERSION, RadioOperation.GET, null);
                    /*sendRadioCommand(RadioCommand.HD_ENABLE_HD_TUNER, RadioOperation.GET, null);
                    sendRadioCommand(RadioCommand.VOLUME, RadioOperation.GET, null);
                    sendRadioCommand(RadioCommand.MUTE, RadioOperation.GET, null);
                    sendRadioCommand(RadioCommand.BASS, RadioOperation.GET, null);
                    sendRadioCommand(RadioCommand.TREBLE, RadioOperation.GET, null);
                    sendRadioCommand(RadioCommand.COMPRESSION, RadioOperation.GET, null);
                    sendRadioCommand(RadioCommand.HD_ACTIVE, RadioOperation.GET, null);
                    sendRadioCommand(RadioCommand.TUNE, RadioOperation.GET, null);*/
                }

                // Dispatch power on callback
                Message msg = this.mCallbackHandler.obtainMessage(CallbackHandler.CALLBACK_POWER_ON);
                this.mCallbackHandler.sendMessage(msg);

            }

            mPreviousPowerTime = SystemClock.elapsedRealtime();
        }

    }

    /**
     * Powers off the Radio.  Although it should only be called from mControlHandler's looper,
     * it remains synchronized so a call to the RadioController's getPowerStatus() function is accurate
     */
    private void powerOffRadio() {
        synchronized (POWER_LOCK) {
            long powerDelay = (this.mPreviousPowerTime + POWER_TOGGLE_DELAY) - SystemClock.elapsedRealtime();
            if (powerDelay > 0) {
                // sleep
                try {
                    Thread.sleep(powerDelay);
                    if (DEBUG)
                        Log.v(TAG, "Power delay, slept for: " + powerDelay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (this.isOpen() && this.mIsPoweredOn) {
                this.mSerialPort.setDTR(false);   // DTR off = Power off
                this.mIsPoweredOn = false;

                // Dispatch power off callback
                Message msg = this.mCallbackHandler.obtainMessage(CallbackHandler.CALLBACK_POWER_OFF);
                this.mCallbackHandler.sendMessage(msg);
            }

            this.mPreviousPowerTime = SystemClock.elapsedRealtime();
        }
    }

    /**
     * Builds a radio message and writes it to the HD Radio's serial interface.  This method is
     * NOT synchonized, as it should only be called in mControlHandler's looper.
     *
     * @param command       The command to send
     * @param operation     The associated operation (should be GET or SET)
     * @param data          Accompanying data (can be null for GET commands)
     */
    private void sendRadioCommand(RadioCommand command, RadioOperation operation, Object data) {
        byte[] radioPacket = RadioPacketBuilder.buildRadioPacket(command, operation, data, mSeekAll);
        if (radioPacket != null && this.mSerialPort != null) {
            // Do not allow any command to execute within 1 second of a direct tune
            long tuneDelay = (this.mPreviousTuneTime + POST_TUNE_DELAY) - SystemClock.elapsedRealtime();
            if (tuneDelay > 0) {
                // sleep
                try {
                    Thread.sleep(tuneDelay);
                    if (DEBUG) {
                        Log.v(TAG, "Post Tune delay, slept for: " + tuneDelay);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            this.mSerialPort.write(radioPacket);


            // If a tune command with a tuneInfo object was received, it is a direct tune.
            // Set the timer, as direction tunes require more time to lock
            if (command == RadioCommand.TUNE && data instanceof TuneInfo) {
                this.mPreviousTuneTime = SystemClock.elapsedRealtime();
            }

            // Always sleep 100ms between commands
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Invalid Radio Packet, cannot send");
        }

    }


    // TODO: Implement Query Device / Open by Hardware ID (Should probably create a HDRadioManger class for this)

    // Broadcast Reciever to handle disconnections (this is temporary)
    private BroadcastReceiver mDisconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_USB_DETACHED)) {
                synchronized (this) {
                    UsbDevice uDev = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (uDev.equals(HDRadio.this.mUsbDevice)) {

                        Toast.makeText(context, "USB Device Disconnected",
                                Toast.LENGTH_SHORT).show();

                        // Disconnect from a new thread so we don't block the UI thread
                        Thread errorThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Message msg = HDRadio.this.mCallbackHandler
                                        .obtainMessage(CallbackHandler.CALLBACK_DEVICE_ERROR);
                                msg.arg1 = RadioError.CONNECTION_ERROR.ordinal();
                                HDRadio.this.mCallbackHandler.sendMessage(msg);

                            }
                        });
                        errorThread.start();
                    }
                }
            }
        }
    };
    private volatile boolean mDisconnectReceiverRegistered = false;

    // Callback for bulk reads
    private UsbSerialInterface.UsbReadCallback mReadCallback = new UsbSerialInterface.UsbReadCallback() {

        @Override
        public void onReceivedData(byte[] buffer)
        {
            // Send the data back to the instantiating class via callback
            Message msg = HDRadio.this.mDataHandler.obtainMessage();
            msg.obj = buffer;
            HDRadio.this.mDataHandler.sendMessage(msg);
        }
    };

    /**
     * Class to connect to MJS Gadgets HD Radio cable.  It extends Thread because this thread needs
     * to run in the foreground.  All other threads launched in the HDRadio class can be Runnables
     * managed by the ExecutorService, which run in the background.
     */
    private class ConnectionThread extends Thread {

        private UsbDevice mRequestedUsbDevice;
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

        ConnectionThread(UsbDevice dev) {
            this.mRequestedUsbDevice = dev;
        }

        private synchronized void resumeThread() {
            if (this.mIsWaiting) {
                this.mIsWaiting = false;
                notify();
            }
        }

        @Override
        public void run() {
            synchronized (OPEN_LOCK) {
                if (HDRadio.this.isOpen()) {
                    Log.i(TAG, "Radio already open");
                    // Dispatch On Opened Callback
                    Message msg = HDRadio.this.mCallbackHandler
                            .obtainMessage(CallbackHandler.CALLBACK_ON_OPENED);
                    msg.arg1 = 1;  // Open success
                    msg.obj = HDRadio.this.mController;
                    HDRadio.this.mCallbackHandler.sendMessage(msg);
                    return;
                }

                // TODO: currently only support MJS cables, want to support MCUs and Bluetooth
                //       connections as well

                UsbManager usbManager = (UsbManager)(HDRadio.this.mContext)
                        .getSystemService(Context.USB_SERVICE);

                if (this.mRequestedUsbDevice == null) {
                    // find the first mjs cable since a null value was passed

                    ArrayList<UsbDevice> hdDeviceList = HDRadio.this.getUsbRadioDevices();
                    if (hdDeviceList.isEmpty()) {
                        // no mjs cable found
                        Log.e(TAG, "No Usb device passed to open, and no MJS Cables found");
                        // Dispatch On Opened Callback
                        Message msg = HDRadio.this.mCallbackHandler
                                .obtainMessage(CallbackHandler.CALLBACK_ON_OPENED);
                        msg.arg1 = 0;  // Open fail
                        HDRadio.this.mCallbackHandler.sendMessage(msg);
                        return;
                    } else {
                        this.mRequestedUsbDevice = hdDeviceList.get(0);
                    }
                }

                // Request permission
                if (!usbManager.hasPermission(this.mRequestedUsbDevice)) {
                    // Register broadcast receiver
                    IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                    HDRadio.this.mContext.registerReceiver(usbPermissonReceiver, filter);

                    this.mUsbPermissonGranted = false;
                    // request permission and wait
                    PendingIntent pi = PendingIntent.getBroadcast(HDRadio.this.mContext,
                            0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(this.mRequestedUsbDevice, pi);

                    synchronized (this) {
                        try {
                            this.mIsWaiting = true;
                            wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    HDRadio.this.mContext.unregisterReceiver(usbPermissonReceiver);

                    if (!this.mUsbPermissonGranted) {
                        Log.e(TAG, "Usb Permission not granted to device: " + this.mRequestedUsbDevice.getDeviceName());
                        // Dispatch On Opened Callback
                        Message msg = HDRadio.this.mCallbackHandler
                                .obtainMessage(CallbackHandler.CALLBACK_ON_OPENED);
                        msg.arg1 = 0;  // Open fail
                        HDRadio.this.mCallbackHandler.sendMessage(msg);
                        return;
                    }
                }

                // We have a usb device with permission, open it
                UsbDeviceConnection usbConnection = usbManager.openDevice(this.mRequestedUsbDevice);
                HDRadio.this.mSerialPort = UsbSerialDevice
                        .createUsbSerialDevice(this.mRequestedUsbDevice, usbConnection);
                if (HDRadio.this.mSerialPort != null) {
                    if (HDRadio.this.mSerialPort.open()) {
                        // Open success
                        // TODO: Forked Serial library defaults DTR off.  Some MCUs may require DTR on
                        //mSerialPort.setDTR(true);  // turn off DTR for MJS cables (I wouldn't do this for MCUs)
                        mSerialPort.setRTS(true);
                        mSerialPort.setBaudRate(115200);
                        mSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                        mSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                        mSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
                        mSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                        mSerialPort.read(HDRadio.this.mReadCallback);

                        // Set the radio's Usb device
                        HDRadio.this.mUsbDevice = this.mRequestedUsbDevice;
                        HDRadio.this.mIsConnected = true;

                        // Get the device serial number (API 21+ only, I can probably do it
                        // with a direct block transfer though)
                        //HDRadio.this.mDeviceSerialNumber = ftdev.getDeviceInfo().serialNumber;

                        // Register the Broadcast receiver to listen for Radio Disconnections
                        if (!HDRadio.this.mDisconnectReceiverRegistered) {
                            IntentFilter filter = new IntentFilter(ACTION_USB_DETACHED);
                            HDRadio.this.mContext.registerReceiver(HDRadio.this.mDisconnectReceiver, filter);
                            HDRadio.this.mDisconnectReceiverRegistered = true;
                        }

                        // Some micro controllers need time to initialize before you can communicate.
                        // CH34x is one such device, others need to be tested.
                        if (CH34xIds.isDeviceSupported(mUsbDevice.getVendorId(), mUsbDevice.getProductId())) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                Log.w(TAG, e.getMessage());
                            }
                        }

                        Message msg = HDRadio.this.mCallbackHandler
                                .obtainMessage(CallbackHandler.CALLBACK_ON_OPENED);
                        msg.arg1 = 1;  // Open success
                        msg.obj = HDRadio.this.mController;
                        HDRadio.this.mCallbackHandler.sendMessage(msg);
                    } else {
                        Log.e(TAG, "Unable to open device");
                        // Dispatch On Opened Callback
                        Message msg = HDRadio.this.mCallbackHandler
                                .obtainMessage(CallbackHandler.CALLBACK_ON_OPENED);
                        msg.arg1 = 0;  // Open fail
                        HDRadio.this.mCallbackHandler.sendMessage(msg);
                    }
                } else {
                    Log.e(TAG, "Usb Device not a support serial device");
                    // Dispatch On Opened Callback
                    Message msg = HDRadio.this.mCallbackHandler
                            .obtainMessage(CallbackHandler.CALLBACK_ON_OPENED);
                    msg.arg1 = 0;  // Open fail
                    HDRadio.this.mCallbackHandler.sendMessage(msg);
                }
            }
        }
    }
}
