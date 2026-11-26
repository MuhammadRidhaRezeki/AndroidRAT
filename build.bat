@echo off
rem Manual APK build without Gradle.
rem kotlinc -> jar -> d8 -> aapt2 -> zipalign -> apksigner
setlocal enabledelayedexpansion

set PROJ=%~dp0
set SDK=C:\Users\Ridha\work\work2\.tools\android-sdk
set KOTLINC=C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc\bin\kotlinc.bat
set KOTLIN_LIB=C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc\lib
set JAR=C:\Program Files\Java\jdk-24\bin\jar.exe
set ANDROID_JAR=%SDK%\platforms\android-34\android.jar
set BT=%SDK%\build-tools\34.0.0
set KS=%USERPROFILE%\.android\debug.keystore
set OUT=%PROJ%build_manual
set SRC=%PROJ%app\src\main

if not exist "%OUT%" mkdir "%OUT%"
if exist "%OUT%\classes" rmdir /s /q "%OUT%\classes"
mkdir "%OUT%\classes"
if exist "%OUT%\dex" rmdir /s /q "%OUT%\dex"
mkdir "%OUT%\dex"

echo [1/6] kotlinc
call "%KOTLINC%" -classpath "%ANDROID_JAR%" -jvm-target 17 -d "%OUT%\classes" "%SRC%\java"
if errorlevel 1 goto :fail

echo [2/6] jar
"%JAR%" --create --file "%OUT%\classes.jar" -C "%OUT%\classes" .
if errorlevel 1 goto :fail

echo [3/6] d8
call "%BT%\d8.bat" --release --min-api 26 --lib "%ANDROID_JAR%" --output "%OUT%\dex" "%OUT%\classes.jar" "%KOTLIN_LIB%\kotlin-stdlib.jar"
if not exist "%OUT%\dex\classes.dex" goto :fail

echo [4/6] aapt2 link
rem aapt2 needs a literal package attribute; AGP normally injects it from namespace
powershell -NoProfile -Command "(Get-Content '%SRC%\AndroidManifest.xml' -Raw) -replace '<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">', '<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"com.example.rat\">' | Set-Content -Path '%OUT%\AndroidManifest.xml' -Encoding UTF8"
call "%BT%\aapt2.exe" link -o "%OUT%\base.apk" --manifest "%OUT%\AndroidManifest.xml" -I "%ANDROID_JAR%" --min-sdk-version 26 --target-sdk-version 34 --version-code 3 --version-name 1.2
if errorlevel 1 goto :fail

echo [5/6] add dex + zipalign
powershell -NoProfile -Command "Add-Type -AssemblyName System.IO.Compression.FileSystem; $z=[System.IO.Compression.ZipFile]::Open('%OUT%\base.apk','Update'); $e=$z.CreateEntry('classes.dex',[System.IO.Compression.CompressionLevel]::NoCompression); $i=[System.IO.File]::OpenRead('%OUT%\dex\classes.dex'); $o=$e.Open(); $i.CopyTo($o); $o.Close(); $i.Close(); $z.Dispose()"
call "%BT%\zipalign.exe" -p -f 4 "%OUT%\base.apk" "%OUT%\aligned.apk"
if errorlevel 1 goto :fail

echo [6/6] sign
call "%BT%\apksigner.bat" sign --ks "%KS%" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out "%OUT%\app-debug-signed.apk" "%OUT%\aligned.apk"
if errorlevel 1 goto :fail

echo.
echo built: %OUT%\app-debug-signed.apk
goto :eof

:fail
echo.
echo build failed
exit /b 1
