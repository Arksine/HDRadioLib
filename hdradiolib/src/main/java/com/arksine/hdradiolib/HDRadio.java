package com.arksine.hdradiolib;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.support.annotation.NonNull;

import com.arksine.hdradiolib.drivers.ArduinoDriver;
import com.arksine.hdradiolib.drivers.MJSRadioDriver;
import com.arksine.hdradiolib.enums.PowerStatus;
import com.arksine.hdradiolib.enums.RadioBand;
import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioConstant;
import com.arksine.hdradiolib.drivers.RadioDriver;
import com.arksine.hdradiolib.enums.RadioError;
import com.arksine.hdradiolib.enums.RadioOperation;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

/**
 * Library to communicate with a DirectedHD DMHD-1000 HD Radio via USB using the MJS Gadgets
 * interface cable
 */

public class HDRadio {
    private SharedPreferences mRadioPreferences;

    public enum DriverType {MJS_DRIVER, ARDUINO_DRIVER, CUSTOM}

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
    private volatile long mPreviousPowerTime = 0;       // methods, so atomic access is a given
    private AtomicReference<PowerStatus> mPowerStatus = new AtomicReference<>(PowerStatus.POWERED_OFF);
    private AtomicBoolean mIsWaiting = new AtomicBoolean(false);
    private AtomicBoolean mSeekAll = new AtomicBoolean(true);

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

            HDRadio.this.mControlHandler.postDelayed(mRequestSignalRunnable, 800);
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
                    SeekData seekData = new SeekData(RadioConstant.UP,
                            HDRadio.this.mRadioValues.mTune.get().getBand(),
                            HDRadio.this.mSeekAll.get());
                    HDRadio.this.sendRadioCommand(RadioCommand.SEEK, RadioOperation.SET, seekData);
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
                    SeekData seekData = new SeekData(RadioConstant.DOWN,
                                    HDRadio.this.mRadioValues.mTune.get().getBand(),
                                    HDRadio.this.mSeekAll.get());
                    HDRadio.this.sendRadioCommand(RadioCommand.SEEK, RadioOperation.SET, seekData);
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

        @Override
        public boolean getMute() {
            return HDRadio.this.mRadioValues.mMute.get();
        }

        @Override
        public int getSignalStrength() {
            return HDRadio.this.mRadioValues.mSignalStrength.get();
        }

        @Override
        public TuneInfo getTune() {
            return HDRadio.this.mRadioValues.mTune.get();
        }

        @Override
        public boolean getHdActive() {
            return HDRadio.this.mRadioValues.mHdActive.get();
        }

        @Override
        public boolean getHdStreamLock() {
            return HDRadio.this.mRadioValues.mHdStreamLock.get();
        }

        @Override
        public int getHdSignalStrength() {
            return HDRadio.this.mRadioValues.mHdSignalStrength.get();
        }

        @Override
        public int getHdSubchannel() {
            return HDRadio.this.mRadioValues.mHdSubchannel.get();
        }

        @Override
        public int getHdSubchannelCount() {
            return HDRadio.this.mRadioValues.mHdSubchannelCount.get();
        }

        @Override
        public String getHdTitle() {
            return HDRadio.this.mRadioValues.mHdTitle.get();
        }

        @Override
        public String getHdArtist() {
            return HDRadio.this.mRadioValues.mHdArtist.get();
        }

        @Override
        public String getHdCallsign() {
            return HDRadio.this.mRadioValues.mHdCallsign.get();
        }

        @Override
        public String getHdStationName() {
            return HDRadio.this.mRadioValues.mHdStationName.get();
        }

        @Override
        public String getUniqueId() {
            return HDRadio.this.mRadioValues.mUniqueId.get();
        }

        @Override
        public String getApiVersion() {
            return HDRadio.this.mRadioValues.mApiVersion.get();
        }

        @Override
        public String getHardwareVersion() {
            return HDRadio.this.mRadioValues.mHwVersion.get();
        }

        @Override
        public boolean getRdsEnabled() {
            return HDRadio.this.mRadioValues.mRdsEnabled.get();
        }

