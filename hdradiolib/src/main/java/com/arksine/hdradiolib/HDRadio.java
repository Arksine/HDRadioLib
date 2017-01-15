package com.arksine.hdradiolib;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;

import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioConstant;
import com.arksine.hdradiolib.enums.RadioOperation;
import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Library to communicate with a DirectedHD DMHD-1000 HD Radio via USB using the MJS Gadgets
 * interface cable
 */

public class HDRadio {
    private static final String TAG = HDRadio.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final String ACTION_USB_PERMISSION = "com.arksine.hdradiolib.USB_PERMISSION";

    private static final int STREAM_LOCK_TIMEOUT = 10000;
    private static final int POST_TUNE_DELAY = 1000;
    private static final int POWER_TOGGLE_DELAY = 2000;

    // Only allow one thread to open a device at a time, regardless of the instance
    private static final Object OPEN_LOCK = new Object();
    private static final Object POWER_LOCK = new Object();

    private static D2xxManager ftdiManager = null;

    private Context mContext;
    private String mDeviceSerialNumber = "";
    private String mRadioHardwareId;

    private RadioDataHandler mDataHandler;
    private RadioConnection mRadioConnecton;
    private CallbackHandler mCallbackHandler;

    private volatile long mPreviousTuneTime = 0;
    private volatile long mPreviousPowerTime = 0;

    private volatile boolean mIsPoweredOn = false;
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

        // Control Handler
        HandlerThread controlHandlerThread = new HandlerThread("ControlHandlerThread");
        controlHandlerThread.start();
        Looper ctrlLooper = controlHandlerThread.getLooper();
        this.mControlHandler = new Handler(ctrlLooper);

