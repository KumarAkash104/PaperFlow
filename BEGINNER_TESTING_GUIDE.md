# Beginner Testing Guide

This guide assumes you are starting from zero Android background.

## 1. Install Android Studio

Download Android Studio from the official Android Developers page:

https://developer.android.com/studio/install

During setup, keep the default options selected. Android Studio will install the Android SDK, emulator tools, and a JDK.

## 2. Open the project

1. Open Android Studio.
2. Click `Open`.
3. Select this folder: `D:\Codex`.
4. Wait for `Gradle Sync` to finish.

If Android Studio asks to install missing SDK components, accept it.

## 3. Prepare your Android phone

Camera apps are best tested on a real phone.

1. Open `Settings` on your phone.
2. Go to `About phone`.
3. Tap `Build number` 7 times to enable Developer options.
4. Go back to `Settings`.
5. Open `Developer options`.
6. Turn on `USB debugging`.
7. Connect the phone to your computer using USB.
8. When the phone asks whether to allow USB debugging, tap `Allow`.

## 4. Run the app

1. In Android Studio, look near the top toolbar.
2. Select your phone from the device dropdown.
3. Click the green Run button.
4. Wait for the app to install and open.

## 5. First test

Test these items in order:

1. The app opens and says it needs camera access.
2. Tap `Allow camera`.
3. Android asks for camera permission.
4. Tap `Allow`.
5. You should see the live camera preview.
6. Point the phone at a paper document.
7. Tap `Capture`.
8. The app should show the captured photo.
9. Tap `Retake`.
10. The live camera preview should return.

## 6. If something fails

Tell me exactly which step failed and copy any red error shown in Android Studio.

Useful details to send me:

- Your phone model.
- Android version on the phone.
- Whether the app installed.
- Whether camera permission appeared.
- Whether camera preview appeared.
- The first red error line from Android Studio, if there is one.

## 7. What we will build next

After this first camera test works, the next version will add:

1. Multiple captured pages.
2. A page list.
3. Delete page.
4. Rotate page.
5. Simple PDF export.
