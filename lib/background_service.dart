import 'dart:async';
import 'dart:io';
import 'dart:ui';

import 'package:flutter/services.dart';

class BackgroundService {
  bool _isFromInitialization = false;
  bool _isRunning = false;
  bool _isMainChannel = false;
  static const MethodChannel backgroundChannel =
      const MethodChannel('id.flutter/background_service_bg');

  static const MethodChannel mainChannel =
      const MethodChannel('id.flutter/background_service');

  static BackgroundService _instance = BackgroundService._internal()
    .._setupBackground();
  BackgroundService._internal();
  factory BackgroundService() => _instance;

  void _setupMain() {
    _isFromInitialization = true;
    _isRunning = true;
    _isMainChannel = true;
    mainChannel.setMethodCallHandler(_handle);
  }

  void _setupBackground() {
    _isRunning = true;
    backgroundChannel.setMethodCallHandler(_handle);
  }

  Future<dynamic> _handle(MethodCall call) async {
    switch (call.method) {
      case "onReceiveData":
        Map<String, dynamic> message =
            Map<String, dynamic>.from(call.arguments);
        _streamController.sink.add(message);
        break;
      default:
    }

    return true;
  }

  static Future<bool> initialize(
    Function onStart, {
    bool foreground = true,
    bool autoStart = true,
  }) async {
    final CallbackHandle? handle = PluginUtilities.getCallbackHandle(onStart);
    if (handle == null) {
      return false;
    }

    final service = BackgroundService();
    service._setupMain();

    final r = await mainChannel.invokeMethod(
      "BackgroundService.start",
      <String, dynamic>{
        "handle": handle.toRawHandle(),
        "is_foreground_mode": foreground,
        "auto_start_on_boot": autoStart
      },
    );

    return r ?? false;
  }

  // Send data from UI to Service, or from Service to UI
  void sendData({String? action}) async {
    if (!(await (isServiceRunning()))) {
      dispose();
      return;
    }
    if (_isFromInitialization) {
      mainChannel.invokeMethod("sendData", {"action": action});
      return;
    }

    backgroundChannel.invokeMethod("sendData", {"action": action});
  }

  // Set Foreground Notification Information
  // Only available when foreground mode is true
  void setNotificationInfo({String? title, String? content}) {
    if (Platform.isAndroid)
      backgroundChannel.invokeMethod("setNotificationInfo", {
        "title": title,
        "content": content,
      });
  }

  // Set Foreground Mode
  // Only for Android
  void setForegroundMode(bool value) {
    if (Platform.isAndroid)
      backgroundChannel.invokeMethod("setForegroundMode", {
        "value": value,
      });
  }

  Future<bool> isServiceRunning() async {
    if (_isMainChannel) {
      var result = await mainChannel.invokeMethod("isServiceRunning");
      return result ?? false;
    } else {
      return _isRunning;
    }
  }

  // StopBackgroundService from Running
  void stopBackgroundService() {
    //TODO: Remove this check once implemented for IOS.
    if (Platform.isAndroid) backgroundChannel.invokeMethod("stopService");
    _isRunning = false;
  }

  void setAutoStartOnBootMode(bool value) {
    if (Platform.isAndroid)
      backgroundChannel.invokeMethod("setAutoStartOnBootMode", {
        "value": value,
      });
  }

  StreamController<Map<String, dynamic>?> _streamController =
      StreamController.broadcast();

  Stream<Map<String, dynamic>?> get onDataReceived => _streamController.stream;

  void dispose() {
    _streamController.close();
  }
}