        // Data Handler
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
     * @param serialNumber If null, the first matching enumerated device is opened.  Otherwise an
     *                     attempt is made to open the device by serial number
     */
    public void open(final String serialNumber) {
        ConnectionThread thread = new ConnectionThread(serialNumber);
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

                        HDRadio.this.mRadioConnecton.close();
                    }
                }

                // Post On Closed Callback
                Message msg = HDRadio.this.mCallbackHandler.obtainMessage(CallbackHandler.CALLBACK_ON_CLOSED);
                HDRadio.this.mCallbackHandler.sendMessage(msg);
            }
        });
    }

    public boolean isOpen() {
        return this.mRadioConnecton != null && this.mRadioConnecton.isOpen();
    }

    public String getDeviceSerialNumber() {
        return this.mDeviceSerialNumber;
    }

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
                this.mRadioConnecton.raiseRTS();

                // Raise DTR to power on
                this.mRadioConnecton.raiseDTR();

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
                this.mRadioConnecton.clearRTS();

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
                this.mRadioConnecton.clearDTR();   // DTR off = Power off
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
        if (radioPacket != null && this.mRadioConnecton != null) {
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

            this.mRadioConnecton.writeData(radioPacket);


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

    /**
     * Class to connect to MJS Gadgets HD Radio cable.  It extends Thread because this thread needs
     * to run in the foreground.  All other threads launched in the HDRadio class can be Runnables
     * managed by the ExecutorService, which run in the background.
     */
    private class ConnectionThread extends Thread {

        private String mRequestedSerialNumber;
        private volatile boolean mIsWaiting = false;
        private volatile boolean mUsbRequestGranted;

        private BroadcastReceiver usbRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(ACTION_USB_PERMISSION)) {
                    synchronized (this) {
                        ConnectionThread.this.mUsbRequestGranted =
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

        @Override
        public void run() {
            synchronized (OPEN_LOCK) {
                // TODO: should possibly put this somewhere else
                if (ftdiManager == null) {
                    try {
                        ftdiManager = D2xxManager.getInstance(HDRadio.this.mContext);
                    } catch (D2xxManager.D2xxException e) {
                        Log.e(TAG, "Unable to retreive instance of FTDI Manager");

                        // Dispatch On Opened Callback
                        Message msg = HDRadio.this.mCallbackHandler
                                .obtainMessage(CallbackHandler.CALLBACK_ON_OPENED);
                        msg.arg1 = 0;  // Open fail
                        HDRadio.this.mCallbackHandler.sendMessage(msg);
                        return;
                    }
                    // Add MJS Gadgets cable to the D2XX driver's compatible device list
                    ftdiManager.setVIDPID(1027, 37752);

                }

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

                // Register USB permission receiver
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                HDRadio.this.mContext.registerReceiver(usbRequestReceiver, filter);

                UsbManager usbManager = (UsbManager)(HDRadio.this.mContext).getSystemService(Context.USB_SERVICE);
                ArrayList<UsbDevice> hdDeviceList = HDRadio.this.getUsbRadioDevices();
                if (hdDeviceList.isEmpty()) {
                    // Dispatch On Opened Callback
                    Message msg = HDRadio.this.mCallbackHandler
                            .obtainMessage(CallbackHandler.CALLBACK_ON_OPENED);
                    msg.arg1 = 0;  // Open fail
                    HDRadio.this.mCallbackHandler.sendMessage(msg);
                } else {

                    FT_Device ftdev = null;
                    // Iterate through the list of compatible radio devices, comparing serial numbers if necessary
                    for (UsbDevice hdRadioDev : hdDeviceList) {
                        if (!usbManager.hasPermission(hdRadioDev)) {
                            this.mUsbRequestGranted = false;
                            // request permission and wait
                            PendingIntent pi = PendingIntent.getBroadcast(HDRadio.this.mContext,
                                    0, new Intent(ACTION_USB_PERMISSION), 0);
                            usbManager.requestPermission(hdRadioDev, pi);

                            synchronized (this) {
                                try {
                                    this.mIsWaiting = true;
                                    wait();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }

                            if (!this.mUsbRequestGranted) {
                                Log.i(TAG, "Usb Permission not granted to device: " + hdRadioDev.getDeviceName());
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

                        ftdev = ftdiManager.openByUsbDevice(HDRadio.this.mContext, hdRadioDev);
                        if (ftdev != null && ftdev.isOpen()) {
                            if (mRequestedSerialNumber == null) {
                                break;
                            } else {
                                String devSerialNumber = ftdev.getDeviceInfo().serialNumber;
                                if (mRequestedSerialNumber.equals(devSerialNumber)) {
                                    break;
                                } else {
                                    ftdev.close();
                                    ftdev = null;
                                    Log.i(TAG, "Requested serial number " + mRequestedSerialNumber + " does not " +
                                            "match device serial number " + devSerialNumber);
                                }
                            }
                        } else {
                            Log.i(TAG, "Unable to open device: " + hdRadioDev.getDeviceName());
                            ftdev = null;
                        }

                    }

                    // If the device was found and opened, initialize and create connection
                    if (ftdev != null) {

                        HDRadio.this.mDeviceSerialNumber = ftdev.getDeviceInfo().serialNumber;
                        ftdev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);
                        ftdev.setBaudRate(115200);
                        ftdev.setDataCharacteristics(D2xxManager.FT_DATA_BITS_8,
                                D2xxManager.FT_STOP_BITS_1, D2xxManager.FT_PARITY_NONE);
                        ftdev.setFlowControl(D2xxManager.FT_FLOW_NONE, (byte) 0x0b, (byte) 0x0d);
                        ftdev.clrDtr(); // Don't power on
                        ftdev.setRts(); // Raise the RTS to turn on hardware mute

                        // create radio connection
                        HDRadio.this.mRadioConnecton = new RadioConnection(ftdev,
                                HDRadio.this.mDataHandler, HDRadio.this.mCallbackHandler);

                        // Dispatch On Opened Callback
                        Message msg = HDRadio.this.mCallbackHandler
                                .obtainMessage(CallbackHandler.CALLBACK_ON_OPENED);
                        msg.arg1 = 1;  // Open success
                        msg.obj = HDRadio.this.mController;
                        HDRadio.this.mCallbackHandler.sendMessage(msg);
                    } else {
                        // Dispatch On Opened Callback
                        Message msg = HDRadio.this.mCallbackHandler
                                .obtainMessage(CallbackHandler.CALLBACK_ON_OPENED);
                        msg.arg1 = 0;  // Open fail
                        HDRadio.this.mCallbackHandler.sendMessage(msg);
                    }
                }

                HDRadio.this.mContext.unregisterReceiver(usbRequestReceiver);
            }
        }
    }

}
