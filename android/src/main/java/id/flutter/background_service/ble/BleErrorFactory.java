package id.flutter.background_service.ble;

import com.polidea.multiplatformbleadapter.errors.BleError;
import com.polidea.multiplatformbleadapter.errors.BleErrorCode;


public class BleErrorFactory {
    
    public static BleError fromThrowable(Throwable exception) {
        return new BleError(BleErrorCode.UnknownError, exception.toString(), null);
    }
}
