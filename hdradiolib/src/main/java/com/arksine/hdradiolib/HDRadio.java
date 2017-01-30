package com.arksine.hdradiolib;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;

import com.arksine.hdradiolib.enums.PowerStatus;
import com.arksine.hdradiolib.enums.RadioBand;
import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioConstant;
import com.arksine.hdradiolib.enums.RadioError;
import com.arksine.hdradiolib.enums.RadioOperation;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Library to communicate with a DirectedHD DMHD-1000 HD Radio via USB using the MJS Gadgets
 * interface cable
 */

public class HDRadio {
    private static final String TAG = HDRadio.class.getSimpleName();
    private static final boolean DEBUG = true;
    private SharedPreferences mRadioPreferences;

    public enum DriverType {MJS_DRIVER, USB_SERIAL_TEST_DRIVER, CUSTOM}

    private static final int POST_COMMAND_DELAY = 150;
    private static final int POST_TUNE_DELAY = 1000;
    private static final int POWER_TOGGLE_DELAY = 2000;
    private final Object POWER_LOCK = new Object();

    private Context mContext;
    private RadioValues mRadioValues;
    private RadioDataHandler mDataHandler;
    private EventHandler mEventHandler;
    private Handler mControlHandler;
    private RadioDriver mRadioDriver;
    private volatile long mPreviousTuneTime = 0;        // These longs are only accessed by syncrhonized
    private volatile long mPreviousPowerTime = 0;       // methods, so atomic access is a given
    private AtomicReference<PowerStatus> mPowerStatus = new AtomicReference<>(PowerStatus.POWERED_OFF);
    //private AtomicBoolean mIsPoweredOn = new AtomicBoolean(false);
    private AtomicBoolean mIsWaiting = new AtomicBoolean(false);
    private AtomicBoolean mSeekAll = new AtomicBoolean(false);

    private final SetSubchannelRunnable mSetSubchannelRunnable = new SetSubchannelRunnable();
    private final Runnable mRequestSignalRunnable = new Runnable() {
        @Override
        public void run() {
            // If HD is active, request HD signal strength
            if (HDRadio.this.mRadioValues.mHdActive.get()) {
                HDRadio.this.sendRadioCommand(RadioCommand.HD_SIGNAL_STRENGTH,
                        RadioOperation.GET, null);
            } else {
                HDRadio.this.sendRadioCommand(RadioCommand.SIGNAL_STRENGTH,
                        RadioOperation.GET, null);
            }

            HDRadio.this.mControlHandler.postDelayed(mRequestSignalRunnable, 10000);
        }
    };

    private final RadioDriver.DriverEvents mDriverEvents = new RadioDriver.DriverEvents() {
        @Override
        public void onOpened(boolean success) {
            HDRadio.this.mEventHandler.handleOpenedEvent(success, HDRadio.this.mController);
        }

        @Override
        public void onError(RadioError error) {
            // Send Device Error Callback
            HDRadio.this.mEventHandler.handleDeviceErrorEvent(error);
        }

        @Override
        public void onClosed() {
            // Post On Closed Callback
            HDRadio.this.mEventHandler.handleClosedEvent();
        }
    };

