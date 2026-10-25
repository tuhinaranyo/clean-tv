#!/bin/sh
# Put Google's own voice assistant screen back (undo the custom voice screen).
cd "$(dirname "$0")"
./venv/bin/python tools/tv.py 'settings put secure voice_interaction_service com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService ; settings delete secure assistant ; echo restored'
