package com.arksine.hdradiolib;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import com.arksine.hdradiolib.enums.RadioBand;
import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioOperation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Receives bytes of data from the HD Radio and parses it.
 */

class RadioDataHandler extends Handler {
    private static final String TAG = RadioDataHandler.class.getSimpleName();
    private static final boolean DEBUG = true;


    // Packet parsing vars
    private ByteBuffer mDataBuffer = ByteBuffer.allocate(256);
    private int mPacketLength = 0;
    private int mPacketCheckSum = 0;
    private boolean mIsEscaped = false;
    private boolean mIsLengthByte = false;
    private boolean mPacketStarted = false;

    // Tracking variables necessary to implement some of the Radio Interface functionality.  They
    // Will be accessed from multiple threads, so they need to be volatile
    private volatile int volume = 0;
    private volatile int bass = 0;
    private volatile int treble = 0;
    private volatile int subchannel = 0;

    private Handler mCallbackHandler;

    /**
     * The interface below is a callback for the main HDRadio class, notifying it that
     * a power on reply was recieved.  This is necessary to correctly time commands after power
     * on.
     */
    interface PowerNotifyCallback {
        void onPowerOnReceived();
    }
    PowerNotifyCallback powerCb;


    RadioDataHandler(@NonNull Looper looper, @NonNull Handler cbHandler,
                     @NonNull PowerNotifyCallback pcb) {
        super(looper);
        this.mCallbackHandler = cbHandler;
        this.powerCb = pcb;
    }

    /**
     * Retreives variables necessary to implement RadioController functionality.  For example,
     * to implement volumeUp, I need to know what the current volume level is.  This class is
     * syncrhonized to prevent threads from simultaneously reading and writing to variables.
     *
     * @param command   The requested value's related command
     * @return          The value requested
     */
    public synchronized int getTrackingVariable(@NonNull RadioCommand command) {
        switch (command) {
            case VOLUME:
                return this.volume;
            case BASS:
                return this.bass;
            case TREBLE:
                return this.treble;
            case HD_SUBCHANNEL:
                return this.subchannel;
            default:
                Log.e(TAG, "Variable not tracked: " + command.toString());
                return -1;
        }
    }

    @Override
    public void handleMessage(Message msg) {
        parseIncomingBytes((byte[])msg.obj);
    }

    private void parseIncomingBytes(byte[] incomingBytes) {
        if (DEBUG)
            Log.v(TAG, "Incoming Radio Bytes:\n" + RadioPacketBuilder.bytesToHexString(incomingBytes));

        /**
         * The following is known the following about radio packets:
         * 1st byte is 0xA4, which is the header.
         * 2nd byte is length
         * 0x1B is escape byte, when active 0x1B is escaped as 1B, 0x48 is escaped as 0xA4.  I'm
         * operating under the assumption that all bytes outside of the header are escaped, including
         * the length and checksum
         *
         * The checksum is calculated as the sum of all bytes received (outside of the checksum itself) mod 256
         */

        for (byte b : incomingBytes) {
            if ((b == (byte)0xA4)) {
                // Header received, start new packet

                if (this.mPacketStarted) {
                    Log.v(TAG, "New header received during previous packet, discarding current packet");
                }

                // Start byte is received and it isn't the length byte or the checksum
                this.mPacketLength = 0;
                this.mDataBuffer.clear();
                this.mPacketStarted = true;
                this.mIsLengthByte = true;
                this.mPacketCheckSum = (b & 0xFF);
                this.mIsEscaped = false;   // just in case a header is read directly after escape byte
            } else if(!this.mPacketStarted) {
                Log.v(TAG, "Byte received without a start header, discarding");
            } else if (b == (byte)0x1B && !this.mIsEscaped) {
                // Escape byte received
                this.mIsEscaped = true;
            } else {

                if (this.mIsEscaped) {
                    if (DEBUG)
                        Log.v(TAG, "Escaped char: " + String.format("%02X", b));

                    if (b == (byte)0x48) {
                        // 0x48 is escaped as 0xA4
                        b = (byte) 0xA4;
                    }
                    this.mIsEscaped = false;
                    // Note: 0x1B is escaped as 0x1B, so we don't need to reset the current byte
                }

                if (this.mIsLengthByte) {
                    // Length Byte received
                    this.mIsLengthByte = false;
                    this.mPacketLength = (b & 0xFF);
                    this.mPacketCheckSum += mPacketLength;

                    if (this.mPacketLength == 0) {
                        // Received a header with an empty packet, not sure what to do
                        Log.wtf(TAG, "Packet length received is zero, discard packet");
                        this.mPacketStarted = false;
                    }
                } else if (this.mDataBuffer.position() == this.mPacketLength) {
                    // Checksum byte received

                    if ((this.mPacketCheckSum % 256) == (b & 0xFF)) {
                        // Checksum is valid

                        byte[] data = new byte[this.mPacketLength];
                        this.mDataBuffer.flip();
                        this.mDataBuffer.get(data);
                        this.processRadioPacket(data);
                    } else {
                        Log.v(TAG, "Invalid checksum, discarding packet");
                    }

                    // set packet to false so stray bytes that are no 0xA4 are discarded
                    this.mPacketStarted = false;

                } else {
                    // Byte is part of the data packet
                    this.mDataBuffer.put(b);
                    this.mPacketCheckSum += (b & 0xFF);
                }
            }
        }
    }

