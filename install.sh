#!/bin/sh
# Build the launcher and install it on the TV, set it as default Home.
# Usage: TV_HOST=192.168.0.109 ./install.sh
set -e
cd "$(dirname "$0")"
./build.sh
./venv/bin/python tools/tv.py "am start -a android.intent.action.MAIN -c android.intent.category.HOME; am force-stop com.android.tv.settings; am force-stop com.google.android.youtube.tv; am force-stop com.google.android.youtube.tvmusic" >/dev/null
./venv/bin/python tools/install.py launcher/build/launcher.apk
./venv/bin/python tools/tv.py 'cmd package set-home-activity app.cleanyourtv.launcher/.LauncherActivity' \
  'pm grant app.cleanyourtv.launcher android.permission.ACCESS_COARSE_LOCATION ; pm grant app.cleanyourtv.launcher android.permission.ACCESS_FINE_LOCATION ; pm grant app.cleanyourtv.launcher android.permission.WRITE_SECURE_SETTINGS ; pm grant app.cleanyourtv.launcher android.permission.CHANGE_CONFIGURATION ; pm grant app.cleanyourtv.launcher android.permission.RECORD_AUDIO ; appops set app.cleanyourtv.launcher WRITE_SETTINGS allow' \
  'am force-stop com.android.tv.settings ; am start -n app.cleanyourtv.launcher/.LauncherActivity'
