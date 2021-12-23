package id.flutter.background_service.delegate;

import android.util.Log;

import id.flutter.background_service.ble.SafeMainThreadResolver;
import id.flutter.background_service.constant.ArgumentKey;
import id.flutter.background_service.constant.MethodName;
import id.flutter.background_service.converter.BleErrorJsonConverter;
import com.polidea.multiplatformbleadapter.BleAdapter;
import com.polidea.multiplatformbleadapter.Device;
import com.polidea.multiplatformbleadapter.OnErrorCallback;
import com.polidea.multiplatformbleadapter.OnSuccessCallback;
import com.polidea.multiplatformbleadapter.errors.BleError;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class MtuDelegate extends CallDelegate {

    private static final String TAG = MtuDelegate.class.getName();
    private static List<String> supportedMethods =  Arrays.asList(MethodName.REQUEST_MTU);

    private BleAdapter bleAdapter;
    private BleErrorJsonConverter bleErrorJsonConverter = new BleErrorJsonConverter();

    public MtuDelegate(BleAdapter bleAdapter) {
        super(supportedMethods);
        this.bleAdapter = bleAdapter;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall methodCall, @NonNull MethodChannel.Result result) {
        switch (methodCall.method) {
            case MethodName.REQUEST_MTU:
                requestMtu(
                    methodCall.<String>argument(ArgumentKey.DEVICE_IDENTIFIER),
                    methodCall.<Integer>argument(ArgumentKey.MTU),
                    methodCall.<String>argument(ArgumentKey.TRANSACTION_ID),
                    result);
                return;
            default:
                throw new IllegalArgumentException(methodCall.method + " cannot be handled by this delegate");
        }
    }

    private void requestMtu(String deviceIdentifier, @NonNull int mtu, String transactionId, @NonNull final MethodChannel.Result result) {
        Log.d(TAG, "Request MTU " + mtu);

        final SafeMainThreadResolver resolver = new SafeMainThreadResolver<>(
                new OnSuccessCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer mtu) {
                        result.success(mtu);
                    }
                },
                new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        Log.e(TAG, "MTU request error " + error.reason + "  " + error.internalMessage);
                        result.error(String.valueOf(error.errorCode.code), error.reason, bleErrorJsonConverter.toJson(error));
                    }
                });

        bleAdapter.requestMTUForDevice(deviceIdentifier, mtu, transactionId, new OnSuccessCallback<Device>() {
            @Override
            public void onSuccess(Device device) {
                resolver.onSuccess(device.getMtu());
            }
        }, new OnErrorCallback() {
            @Override
            public void onError(BleError error) {
                resolver.onError(error);
            }
        });
    }
}
