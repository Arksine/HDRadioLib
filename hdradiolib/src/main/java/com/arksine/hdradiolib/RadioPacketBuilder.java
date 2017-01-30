package com.arksine.hdradiolib;

import android.support.annotation.NonNull;
import android.util.Log;

import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioConstant;
import com.arksine.hdradiolib.enums.RadioOperation;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Eric on 12/28/2016.
 */

class RadioPacketBuilder {
    private static final String TAG = RadioPacketBuilder.class.getSimpleName();
    private static final boolean DEBUG = true;

    private RadioPacketBuilder() {}

    public static byte[] buildRadioPacket(RadioCommand command, RadioOperation op) {
        return buildRadioPacket(command, op, null, null);
    }

    public static byte[] buildRadioPacket(RadioCommand command, RadioOperation op, Object data) {
        return buildRadioPacket(command, op, data, null);
    }

    public static byte[] buildRadioPacket(RadioCommand command, RadioOperation op, Object data,
                                          Boolean seekAll) {


        byte[] dataPacket;
        byte lengthByte;
        byte checkByte;

        switch (op) {
            case GET:
                dataPacket = buildRequestPacket(command);
                break;
            case SET:
                dataPacket = buildSetPacket(command, data, seekAll);
                break;
            default:
                Log.i(TAG, "Invalid operation, must be get or set");
                return null;
        }

        if (dataPacket == null) {
            return null;
        }

        // TODO: The code below could be done in the write function of MjsRadioDriver, since that is essentially an output
        //       stream as well.  Would be faster, as I would only have to write to one steam instead of two.

        // The built packet must be parsed and added so the checksum can be calculated
        // and appended, along with any necessary bytes escaped
        ByteArrayOutputStream bufferOut = new ByteArrayOutputStream(dataPacket.length + 3);
        bufferOut.write((byte)0xA4);  // header byte

        // Write the length byte, escape it if necessary
        lengthByte = (byte) (dataPacket.length & 0xFF);
        switch (lengthByte) {
            case (byte) 0x1B:
                bufferOut.write((byte) 0x1B);
                bufferOut.write(lengthByte);
                break;
            case (byte) 0xA4:
                bufferOut.write((byte) 0x1B);
                bufferOut.write((byte) 0x48);
                break;
            default:
                bufferOut.write(lengthByte);
                break;
        }

        // write the packet data, escape if necessary
        int checksum = 0xA4 + (lengthByte & 0xFF);
        for (byte b : dataPacket) {
            checksum += (b & 0xFF);
            switch (b) {
                case (byte) 0x1B:
                    bufferOut.write((byte) 0x1B);
                    bufferOut.write(b);
                    break;
                case (byte) 0xA4:
                    bufferOut.write((byte) 0x1B);
                    bufferOut.write((byte) 0x48);
                    break;
                default:
                    bufferOut.write(b);
                    break;
            }
        }

        // Write the checksum, escape if necessary
        checksum = checksum % 256;
        checkByte = (byte)(checksum & 0xFF);
        switch (checkByte) {
            case (byte) 0x1B:
                bufferOut.write((byte) 0x1B);
                bufferOut.write(checkByte);
                break;
            case (byte) 0xA4:
                bufferOut.write((byte) 0x1B);
                bufferOut.write((byte) 0x48);
                break;
            default:
                bufferOut.write(checkByte);
                break;
        }

        if (DEBUG)
            Log.i(TAG, "Hex Bytes Sent:\n" + bytesToHexString(bufferOut.toByteArray()));

        return bufferOut.toByteArray();
    }

    private static byte[] buildRequestPacket(@NonNull RadioCommand command) {
        ByteBuffer dataBuf = ByteBuffer.allocate(4);
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);

        byte[] commandCode = command.getBytes();
        byte[] operationCode = RadioOperation.GET.getBytes();

        if (commandCode == null || operationCode == null) {
            Log.w(TAG, "Invalid command or operation key, cannot build packet");
            return null;
        }

