package gridwatch.plugwatch.wit;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.widget.Toast;

import com.evernote.android.job.JobRequest;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleScanResult;
import com.polidea.rxandroidble.exceptions.BleScanException;
import com.polidea.rxandroidble.utils.ConnectionSharingAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import gridwatch.plugwatch.PlugWatchApp;
import gridwatch.plugwatch.configs.BluetoothConfig;
import gridwatch.plugwatch.configs.SensorConfig;
import gridwatch.plugwatch.database.MeasurementRealm;
import gridwatch.plugwatch.network.WitJob;
import gridwatch.plugwatch.network.WitRetrofit;
import gridwatch.plugwatch.utilities.PhoneIDWriter;
import gridwatch.plugwatch.utilities.Restart;
import io.realm.Realm;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

/**
 * Created by nklugman on 11/24/16.
 */

public class WitConnector {


    private static WitConnector mInstance = null;

    Realm realm;


    private boolean isConnected;
    private String macAddress;

    long last_good_data = System.currentTimeMillis();
    private boolean isRooted;

    private Context mContext;
    private RxBleClient rxBleClient;
    private Subscription scanSubscription;
    private RxBleDevice bleDevice;
    ArrayList<byte[]> to_write_slowly = new ArrayList<>();
    private String mCurrent;
    private String mFrequency;
    private String mPower;
    private String mPowerFactor;
    private String mVoltage;
    private PublishSubject<Void> disconnectTriggerSubject = PublishSubject.create();
    private Observable<RxBleConnection> connectionObservable;

    public static WitConnector getInstance() {
        return mInstance;
    }


    public WitConnector() {

    }

    public void start(Context r) {
        kill();
        mContext = r;
        rxBleClient = PlugWatchApp.getRxBleClient(r);
        realm = Realm.getDefaultInstance();
        start_scanning();
    }

