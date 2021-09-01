package id.flutter.background_service;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.AlarmManagerCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.UnsatisfiedLinkError;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.loader.FlutterLoader;
import io.flutter.plugin.common.JSONMethodCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.view.FlutterCallbackInformation;
import io.flutter.view.FlutterMain;

public class BackgroundService extends Service implements MethodChannel.MethodCallHandler {
    private static final String TAG = "BackgroundService";
    private FlutterEngine backgroundEngine;
    private MethodChannel methodChannel;
    private DartExecutor.DartCallback dartCallback;
    private boolean isManuallyStopped = false;

    String notificationTitle = "BackgroundService";
    String notificationContent = "Running";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void enqueue(Context context) {
        Intent intent = new Intent(context, WatchdogReceiver.class);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        PendingIntent pIntent = PendingIntent.getBroadcast(context, 111, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManagerCompat.setAndAllowWhileIdle(manager, AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pIntent);
    }

    public static void setCallbackDispatcher(Context context, long callbackHandleId, boolean isForeground, boolean autoStartOnBoot) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit()
                .putLong("callback_handle", callbackHandleId)
                .putBoolean("is_foreground", isForeground)
                .putBoolean("auto_start_on_boot", autoStartOnBoot)
                .apply();
    }

    public void setAutoStartOnBootMode(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("auto_start_on_boot", value).apply();
    }

    public static boolean isAutoStartOnBootMode(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("auto_start_on_boot", true);
    }

    public void setForegroundServiceMode(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("is_foreground", value).apply();
    }

    public static boolean isForegroundService(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("is_foreground", true);
    }

    public void setManuallyStopped(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("is_manually_stopped", value).apply();
    }

    public static boolean isManuallyStopped(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("is_manually_stopped", false);
    }

    private final BroadcastReceiver mBroadCastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (methodChannel == null) {
                return;
            }
            JSONObject json = new JSONObject();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        try {
                            json.put("state", "btON");
                            methodChannel.invokeMethod("onReceiveData", json);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        try {
                            json.put("state", "btOFF");
                            methodChannel.invokeMethod("onReceiveData", json);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG,"STATE_TURNING_ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG,"STATE_TURNING_OFF");
                        break;
                }
            } else if(action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                boolean gps_state = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (gps_state) {
                    try {
                        json.put("state", "gpsON");
                        methodChannel.invokeMethod("onReceiveData", json);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        json.put("state", "gpsOFF");
                        methodChannel.invokeMethod("onReceiveData", json);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        try {
            String packageName = getApplicationContext().getPackageName();
            PackageInfo pm = getPackageManager().getPackageInfo(packageName, 0);
            notificationTitle = pm.applicationInfo.loadLabel(getPackageManager()).toString();
        } catch (Exception e) {
            Log.e(TAG, "Exception : " + e);
        }
        notificationContent = "여기를 누르면 실행합니다.";
        updateNotificationInfo();

        IntentFilter intent = new IntentFilter();
        intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intent.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        registerReceiver(mBroadCastReceiver, intent);
    }

    @Override
    public void onDestroy() {
        if (!isManuallyStopped) {
            enqueue(this);
        } else {
            setManuallyStopped(true);
        }
        unregisterReceiver(mBroadCastReceiver);
        stopForeground(true);
        isRunning.set(false);

        if (backgroundEngine != null) {
            backgroundEngine.getServiceControlSurface().detachFromService();
            backgroundEngine.destroy();
            backgroundEngine = null;
        }

        methodChannel = null;
        dartCallback = null;
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "CHANNEL_DEFAULT";
            String description = "실행 알림";

            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(channelId, description, importance);
            channel.setShowBadge(false);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    protected void updateNotificationInfo() {
        if (isForegroundService(this)) {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            boolean gps_state = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean bt_state = BluetoothAdapter.getDefaultAdapter().isEnabled();
            String packageName = getApplicationContext().getPackageName();
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            int resource = R.drawable.noti_not_running;
            if (gps_state && bt_state) {
                resource = R.drawable.noti_running;
            } else if (gps_state && !bt_state) {
                intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            } else if (!gps_state && bt_state) {
                intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            } else {
                intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            }

            PendingIntent pi = PendingIntent.getActivity(BackgroundService.this, 99778, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "CHANNEL_DEFAULT")
                    .setSmallIcon(resource)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationContent)
                    .setContentIntent(pi);

            startForeground(99778, mBuilder.build());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setManuallyStopped(false);
        enqueue(this);
        runService();

        return START_STICKY;
    }

    AtomicBoolean isRunning = new AtomicBoolean(false);

    private void runService() {
        try {
            if (isRunning.get() || (backgroundEngine != null && !backgroundEngine.getDartExecutor().isExecutingDart()))
                return;
            updateNotificationInfo();

            SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
            long callbackHandle = pref.getLong("callback_handle", 0);

            FlutterCallbackInformation callback = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle);
            if (callback == null) {
                Log.e(TAG, "callback handle not found");
                return;
            }

            isRunning.set(true);
            backgroundEngine = new FlutterEngine(this);
            backgroundEngine.getServiceControlSurface().attachToService(BackgroundService.this, null, isForegroundService(this));

            methodChannel = new MethodChannel(backgroundEngine.getDartExecutor().getBinaryMessenger(), "id.flutter/background_service_bg", JSONMethodCodec.INSTANCE);
            methodChannel.setMethodCallHandler(this);

            dartCallback = new DartExecutor.DartCallback(getAssets(), FlutterInjector.instance().flutterLoader().findAppBundlePath(), callback);
            backgroundEngine.getDartExecutor().executeDartCallback(dartCallback);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "UnsatisfiedLinkError: After a reboot this may happen for a short period and it is ok to ignore then!" + e.getMessage());
        }
    }

    public void receiveData(JSONObject data) {
        if (methodChannel != null) {
            try {
                methodChannel.invokeMethod("onReceiveData", data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        String method = call.method;

        try {
            if (method.equalsIgnoreCase("setNotificationInfo")) {
                JSONObject arg = (JSONObject) call.arguments;
                if (arg.has("title")) {
                    notificationTitle = arg.getString("title");
                    notificationContent = arg.getString("content");
                    updateNotificationInfo();
                    result.success(true);
                    return;
                }
            }

            if (method.equalsIgnoreCase("setAutoStartOnBootMode")) {
                JSONObject arg = (JSONObject) call.arguments;
                boolean value = arg.getBoolean("value");
                setAutoStartOnBootMode(value);
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("setForegroundMode")) {
                JSONObject arg = (JSONObject) call.arguments;
                boolean value = arg.getBoolean("value");
                setForegroundServiceMode(value);
                if (value) {
                    updateNotificationInfo();
                } else {
                    stopForeground(true);
                }

                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("stopService")) {
                isManuallyStopped = true;
                Intent intent = new Intent(this, WatchdogReceiver.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 111, intent, 0);
                AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
                alarmManager.cancel(pendingIntent);
                stopSelf();
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("sendData")) {
                LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
                Intent intent = new Intent("id.flutter/background_service");
                intent.putExtra("data", ((JSONObject) call.arguments).toString());
                manager.sendBroadcast(intent);
                result.success(true);
                return;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }

        result.notImplemented();
    }
}