        dataBuf.put(commandCode);
        dataBuf.put(operationCode);
        return dataBuf.array();
    }

    private static byte[] buildSetPacket(@NonNull RadioCommand command, Object data,
                                         Boolean seekAll) {

        ByteBuffer dataBuf = ByteBuffer.allocate(256);  // 256 is absolute maximum length of packet
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);

        byte[] commandCode = command.getBytes();
        byte[] operationCode = RadioOperation.SET.getBytes();

        if (commandCode == null || operationCode == null) {
            Log.w(TAG, "Invalid command or operation key, cannot build packet");
            return null;
        }

        dataBuf.put(commandCode);
        dataBuf.put(operationCode);

        switch (command) {
            case POWER:           // set boolean item
            case MUTE:
                if (!(data instanceof Boolean)) {
                    Log.i(TAG, "Invalid boolean data received for command: " + command);
                    return null;
                }

                boolean statusOn = (boolean) data;
                if (statusOn) {
                    dataBuf.put(RadioConstant.ONE.getBytes());
                } else {
                    dataBuf.put(RadioConstant.ZERO.getBytes());
                }
                break;

            case VOLUME:          // set integer item
            case BASS:
            case TREBLE:
                // TODO: The commented out code below was an attempt to set integer based commands
                // up or down, the way tune or seek is done.  It may be possible, but if so I don't have the
                // correct constant.  It appears any value outside of the range 1-90 is automatically
                // zero

                //       Will attempt to reverse engineer the Radio controller in the future
                /*if (data instanceof RadioConstant) {
                    // Command is to tune up or down
                    RadioConstant direction = (RadioConstant)data;
                    if (!(direction == RadioConstant.UP || direction == RadioConstant.DOWN)) {
                        Log.i(TAG, "Direction is not valid for tune command");
                        return null;
                    }
                    dataBuf.put(RadioConstant.SEEK_REQUEST.getBytes());     // try seek request
                    dataBuf.put(RadioConstant.ZERO.getBytes());
                    dataBuf.put(direction.getBytes());
                    break;
                }*/
            case HD_SUBCHANNEL:
                if (!(data instanceof Integer)) {
                    Log.i(TAG, "Invalid integer data received for command: " + command);
                    return null;
                }

                int val = (int) data;

                if (DEBUG)
                    Log.v(TAG, "Setting value for " + command + ": " + val);

                // Check to see if integer value is outside of range
                if (val > 90) {
                    val = 90;
                } else if (val < 0) {
                    val = 0;
                }

                dataBuf.putInt(val);
                break;
            case COMPRESSION:
                // TODO: Not sure what kind of item is.  Linux app says its an integer, but it doesn't seem
                //      to respond to integer settings.  It may not be possible to set it.
                break;
            case TUNE:
                if (data instanceof RadioConstant) {
                    // Command is to tune up or down
                    RadioConstant direction = (RadioConstant)data;
                    if (!(direction == RadioConstant.UP || direction == RadioConstant.DOWN)) {
                        Log.i(TAG, "Direction is not valid for tune command");
                        return null;
                    }
                    dataBuf.put(RadioConstant.ZERO.getBytes());     // pad with 8 zero bytes
                    dataBuf.put(RadioConstant.ZERO.getBytes());
                    dataBuf.put(direction.getBytes());
                }
                else if (data instanceof TuneInfo) {
                    // Command is to tune directly to a station
                    TuneInfo info = (TuneInfo)data;
                    byte[] bandBytes = info.getBand().getBytes();
                    if (bandBytes == null) {
                        Log.i(TAG, "Band is not valid for tune command");
                        return null;
                    }
                    dataBuf.put(bandBytes);
                    dataBuf.putInt(info.getFrequency());
                    dataBuf.put(RadioConstant.ZERO.getBytes()); // pad end with 4 zero bytes

                } else {
                    // The data is incorrect
                    Log.i(TAG, "Data is not valid for tune command");
                    return null;
                }

                break;
            case SEEK:
                if (!(data instanceof RadioConstant)) {
                    // Seek must be a constant, up or down
                    Log.i(TAG, "Invalid data received for command: " + command);
                    return null;
                } else if (seekAll == null) {
                    Log.i(TAG, "SeekAll not set for seek command, cannot build packet");
                    return null;
                }

                RadioConstant seekDir = (RadioConstant)data;
                if (!(seekDir == RadioConstant.UP || seekDir == RadioConstant.DOWN)) {
                    Log.i(TAG, "Direction is not valid for tune command");
                    return null;
                }
                dataBuf.put(RadioConstant.SEEK_REQUEST.getBytes());
                dataBuf.put(RadioConstant.ZERO.getBytes());
                dataBuf.put(seekDir.getBytes());

                if (seekAll) {
                    // Seek all stations
                    dataBuf.put(RadioConstant.ZERO.getBytes());
                } else {
                    // Seek only HD stations
                    dataBuf.put(RadioConstant.ONE.getBytes());
                }

                dataBuf.put(RadioConstant.ZERO.getBytes());
                break;
            default:
                Log.i(TAG, "Invalid command, cannot set: " + command);
                return null;
        }

        dataBuf.flip();
        byte[] returnBytes = new byte[dataBuf.limit()];
        dataBuf.get(returnBytes);

        return returnBytes;

    }

    /**
     * Utility function for debug output, converts bytes into a string hex representation
     *
     * @param bytes     array bytes to convert
     * @return          String of bytes represented as hex
     */
    public static String bytesToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = HEXARRAY[v >>> 4];
            hexChars[j * 3 + 1] = HEXARRAY[v & 0x0F];
            if (j > 0 && j % 14 == 0 ) {
                // newline every 15 bytes (15th byte is 14th index)
                hexChars[j * 3 + 2] = '\n';
            } else {
                hexChars[j * 3 + 2] = ' ';
            }
        }
        return new String(hexChars);
    }
    final private static char[] HEXARRAY = "0123456789ABCDEF".toCharArray();
}