    private final RadioController mController = new RadioController() {
        @Override
        public void setSeekAll(final boolean seekAll) {
             HDRadio.this.mSeekAll.set(seekAll);
        }

        @Override
        public boolean getSeekAll() {
            return HDRadio.this.mSeekAll.get();
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
        public boolean isPoweredOn() {
            return HDRadio.this.mPowerStatus.get() == PowerStatus.POWERED_ON;
        }

        @Override
        public PowerStatus getPowerStatus() {
            return HDRadio.this.mPowerStatus.get();
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
                    int volume = HDRadio.this.mRadioValues.mVolume.get();
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
                    int volume = HDRadio.this.mRadioValues.mVolume.get();
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
                    int bass = HDRadio.this.mRadioValues.mBass.get();
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
                    int bass = HDRadio.this.mRadioValues.mBass.get();
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
                    int treble = HDRadio.this.mRadioValues.mTreble.get();
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
                    int treble = HDRadio.this.mRadioValues.mTreble.get();
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
                    // Remove pending Signal Requests
                    HDRadio.this.mControlHandler.removeCallbacks(mRequestSignalRunnable);
                    HDRadio.this.mControlHandler.removeCallbacks(mSetSubchannelRunnable);

                    HDRadio.this.sendRadioCommand(RadioCommand.TUNE, RadioOperation.SET, tuneInfo);

                    final int subchannel = tuneInfo.getSubChannel();
                    if (subchannel > 0) {
                        // Start subchannel requests
                        HDRadio.this.mSetSubchannelRunnable.setSubchannel(subchannel);
                    }

                }
            });
        }

        @Override
        public void tuneUp() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Remove pending Signal Requests
                    HDRadio.this.mControlHandler.removeCallbacks(mRequestSignalRunnable);
                    HDRadio.this.mControlHandler.removeCallbacks(mSetSubchannelRunnable);
                    HDRadio.this.sendRadioCommand(RadioCommand.TUNE, RadioOperation.SET, RadioConstant.UP);
                }
            });
        }

        @Override
        public void tuneDown() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Remove pending Signal Requests
                    HDRadio.this.mControlHandler.removeCallbacks(mRequestSignalRunnable);
                    HDRadio.this.mControlHandler.removeCallbacks(mSetSubchannelRunnable);
                    HDRadio.this.sendRadioCommand(RadioCommand.TUNE, RadioOperation.SET, RadioConstant.DOWN);
                }
            });
        }

        @Override
        public void setHdSubChannel(final int subChannel) {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.mControlHandler.removeCallbacks(mSetSubchannelRunnable);
                    HDRadio.this.sendRadioCommand(RadioCommand.HD_SUBCHANNEL, RadioOperation.SET, subChannel);
                }
            });
        }

        @Override
        public void seekUp() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Remove pending Signal Requests
                    HDRadio.this.mControlHandler.removeCallbacks(mRequestSignalRunnable);
                    HDRadio.this.mControlHandler.removeCallbacks(mSetSubchannelRunnable);
                    HDRadio.this.sendRadioCommand(RadioCommand.SEEK, RadioOperation.SET, RadioConstant.UP);
                }
            });
        }

        @Override
        public void seekDown() {
            HDRadio.this.mControlHandler.post(new Runnable() {
                @Override
                public void run() {
                    HDRadio.this.mControlHandler.removeCallbacks(mRequestSignalRunnable);
                    HDRadio.this.mControlHandler.removeCallbacks(mSetSubchannelRunnable);
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

    // Constructor that defaults to the MJS driver
    public HDRadio(@NonNull Context context, @NonNull HDRadioEvents callbacks) {
        this(context, callbacks, DriverType.MJS_DRIVER);
    }

    // Constructor that allows the user to specifiy their own driver
    public HDRadio(@NonNull Context context, @NonNull HDRadioEvents callbacks,
                   @NonNull RadioDriver driver) {
        this(context, callbacks, DriverType.CUSTOM);
        this.mRadioDriver = driver;
        if (!this.mRadioDriver.isInitialized()) {
            this.mRadioDriver.initialize(mDataHandler, mDriverEvents);
        }
    }

    /**
     * Constructor for the HDRadio class.
     *
     * @param context       Calling application context
     * @param callbacks     User provided callbacks the callback handler executes
     * @param dType         Driver Type to use for communication with the HDRadio
     */
    public HDRadio(@NonNull Context context, @NonNull HDRadioEvents callbacks,
                   @NonNull DriverType dType) {

        this.mContext = context;
        this.mRadioPreferences = context.getSharedPreferences(
                context.getString(R.string.pref_file_key), Context.MODE_PRIVATE);

        this.mRadioValues = new RadioValues();

        HandlerThread eventHandlerThread = new HandlerThread("EventHandlerThread");
        eventHandlerThread.start();
        Looper eventLooper = eventHandlerThread.getLooper();
        this.mEventHandler = new EventHandler(callbacks, eventLooper);

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
        RadioDataHandler.DataHandlerEvents dataCbs = new RadioDataHandler.DataHandlerEvents() {
            @Override
            public void onPowerOnReceived() {
                HDRadio.this.notifyPowerOn();
            }

            @Override
            public void onTuneReceived() {
                // TODO: Request HD Subchannel if necessary
                HDRadio.this.mControlHandler.post(mRequestSignalRunnable);
            }
        };
        this.mDataHandler = new RadioDataHandler(dataLooper, this.mEventHandler, dataCbs,
                this.mRadioValues);

        switch (dType) {
            case MJS_DRIVER:
                this.mRadioDriver = new MjsRadioDriver(mContext, mDataHandler, mDriverEvents);
                break;
            case USB_SERIAL_TEST_DRIVER:
                // TODO: Implement
                break;
            case CUSTOM:
                Log.i(TAG, "Using custom driver");
                break;
            default:
                this.mRadioDriver = new MjsRadioDriver(mContext, mDataHandler, mDriverEvents);
                Log.i(TAG, "Invalid Driver Request, use Mjs Driver");
        }

    }


    public void openById(final Object deviceId) {
        if (!this.isOpen()) {
            this.mRadioDriver.openById(getDeviceId());
        } else {
            Log.i(TAG, "Device already open.");
        }
    }

    /**
     * Open First HDRadioDevice encountered, should only be used if there is only one radio device
     */
    public void open() {
        if (!this.isOpen()) {
            this.mRadioDriver.open();
        } else {
            Log.i(TAG, "Device already open.");
        }
    }

    public void close() {

        // Run this on the control handler's thread so any remaining requests in the queue
        // are executed
        this.mControlHandler.post(new Runnable() {
            @Override
            public void run() {

                if (HDRadio.this.isOpen()) {
                    if (HDRadio.this.mPowerStatus.get() == PowerStatus.POWERED_ON) {
                        HDRadio.this.powerOffRadio();
                    }

                    HDRadio.this.mRadioDriver.close();
                }

            }
        });
    }

    public boolean isOpen() {
        return this.mRadioDriver.isOpen();
    }

    public Object getDeviceId() {
        return this.mRadioDriver.getIdentifier();
    }

    /**
     * Requests a device list from the Driver and returns it
     *
     * @return The matching UsbDevice instance if found, null if not found
     */
    public ArrayList<Object> getDeviceList() {
        return this.mRadioDriver.getDeviceList();
    }

    private synchronized void notifyPowerOn() {
        if (this.mIsWaiting.compareAndSet(true, false)) {
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

            long powerDelay = (this.mPreviousPowerTime + POWER_TOGGLE_DELAY)
                    - SystemClock.elapsedRealtime();
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

            if (this.mPowerStatus.compareAndSet(PowerStatus.POWERED_OFF, PowerStatus.POWERING_ON)) {

                // Set the hardware mute so speakers dont get blown by the initial power on
                this.mRadioDriver.raiseRts();

                // Raise DTR to power on
                this.mRadioDriver.raiseDtr();

                // Wait until the radio gives a power on response, with a 10 second timeout
                synchronized (this) {
                    try {
                        this.mIsWaiting.set(true);
                        wait(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (this.mIsWaiting.get()) {
                    //TODO: the timeout was met, this is an error.  Send an error callback
                    return;
                }

                this.initializeRadio();

            }

            this.mPreviousPowerTime = SystemClock.elapsedRealtime();
        }

    }

    /**
     * Called after every power on.  Requests hardware ids, sets persisted volume, treble, bass
     * and Tune.
     */
    private void initializeRadio() {
        this.mPowerStatus.set(PowerStatus.INITIALIZING);

        // sleep for 1s after receiving power on confirmation
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.w(TAG, e.getMessage());
        }


        // release RTS (hardware mute)
        this.mRadioDriver.clearRts();

        // Dispatch power on callback
        this.mEventHandler.handlePowerOnEvent();


        // Retreived persistent values
        this.mSeekAll.set(this.mRadioPreferences.getBoolean("radiolib_pref_key_seekall", true));
        int frequency = this.mRadioPreferences.getInt("radiolib_pref_key_frequency", 879);
        RadioBand band = RadioBand.valueOf(this.mRadioPreferences
                .getString("radiolib_pref_key_band", "FM"));
        int subch = this.mRadioPreferences.getInt("radiolib_pref_key_subchannel", 0);
        TuneInfo savedTune = new TuneInfo(band, frequency, subch);
        int volume = this.mRadioPreferences.getInt("radiolib_pref_key_volume", 50);
        int bass = this.mRadioPreferences.getInt("radiolib_pref_key_bass", 15);
        int treble = this.mRadioPreferences.getInt("radiolib_pref_key_treble", 15);

        this.mController.requestUpdate(RadioCommand.HD_UNIQUE_ID);
        this.mController.requestUpdate(RadioCommand.HD_HW_VERSION);
        this.mController.requestUpdate(RadioCommand.HD_API_VERSION);
        this.mController.setVolume(volume);
        this.mController.setBass(bass);
        this.mController.setTreble(treble);
        this.mController.tune(savedTune);

        this.mPowerStatus.set(PowerStatus.POWERED_ON);
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

            if (this.isOpen() && this.mPowerStatus.compareAndSet(PowerStatus.POWERED_ON,
                    PowerStatus.POWERING_OFF)) {

                this.mRadioDriver.clearDtr();   // DTR off = Power off

                // Because the radio won't send a power off reply, set the power off variable to false
                this.mRadioValues.mPower.set(false);

                // Persist these values
                TuneInfo currentTune = this.mRadioValues.mTune.get();
                this.mRadioPreferences.edit()
                        .putBoolean("radiolib_pref_key_seekall", this.mSeekAll.get())
                        .putInt("radiolib_pref_key_frequency", currentTune.getFrequency())
                        .putString("radiolib_pref_key_band", currentTune.getBand().toString())
                        .putInt("radiolib_pref_key_subchannel", currentTune.getSubChannel())
                        .putInt("radiolib_pref_key_volume", this.mRadioValues.mVolume.get())
                        .putInt("radiolib_pref_key_bass", this.mRadioValues.mBass.get())
                        .putInt("radiolib_pref_key_treble", this.mRadioValues.mTreble.get())
                        .apply();

                this.mPowerStatus.set(PowerStatus.POWERED_OFF);

                // Dispatch power off callback
                this.mEventHandler.handlePowerOffEvent();
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
        byte[] radioPacket = RadioPacketBuilder.buildRadioPacket(command, operation, data, mSeekAll.get());
        if (radioPacket != null && this.mRadioDriver.isOpen()) {
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

            this.mRadioDriver.writeData(radioPacket);

            // If a tune command with a tuneInfo object was received, it is a direct tune.
            // Set the timer, as direction tunes require more time to lock
            if (command == RadioCommand.TUNE && data instanceof TuneInfo) {
                this.mPreviousTuneTime = SystemClock.elapsedRealtime();
            }

            // Always sleep between commands
            try {
                Thread.sleep(POST_COMMAND_DELAY);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Invalid Radio Packet, cannot send");
        }
    }

    private class SetSubchannelRunnable implements Runnable {

        private int mRequestedSubchannel = 0 ;
        private int mRequestCount = 0;

        SetSubchannelRunnable() {}

        public void setSubchannel(int subchannel) {
            this.mRequestedSubchannel = subchannel;
            this.mRequestCount = 0;
            HDRadio.this.mControlHandler.post(this);
        }


        @Override
        public void run() {
            if (this.mRequestedSubchannel > 0 &&
                    this.mRequestedSubchannel != HDRadio.this.mRadioValues.mHdSubchannel.get()) {
                HDRadio.this.sendRadioCommand(RadioCommand.HD_SUBCHANNEL, RadioOperation.SET, mRequestedSubchannel);
                mRequestCount++;

                // Send a new request every .5 seconds, up to 10 times
                if (mRequestCount < 10) {
                    HDRadio.this.mControlHandler.postDelayed(this, 500);
                }
            }
        }
    }

}
