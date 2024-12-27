# UnityAndroidBLE Java Library

<p align="center">
    The Java library that the Unity Android Bluetooth Low Energy Plugin binds to<br>
</p>

<p align="center">
    <img src="https://i.imgur.com/fL3ybma.png" style="width:40%;">
</p>

## Features

This repository is to open-source the library that's used in my [Unity Android Bluetooth Low Energy](https://github.com/Velorexe/Unity-Android-Bluetooth-Low-Energy) plugin under the `Assets/Plugins/Android/unityandroidble.jar`. To not make everything secret or closed-sourced, this repository contains the Java side of things, for transparency sake, but also for people who want to expand on the project and add other custom commands.

## What You Need
In order to properly compile this to a working jar, you need:
* The `unity.jar` Java library that's located somewhere in _your_ Unity Editor's folder

The `unity.jar` (renamed from `classes.jar`) is something you need to find yourself, then place in the `libs` folder of your Android project.
For windows you normally find this in C:\Program Files\Unity\Hub\Editor\[Unity-Version]\Editor\Data\PlaybackEngines\AndroidPlayer\Variations\mono\Release\Classes\classes.jar
For mac in /Applications/Unity/Hub/Editor/[Unity-Version]/PlaybackEngines/AndroidPlayer/Variations/mono/Release/Classes/classes.jar

## Compiling

fthiemer: Compiling the application to a `.jar` is removed. You can add the functionality back in, by following instructions in app/build.gradle.

## Contact

If you need any information, have questions about the project or found any bugs in this project, please create a new `Issue` and I'll take a look at it! If you've got more pressing questions or questions that aren't related to create an Issue for, you can contact with the methods below.

* Discord: `velorexe` / `Velorexe#8403`
* Email: <degenerexe.code@gmail.com>


# Relevant changes by fthiemer:
As I added the Polar-BLE-SDK the conditions of the polar license apply for its use. 
I added a copy of it in the root directory.
