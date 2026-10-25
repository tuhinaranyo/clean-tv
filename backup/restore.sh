#!/bin/sh
# Re-enables every package disabled by this project. Usage: TV=192.168.0.109 ./restore.sh
adb connect ${TV:-192.168.0.109}:5555
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.android.backupconfirm
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.android.htmlviewer
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.android.printspooler
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.android.sharedstoragebackup
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.android.wallpaperbackup
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.apps.mediashell
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.backdrop
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.backuptransport
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.feedback
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.leanbacklauncher
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.leanbacklauncher.recommendations
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.marvin.talkback
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.play.games
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.syncadapters.calendar
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.syncadapters.contacts
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.tv.bugreportsender
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.tvlauncher
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.google.android.tvrecommendations
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.mitv.dream
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.mitv.gallery
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.mitv.tvhome.atv
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.mitv.tvhome.michannel
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.mitv.tvhome.mitvplus
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.mitv.tvhome.oemtab
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.miui.tv.analytics
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.sonyliv
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.tcl.browser
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.xiaomi.mimusic2
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.xiaomi.mitv.tvmanager
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.xiaomi.mitv.updateservice
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.xiaomi.statistic
adb -s ${TV:-192.168.0.109}:5555 shell pm enable com.xm.webcontent
adb -s ${TV:-192.168.0.109}:5555 shell pm enable in.startv.hotstar
adb -s ${TV:-192.168.0.109}:5555 shell pm enable org.chromium.webview_shell

# Undo the speed tweaks (animations back to default)
adb -s ${TV:-192.168.0.109}:5555 shell settings put global window_animation_scale 1.0
adb -s ${TV:-192.168.0.109}:5555 shell settings put global transition_animation_scale 1.0
adb -s ${TV:-192.168.0.109}:5555 shell settings put global animator_duration_scale 1.0