    private void processRadioPacket(byte[] radioPacket) {
        /**
         *  Radio Packet Structure:
         * - Bytes 0 and 1 are the message command(IE: tune, power, etc)
         * - Bytes 2 and 3 are the message operation (get, set, reply)
         * - Packets received from the radio should always be replies
         */

        if (DEBUG)
            Log.v(TAG, "Data packet hex:\n" + RadioPacketBuilder.bytesToHexString(radioPacket));

        ByteBuffer msgBuf = ByteBuffer.wrap(radioPacket);
        msgBuf.order(ByteOrder.LITTLE_ENDIAN);
        int messageCmd = msgBuf.getShort();
        int messageOp =  msgBuf.getShort();

        if (messageOp != RadioOperation.REPLY.getByteValueAsInt()) {
            Log.i(TAG, "Message is not a reply, discarding");
            return;
        }

        RadioCommand command = RadioCommand.getCommandFromValue(messageCmd);
        if (command == null) {
            Log.w(TAG, "Unknown command, cannot process packet");
            return;
        }


        // Process packet by data type
        RadioCommand.Type dataType = command.getType();
        Message msg = this.mCallbackHandler.obtainMessage(CallbackHandler.CALLBACK_DATA_RECEIVED);
        msg.arg1 = command.ordinal();
        switch (dataType) {
            case INT: {
                int intValue;
                intValue = msgBuf.getInt();

                if (DEBUG)
                    Log.v(TAG, command.toString() + " value: " + intValue);


                // TODO: might be better to just let calling activities handle this and
                // implement

                // Track a few values required to present user with certain operations
                switch (command) {
                    case VOLUME:
                        synchronized (this) {
                            this.volume = intValue;
                        }
                        break;
                    case BASS:
                        synchronized (this) {
                            this.bass = intValue;
                        }
                        break;
                    case TREBLE:
                        synchronized (this) {
                            this.treble = intValue;
                        }
                        break;
                    case HD_SUBCHANNEL:
                        synchronized (this) {
                            this.subchannel = intValue;
                        }
                        break;
                }

                // Dispatch callback with command and data
                msg.obj = intValue;
                this.mCallbackHandler.sendMessage(msg);
                break;
            }
            case BOOLEAN: {
                // Boolean's are received as 4 bytes, 0 is false 1 is true.
                int boolValue = msgBuf.getInt();
                boolean status;
                if (boolValue == 1) {
                    status = true;
                } else if (boolValue == 0) {
                    status = false;
                } else {
                    Log.i(TAG, "Invalid boolean value: " + boolValue);
                    return;
                }

                // Notify the HDRadio class that a Power ON reply was received
                if (command == RadioCommand.POWER && status) {
                    powerCb.onPowerOnReceived();
                }

                // Dispatch callback with command and data
                msg.obj = status;
                this.mCallbackHandler.sendMessage(msg);
                break;
            }
            case STRING: {
                // Get length of the string
                int strLength = msgBuf.getInt();

                if (strLength != msgBuf.remaining()) {
                    Log.i(TAG, "String Length received does not match remaining bytes in buffer");
                    strLength = msgBuf.remaining();
                }

                String strMsg;
                byte[] stringBytes;
                if (strLength == 0) {
                    strMsg = "";
                } else {
                    stringBytes = new byte[strLength];
                    msgBuf.get(stringBytes);
                    strMsg = new String(stringBytes);
                }

                if (DEBUG)
                    Log.d(TAG, "Length: " + strLength + "\nConverted String: \n" + strMsg);

                // Dispatch callback with command and data
                msg.obj = strMsg;
                this.mCallbackHandler.sendMessage(msg);
                break;
            }
            case TUNEINFO: {
                RadioBand band;
                int bandValue = msgBuf.getInt();     // Get band bytes
                if (bandValue == 0) {
                    band = RadioBand.AM;
                } else if (bandValue == 1) {
                    band = RadioBand.FM;
                } else {
                    Log.wtf(TAG, "Invalid bytes received for band");
                    return;
                }
                int freqency = msgBuf.getInt();    // Get frequency bytes
                TuneInfo info = new TuneInfo(band, freqency, 0);

                // Dispatch callback with command and data
                msg.obj = info;
                this.mCallbackHandler.sendMessage(msg);
                break;
            }
            case HDSONGINFO: {
                int subch = msgBuf.getInt();
                int infoLength = msgBuf.getInt();

                if (infoLength != msgBuf.remaining()) {
                    Log.w(TAG, "String Length received does not match remaining bytes in buffer");
                    infoLength = msgBuf.remaining();
                }

                String songInfo;
                byte[] stringBytes;
                if (infoLength == 0) {
                    songInfo = "";
                } else {
                    stringBytes = new byte[infoLength];
                    msgBuf.get(stringBytes);
                    songInfo = new String(stringBytes);
                }

                HDSongInfo hdSongInfo = new HDSongInfo(songInfo, subch);
                // Dispatch callback with command and data
                msg.obj = hdSongInfo;
                this.mCallbackHandler.sendMessage(msg);
                break;
            }
            default:
                Log.wtf(TAG, "Unknown type");
        }

    }
}