        @Override
        public String getRdsGenre() {
            return HDRadio.this.mRadioValues.mRdsGenre.get();
        }

        @Override
        public String getRdsProgramService() {
            return HDRadio.this.mRadioValues.mRdsProgramService.get();
        }

        @Override
        public String getRdsRadioText() {
            return HDRadio.this.mRadioValues.mRdsRadioText.get();
        }

        @Override
        public int getVolume() {
            return HDRadio.this.mRadioValues.mVolume.get();
        }

        @Override
        public int getBass() {
            return HDRadio.this.mRadioValues.mBass.get();
        }

        @Override
        public int getTreble() {
            return HDRadio.this.mRadioValues.mTreble.get();
        }

        @Override
        public int getCompression() {
            return HDRadio.this.mRadioValues.mCompression.get();
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
                HDRadio.this.mControlHandler.postDelayed(mRequestSignalRunnable, 1000);
            }

            @Override
            public void onInitComplete() {
                // If initializing, set to Powered on, unmute, and fire event
                if (HDRadio.this.mPowerStatus.compareAndSet(PowerStatus.INITIALIZING,
                        PowerStatus.POWERED_ON)) {

                    HDRadio.this.mControlHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // release RTS (hardware mute)
                            HDRadio.this.mRadioDriver.clearRts();
                        }
                    }, 200);

                    // Dispatch power on callback
                    HDRadio.this.mEventHandler.handlePowerOnEvent();
                }
            }
        };
        this.mDataHandler = new RadioDataHandler(dataLooper, this.mEventHandler, dataCbs,
                this.mRadioValues);

        switch (dType) {
            case MJS_DRIVER:
                this.mRadioDriver = new MJSRadioDriver(mContext);
                this.mRadioDriver.initialize(mDataHandler, mDriverEvents);
                break;
            case ARDUINO_DRIVER:
                this.mRadioDriver = new ArduinoDriver(mContext);
                this.mRadioDriver.initialize(mDataHandler,mDriverEvents);
                break;
            case CUSTOM:
                Timber.i("Using custom driver");
                break;
            default:
                this.mRadioDriver = new MJSRadioDriver(mContext);
                this.mRadioDriver.initialize(mDataHandler, mDriverEvents);
                Timber.i("Invalid Driver Request, defaulting Mjs Driver");
        }

    }


    public void openById(final String deviceId) {
        if (!this.isOpen()) {
            this.mRadioDriver.openById(deviceId);
        } else {
            Timber.i("HD Radio already open.");
        }
    }

    /**
     * Open First HDRadioDevice encountered, should only be used if there is only one radio device
     */
    public void open() {
        if (!this.isOpen()) {
            this.mRadioDriver.open();
        } else {
            Timber.i("HD Radio already open.");
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

    public String getDeviceId() {
        return this.mRadioDriver.getIdentifier();
    }

    /**
     * Requests a list of devices connected to the phone/tablet that could potentially be
     * a HD Radio.  The list depends upon the driver, for example the MJS Cable will only return
     * UsbDevice types with the correct VID/PID
     *
     * @param listType      The Class representing the type of item in the arraylist.  For example,
     *                      if we expect to receive a type ArrayList<UsbDevice>, the listType
     *                      would be UsbDevice.class
     *
     * @return  An array list radio devices found by the corresponding driver
     */
    public <T> ArrayList<T> getDeviceList(Class<T> listType) {
        return this.mRadioDriver.getDeviceList(listType);
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
                Timber.e("Device not open, cannot power on");
                return;
            }

            long powerDelay = (this.mPreviousPowerTime + POWER_TOGGLE_DELAY)
                    - SystemClock.elapsedRealtime();
            if (powerDelay > 0) {
                // sleep
                try {
                    Thread.sleep(powerDelay);
                    Timber.d("Power delay, slept for: %d", powerDelay);
                } catch (InterruptedException e) {
                   Timber.w(e);
                }
            }

            if (this.mPowerStatus.compareAndSet(PowerStatus.POWERED_OFF, PowerStatus.POWERING_ON)) {

                // Set the hardware mute so speakers dont get blown by the initial power on
                this.mRadioDriver.raiseRts();

                // Raise DTR to power on
                this.mRadioDriver.raiseDtr();

                // Wait until the radio gives a power on response, with a 10 second timeout
                boolean timedOut = false;
                synchronized (this) {
                    try {
                        this.mIsWaiting.set(true);
                        wait(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        // Timeout was met, exit
                        if (this.mIsWaiting.compareAndSet(true, false)) {
                            this.mEventHandler.handleDeviceErrorEvent(RadioError.POWER_ERROR);
                            this.mPowerStatus.set(PowerStatus.POWERED_OFF);
                            timedOut = true;
                        }
                    }
                }

                if (!timedOut) {
                    this.initializeRadio();
                }

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

        // sleep for 200ms after receiving power on confirmation
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Timber.w(e);
        }

        // Retreived persistent values
        this.mSeekAll.set(this.mRadioPreferences.getBoolean("radiolib_pref_key_seekall", true));
        int frequency = this.mRadioPreferences.getInt("radiolib_pref_key_frequency", 879);
        RadioBand band = RadioBand.valueOf(this.mRadioPreferences
                .getString("radiolib_pref_key_band", "FM"));
        int subch = this.mRadioPreferences.getInt("radiolib_pref_key_subchannel", 0);
        TuneInfo savedTune = new TuneInfo(band, frequency, subch);
        int volume = this.mRadioPreferences.getInt("radiolib_pref_key_volume", 50);
        int bass = this.mRadioPreferences.getInt("radiolib_pref_key_bass", 10);
        int treble = this.mRadioPreferences.getInt("radiolib_pref_key_treble", 10);

        // TODO: Temporarily turn off RF Modulator
        this.mControlHandler.post(new Runnable() {
            @Override
            public void run() {
                HDRadio.this.sendRadioCommand(RadioCommand.RF_MODULATOR, RadioOperation.SET, 881);
            }
        });

        this.mController.tune(savedTune);
        this.mController.setVolume(volume);
        this.mController.setBass(bass);
        this.mController.setTreble(treble);

        if (BuildConfig.DEBUG) {
            this.mController.requestUpdate(RadioCommand.HD_ENABLE_HD_TUNER);
            this.mController.requestUpdate(RadioCommand.COMPRESSION);
            this.mController.requestUpdate(RadioCommand.RF_MODULATOR);
        }
        this.mController.requestUpdate(RadioCommand.HD_UNIQUE_ID);
        this.mController.requestUpdate(RadioCommand.HD_HW_VERSION);
        this.mController.requestUpdate(RadioCommand.HD_API_VERSION);
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
                    Timber.v("Power delay, slept for: %d", powerDelay);
                } catch (InterruptedException e) {
                    Timber.w(e);
                }
            }

            if (this.isOpen() && this.mPowerStatus.compareAndSet(PowerStatus.POWERED_ON,
                    PowerStatus.POWERING_OFF)) {

                // Remove potential pending callbacks
                this.mControlHandler.removeCallbacks(mRequestSignalRunnable);
                this.mControlHandler.removeCallbacks(mSetSubchannelRunnable);

                // mute before power off
                this.mRadioDriver.raiseRts();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Timber.w(e);
                }

                this.mRadioDriver.clearDtr();   // DTR off = Power off
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Timber.w(e);
                }

                this.mRadioDriver.clearRts();

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
        byte[] radioPacket = RadioPacketBuilder.buildRadioPacket(command, operation, data);
        if (radioPacket != null && this.mRadioDriver.isOpen()) {

            this.mRadioDriver.writeData(radioPacket);

            // Always sleep between commands
            try {
                Thread.sleep(POST_COMMAND_DELAY);
            } catch (InterruptedException e) {
                Timber.w(e);
            }
        } else {
            Timber.i("Invalid Radio Packet, cannot send");
        }
    }

    private class SetSubchannelRunnable implements Runnable {

        private int mRequestedSubchannel = 0 ;
        private int mRequestCount = 0;

        SetSubchannelRunnable() {}

        void setSubchannel(int subchannel) {
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
