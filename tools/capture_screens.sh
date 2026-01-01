#!/bin/bash

# Create output dir
mkdir -p screenshots

# Enable Demo Mode
echo "Enabling Demo Mode..."
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

# Go Home (Page 1)
echo "Navigating to Home..."
adb shell input keyevent 3
sleep 2
echo "Capturing Home..."
adb shell screencap -p /sdcard/home.png
adb pull /sdcard/home.png screenshots/home.png

# Go Left (Page 0 - Widgets)
# Swipe Left->Right
echo "Navigating to Widgets..."
adb shell input swipe 200 1000 900 1000 300
sleep 2
echo "Capturing Widgets..."
adb shell screencap -p /sdcard/widgets.png
adb pull /sdcard/widgets.png screenshots/widgets.png

# Return Home
adb shell input keyevent 3
sleep 1

# Go Right (Page 2 - Drawer)
# Swipe Right->Left
echo "Navigating to Drawer..."
adb shell input swipe 900 1000 200 1000 300
sleep 2
echo "Capturing Drawer..."
adb shell screencap -p /sdcard/drawer.png
adb pull /sdcard/drawer.png screenshots/drawer.png

# Exit Demo Mode
echo "Exiting Demo Mode..."
adb shell am broadcast -a com.android.systemui.demo -e command exit
