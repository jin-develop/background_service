package id.flutter.background_service.converter;

import org.json.JSONException;

import androidx.annotation.Nullable;

public interface JsonConverter<T> {

    @Nullable
    String toJson(T value) throws JSONException;
}
