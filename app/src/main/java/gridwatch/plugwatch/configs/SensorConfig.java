package gridwatch.plugwatch.configs;

/**
 * Created by nklugman on 11/19/16.
 */

public class SensorConfig {

    public final static int ACCEL_TIME = 2;
    public final static int AUDIO_TIME = 2;

    //CONFIGURE THE FFT
    public final static Boolean LOCAL_FFT_BOOL = true;
    public final static int CENTER_FREQ = 50;
    public final static int NOTCH_SIZE = 5; // 5 hz +- center freq
    public final static Boolean FIRST_HARMONIC = false;
    public final static int NUM_FFT_HIT_CNT = 5;
    public final static int FFT_SAMPLE_TIME_MS = 10000;
    public final static Boolean FFT_ON = true;

    //CONFIGURE THE AUDIO RECORDING
    public final static int MICROPHONE_SAMPLE_TIME_MS = 10000;
    public final static int MICROPHONE_SAMPLE_FREQUENCY = 44100;
    public final static byte MICROPHONE_BIT_RATE = 16;
    public final static String recordingFileTmpName = "wit_tmp.raw";
    public final static String recordingFolder = "wit_audio";
    public final static String recordingExtension = ".wav";
    public final static Boolean MICROPHONE_ON = false;

    public final static String PLUGGED = "PLUGGED";
    public final static String UNPLUGGED = "UNPLUGGED";

    public final static int CONNECTION_TIMEOUT = 10000; //interval to check connection
    public final static int CONNECTION_THRESHOLD = 10000*20; //after this number of connection checks restart the app
    public final static int REBOOT_THRESHOLD = 5; // after this threshold of restarts, reboot the phone
    public final static int NOTIFICATION_BUT_NO_DECODE_TIMEOUT = 10000 * 60; //state that exists when notifications are coming that can't be decoded

}

