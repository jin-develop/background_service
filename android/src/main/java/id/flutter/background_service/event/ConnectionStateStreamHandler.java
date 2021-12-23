package id.flutter.background_service.event;

import android.os.Handler;
import android.os.Looper;

import id.flutter.background_service.ble.ConnectionStateChange;
import id.flutter.background_service.converter.BleErrorJsonConverter;
import id.flutter.background_service.converter.ConnectionStateChangeJsonConverter;

import org.json.JSONException;

import io.flutter.plugin.common.EventChannel;

public class ConnectionStateStreamHandler implements EventChannel.StreamHandler {
    private EventChannel.EventSink eventSink;
    private ConnectionStateChangeJsonConverter connectionStateChangeJsonConverter = new ConnectionStateChangeJsonConverter();

    @Override
    synchronized public void onListen(Object o, EventChannel.EventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    synchronized public void onCancel(Object o) {
        eventSink = null;
    }

    synchronized public void onNewConnectionState(final ConnectionStateChange connectionState) {
        if (eventSink != null) {
            final ConnectionStateStreamHandler that = this;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    synchronized (that) {
                        // Check again for null - by the time we get into this async runnable our eventSink
                        // may have been canceled
                        if (eventSink != null) {
                            try {
                                eventSink.success(connectionStateChangeJsonConverter.toJson(connectionState));
                            } catch (JSONException e) {
                                eventSink.error("-1", e.getMessage(), e.getStackTrace());
                            }
                        }
                    }
                }
            });
        }
    }

    synchronized public void onComplete() {
        if (eventSink != null) eventSink.endOfStream();
    }
}
