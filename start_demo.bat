@echo off
rem Double-click to start the laptop side of the demo: the MQTT logger and the camera in auto mode.
rem Each runs in its own window; close a window (or Ctrl+C in it) to stop that part.
cd /d "%~dp0"
start "LabDip logger" cmd /k python logger.py --out demo_trials.csv
start "LabDip camera" cmd /k python camera\cam_capture.py --led --mqtt --every 10 --flat latest
