# background_service

A flutter plugin for execute dart code in background.

## Android

- No additional setting required.
- Support notify for bt, gps state

## iOS

* Register plugins (optional)

```swift
import UIKit
import Flutter
import flutter_background_service // add this

@UIApplicationMain
@objc class AppDelegate: FlutterAppDelegate {
    /// add this
    static func registerPlugins(with registry: FlutterPluginRegistry) {
        GeneratedPluginRegistrant.register(with: registry)
    }

    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {

        AppDelegate.registerPlugins(with: self)
        SwiftBackgroundServicePlugin.setPluginRegistrantCallback { registry in
            AppDelegate.registerPlugins(with: registry)
        }

        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
}
```

* Playing audio continously

Info.plist
```xml
...
	<key>UIBackgroundModes</key>
	<array>
		<string>audio</string>
        ...
	</array>
...
```

Then use audioplayer plugin to play audio.

```dart
...
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  BackgroundService.initialize(onStart);

  runApp(MyApp());
}

void onStart() {
  WidgetsFlutterBinding.ensureInitialized();

  final audioPlayer = AudioPlayer();

  String url =
      "https://www.mediacollege.com/downloads/sound-effects/nature/forest/rainforest-ambient.mp3";

  audioPlayer.onPlayerStateChanged.listen((event) {
    if (event == AudioPlayerState.COMPLETED) {
      audioPlayer.play(url); // repeat
    }
  });

  audioPlayer.play(url);
}
..
```

## Usage

- Follow the example.


## Warning

The code will executed in isolated process, you can't share reference between UI and Service.
Use `sendData` and `onDataReceived` to communicate between service and UI.