    public void kill() {
        to_write_slowly = new ArrayList<>();
        bleDevice = null;
        scanSubscription = null;
        rxBleClient = null;
        //rxBleConnection
        if (connectionObservable != null) {
            connectionObservable
                    .flatMap(rxBleConnection -> rxBleConnection.setupNotification(BluetoothConfig.UUID_WIT_FFE1))
                    .doOnNext(new Action1<Observable<byte[]>>() {
                        @Override
                        public void call(Observable<byte[]> observable) {
                            notificationHasBeenSetUp();
                        }
                    })
                    .flatMap(notificationObservable -> notificationObservable)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::onNotificationReceivedFFE1, this::onNotificationSetupFailure).unsubscribe();
        }
    }

    private void start_scanning() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            BluetoothAdapter.getDefaultAdapter().enable();
        }
        scanSubscription = rxBleClient.scanBleDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnUnsubscribe(this::clearSubscription)
                .subscribe(this::addScanResult, this::onScanFailure);
    }

    private void getWiTenergy() {
        Log.e("getWiTenergy", "hit");
        connectionObservable
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(BluetoothConfig.UUID_WIT_FFE1))
                .doOnNext(new Action1<Observable<byte[]>>() {
                    @Override
                    public void call(Observable<byte[]> observable) {
                        notificationHasBeenSetUp();
                    }
                })
                .flatMap(notificationObservable -> notificationObservable)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        isConnected = false;
                        Log.e("ble on terminate", "restarting scanning");
                        //start_scanning();
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.e("ble disconnected", throwable.getMessage());
                        isConnected = false;
                        //start_scanning();
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        isConnected = false;
                        //Log.e("ble on unsubscribe", "restarting scanning");
                        //start_scanning();
                    }
                })
                .subscribe(this::onNotificationReceivedFFE1, this::onNotificationSetupFailure);
    }

    private void addScanResult(RxBleScanResult bleScanResult) {
        //Log.e("Scan name", bleScanResult.getBleDevice().getName());
        if (bleScanResult.getBleDevice().getName().contains("Smart")) {
            bleDevice = bleScanResult.getBleDevice();

            connectionObservable = bleDevice
                    .establishConnection(mContext, false)
                    .takeUntil(disconnectTriggerSubject)
                    .doOnUnsubscribe(this::clearSubscription)
                    .compose(new ConnectionSharingAdapter());

            macAddress = bleDevice.getMacAddress();
            if (!isConnected) {
                getWiTenergy();
            }
            scanSubscription.unsubscribe();
        }
    }
    private void write_command(UUID charac, byte[] data, int length) {
        final byte[] bArr = Arrays.copyOf(data, length);
        to_write_slowly.add(bArr);
    }

    private void onNotificationSetupFailure(Throwable throwable) {
    }

    private void notificationHasBeenSetUp() {
    }

    private void clearSubscription() {
        scanSubscription = null;
    }


    private void onScanFailure(Throwable throwable) {
        if (throwable instanceof BleScanException) {
            handleBleScanException((BleScanException) throwable);
        }
    }

    private void handleBleScanException(BleScanException bleScanException) {
        switch (bleScanException.getReason()) {
            case BleScanException.BLUETOOTH_NOT_AVAILABLE:
                Toast.makeText(mContext, "Bluetooth is not available", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.BLUETOOTH_DISABLED:
                Toast.makeText(mContext, "Enable bluetooth and try again", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.LOCATION_PERMISSION_MISSING:
                Toast.makeText(mContext,
                        "On Android 6.0 location permission is required. Implement Runtime Permissions", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.LOCATION_SERVICES_DISABLED:
                Toast.makeText(mContext, "Location services needs to be enabled on Android 6.0", Toast.LENGTH_SHORT).show();
                break;
            case BleScanException.BLUETOOTH_CANNOT_START:
            default:
                Toast.makeText(mContext, "Unable to start scanning", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private static final int COUNT_DOWN_TIMER = 1;
    private static final int DEVICESETTING = 5;
    private static final int GATT_TIMEOUT = 1000;
    private static final int OVERLOAD = 2;
    private static final int SCHEDULER = 4;
    private static final int STANDBY = 3;
    private static final int PREF_ACT_REQ = 0;
    private static final int ID_ACC = 1;
    private static final int ID_AMB = 5;
    private static final int ID_BAR = 7;
    private static final int ID_GYR = 3;
    private static final int ID_OBJ = 4;
    private static final int ID_OFFSET = 0;
    private static final int ID_OPT = 2;
    private boolean isClockUpdated = false;

    private String decode_energyData(byte[] data, int index) {
        if (data == null) {
            return "0.0";
        }
        double value = ((((double) (((data[index + ID_ACC] >> ID_OBJ) & 15) * GATT_TIMEOUT)) + ((double) ((data[index + ID_ACC] & 15) * 100))) + ((double) (((data[index + ID_OPT] >> ID_OBJ) & 15) * 10))) + ((double) (data[index + ID_OPT] & 15));
        Object[] objArr;
        switch (data[index]) {
            case ID_ACC /*1*/:
                objArr = new Object[ID_ACC];
                objArr[ID_OFFSET] = Double.valueOf(value / 1000.0d);
                return String.format(Locale.US, "%4.3f", objArr);
            case ID_OPT /*2*/:
                objArr = new Object[ID_ACC];
                objArr[ID_OFFSET] = Double.valueOf(value / 100.0d);
                return String.format(Locale.US, "%4.2f", objArr);
            case ID_GYR /*3*/:
                objArr = new Object[ID_ACC];
                objArr[ID_OFFSET] = Double.valueOf(value / 10.0d);
                return String.format(Locale.US, "%4.1f", objArr);
            case ID_OBJ /*4*/:
                objArr = new Object[ID_ACC];
                objArr[ID_OFFSET] = Double.valueOf(value);
                return String.format(Locale.US, "%4.1f", objArr);
            case ID_AMB /*5*/:
                objArr = new Object[ID_ACC];
                objArr[ID_OFFSET] = Double.valueOf(value / 1000.0d);
                return String.format(Locale.US, "%4.2f", objArr);
            default:
                return "0.0";
        }
    }

    private void onNotificationReceivedFFE1(byte[] value) {
        //There is a state where notifications are coming in but they are not good. This state requires app reboot
        if (isClockUpdated && to_write_slowly.size() == 0) { //don't do this if we are still setting up the connection with writes
            if (System.currentTimeMillis() - last_good_data > SensorConfig.NOTIFICATION_BUT_NO_DECODE_TIMEOUT) {
                Log.e("connection timeout", String.valueOf(System.currentTimeMillis() - last_good_data));
                Restart r = new Restart();
                r.do_restart(mContext, PlugWatchApp.class, new Throwable("Restart due to notification but no decode")); //figure out why this sometimes launches many services
            }
        }

        Log.e("notification", "FFE1");
        if (!isClockUpdated) {
            int i;
            byte[] data = new byte[10];
            Calendar now = Calendar.getInstance();
            int year = now.get(COUNT_DOWN_TIMER);
            int month = now.get(OVERLOAD);
            int day = now.get(DEVICESETTING);
            int hour = now.get(Calendar.HOUR_OF_DAY);
            int minute = now.get(Calendar.MINUTE);
            int second = now.get(Calendar.SECOND);
            data[PREF_ACT_REQ] = (byte) 3;
            data[COUNT_DOWN_TIMER] = (byte) (year & MotionEventCompat.ACTION_MASK);
            data[OVERLOAD] = (byte) ((year >> 8) & MotionEventCompat.ACTION_MASK);
            data[STANDBY] = (byte) ((month + COUNT_DOWN_TIMER) & MotionEventCompat.ACTION_MASK);
            data[SCHEDULER] = (byte) (day & MotionEventCompat.ACTION_MASK);
            data[DEVICESETTING] = (byte) (hour & MotionEventCompat.ACTION_MASK);
            data[6] = (byte) (minute & MotionEventCompat.ACTION_MASK);
            data[7] = (byte) (second & MotionEventCompat.ACTION_MASK);
            int encryptKey = getEncryptKey();
            data[8] = (byte) (encryptKey & MotionEventCompat.ACTION_MASK);
            data[9] = (byte) ((encryptKey >> 8) & MotionEventCompat.ACTION_MASK);
            write_command(BluetoothConfig.UUID_WIT_FFE3, data, 10);
            data[PREF_ACT_REQ] = (byte) 20;
            write_command(BluetoothConfig.UUID_WIT_FFE3, data, COUNT_DOWN_TIMER);
            data[PREF_ACT_REQ] = (byte) 22;
            write_command(BluetoothConfig.UUID_WIT_FFE3, data, COUNT_DOWN_TIMER);
            data[PREF_ACT_REQ] = (byte) 23;
            write_command(BluetoothConfig.UUID_WIT_FFE3, data, COUNT_DOWN_TIMER);
            data[PREF_ACT_REQ] = (byte) 24;
            write_command(BluetoothConfig.UUID_WIT_FFE3, data, COUNT_DOWN_TIMER);
            for (i = PREF_ACT_REQ; i < 6; i += COUNT_DOWN_TIMER) {
                data[PREF_ACT_REQ] = (byte) 14;
                data[COUNT_DOWN_TIMER] = (byte) i;
                data[OVERLOAD] = (byte) 0;
                data[STANDBY] = (byte) 5;
                write_command(BluetoothConfig.UUID_WIT_FFE3, data, SCHEDULER);
            }
            isClockUpdated = true;
            int length = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (length > 0) {
                data[PREF_ACT_REQ] = (byte) 1;
                data[OVERLOAD] = (byte) 0;
                i = PREF_ACT_REQ;
                while (i < 24 && length > 0) {
                    int i2;
                    data[COUNT_DOWN_TIMER] = (byte) i;
                    if (length > 8) {
                        i2 = 8;
                    } else {
                        i2 = length;
                    }
                    data[STANDBY] = (byte) i2;
                    write_command(BluetoothConfig.UUID_WIT_FFE3, data, SCHEDULER);
                    i += 8;
                    length -= 8;
                }
            }
        } else {
            if (to_write_slowly.size() != 0) {
                connectionObservable
                        .flatMap(rxBleConnection -> rxBleConnection.writeCharacteristic(BluetoothConfig.UUID_WIT_FFE3, to_write_slowly.remove(0)))
                        .observeOn(AndroidSchedulers.mainThread())
                        .delay(GATT_TIMEOUT, TimeUnit.MILLISECONDS)
                        .buffer(GATT_TIMEOUT, TimeUnit.MILLISECONDS)
                        .subscribe(bytes -> {
                            onWriteSuccess();
                        }, this::onWriteFailure);
            } else {
                good_data(value);
            }
        }
    }

    private void good_data(byte[] value) {
        isConnected = true;
        last_good_data = System.currentTimeMillis();
        mVoltage = (decode_energyData(value, ID_ACC));
        mCurrent = (decode_energyData(value, ID_OBJ));
        mPower = decode_energyData(value, ID_BAR);
        mPowerFactor = (decode_energyData(value, 10));
        mFrequency = (decode_energyData(value, 13));
        long time = System.currentTimeMillis();
        Log.d("MEASUREMENT: voltage", mVoltage);
        Log.d("MEASUREMENT: current", mCurrent);
        Log.d("MEASUREMENT: power", mPower);
        Log.d("MEASUREMENT: pf", mPowerFactor);
        Log.d("MEASUREMENT: frequency", mFrequency);
        Log.d("MEASUREMENT: time", String.valueOf(time));
        //PlugWatchApp.getInstance().increment_last_time();

        /*
        PlugWatchApp.getInstance().set_last_time(System.currentTimeMillis());
        SharedPreferences settings = mContext.getSharedPreferences(SettingsConfig.SETTINGS_META_DATA, 0);
        settings.edit().putLong(SettingsConfig.LAST_WIT, System.currentTimeMillis()).commit();
        PlugWatchApp.getInstance().set_is_connected(true);
        */

        realm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm bgRealm) {
                try {
                    //bgRealm.setAutoRefresh(true);
                    MeasurementRealm cur = new MeasurementRealm(mCurrent, mFrequency,
                            mPower, mPowerFactor, mVoltage);

                    bgRealm.copyToRealm(cur);
                    PhoneIDWriter b = new PhoneIDWriter(mContext);
                    String phone_id = b.get_last_value();
                    WitRetrofit a = new WitRetrofit(mCurrent, mFrequency, mPower, mPowerFactor,
                            mVoltage, System.currentTimeMillis(), -1, -1, phone_id,
                            -1, "");
                    int jobId = new JobRequest.Builder(WitJob.TAG)
                            .setExecutionWindow(1_000L, 20_000L)
                            .setBackoffCriteria(5_000L, JobRequest.BackoffPolicy.EXPONENTIAL)
                            .setRequiresCharging(false)
                            .setExtras(a.toBundle())
                            .setRequiresDeviceIdle(false)
                            .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                            .setPersisted(true)
                            .build()
                            .schedule();

                } catch (android.database.sqlite.SQLiteConstraintException e) {
                    Log.e("error", e.getMessage());
                }
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {

                Log.e("REALM", "new size is: " + String.valueOf(realm.where(MeasurementRealm.class).findAll().size()));
                //WitEnergyVersionTwo.getInstance().num_wit = wit_db.size();
                //WitEnergyVersionTwo.getInstance().last_time = new Date();
            }
        });

    }


    private int getEncryptKey() {
        int i;
        byte[] KEY = new byte[]{(byte) 105, (byte) 76, (byte) 111, (byte) 103, (byte) 105, (byte) 99};
        byte[] MAC = new byte[6];
        byte[] address = bleDevice.getMacAddress().getBytes().clone();
        int encryptKey = PREF_ACT_REQ;
        int j = 15;
        for (i = PREF_ACT_REQ; i < 6; i += COUNT_DOWN_TIMER) {
            if (address[j] <= 57) {
                MAC[i] = (byte) (address[j] - 48);
            } else {
                MAC[i] = (byte) ((address[j] - 65) + 10);
            }
            MAC[i] = (byte) (MAC[i] << SCHEDULER);
            if (address[j + COUNT_DOWN_TIMER] <= 57) {
                MAC[i] = (byte) (MAC[i] + ((byte) ((address[j + COUNT_DOWN_TIMER] - 48) & 15)));
            } else {
                MAC[i] = (byte) (MAC[i] + ((byte) (((address[j + COUNT_DOWN_TIMER] - 65) + 10) & 15)));
            }
            j -= 3;
        }
        for (i = PREF_ACT_REQ; i < 6; i += COUNT_DOWN_TIMER) {
            encryptKey += (MAC[i] ^ KEY[i]) & MotionEventCompat.ACTION_MASK;
        }
        StringBuilder append = new StringBuilder().append("EncryptKey = ");
        Object[] objArr = new Object[COUNT_DOWN_TIMER];
        objArr[PREF_ACT_REQ] = Integer.valueOf(encryptKey);
        Log.i("encrypt key", append.append(String.format("%x", objArr)).toString());
        Log.i("encrypt key", "MAC =" + String.format("%x:%x:%x:%x:%x:%x", new Object[]{Byte.valueOf(MAC[PREF_ACT_REQ]), Byte.valueOf(MAC[COUNT_DOWN_TIMER]), Byte.valueOf(MAC[OVERLOAD]), Byte.valueOf(MAC[STANDBY]), Byte.valueOf(MAC[SCHEDULER]), Byte.valueOf(MAC[DEVICESETTING])}));
        return encryptKey;
    }

    private void onWriteSuccess() {
        isConnected = true;
        //PlugWatchApp.getInstance().set_is_connected(true);
        clearSubscription();
    }

    private void onWriteFailure(Throwable throwable) {
        isConnected = false;
        //PlugWatchApp.getInstance().set_is_connected(false);
    }
}