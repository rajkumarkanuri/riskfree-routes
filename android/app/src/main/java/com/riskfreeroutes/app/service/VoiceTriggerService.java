package com.riskfreeroutes.app.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Service that continuously listens for the Voice SOS trigger phrase
 * ("help me guardian") during active navigation.
 */
public class VoiceTriggerService extends Service {
    private static final String TAG = "VoiceTriggerService";
    public static final String ACTION_VOICE_SOS_TRIGGERED = "com.riskfreeroutes.app.ACTION_VOICE_SOS_TRIGGERED";
    private static final String TRIGGER_PHRASE = "help me guardian";

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;
    private boolean isPhoneCallActive = false;
    private TelephonyManager telephonyManager;

    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            if (state == TelephonyManager.CALL_STATE_IDLE) {
                isPhoneCallActive = false;
                startListening();
            } else {
                isPhoneCallActive = true;
                stopListening();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new VoiceRecognitionListener());

            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        } else {
            Log.e(TAG, "Speech recognition not available on this device.");
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startListening();
        return START_NOT_STICKY; // Don't restart if killed, it only runs when navigation is active
    }

    private void startListening() {
        if (speechRecognizer != null && !isListening && !isPhoneCallActive) {
            try {
                speechRecognizer.startListening(recognizerIntent);
                isListening = true;
            } catch (Exception e) {
                Log.e(TAG, "Error starting speech recognition", e);
            }
        }
    }

    private void stopListening() {
        if (speechRecognizer != null && isListening) {
            try {
                speechRecognizer.stopListening();
                isListening = false;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping speech recognition", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (telephonyManager != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    private class VoiceRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) { }

        @Override
        public void onBeginningOfSpeech() { }

        @Override
        public void onRmsChanged(float rmsdB) { }

        @Override
        public void onBufferReceived(byte[] buffer) { }

        @Override
        public void onEndOfSpeech() {
            // Speech ended, we wait for onResults or onError to restart
            isListening = false;
        }

        @Override
        public void onError(int error) {
            Log.d(TAG, "Speech recognition error: " + error);
            isListening = false;
            // Restart listening on timeout or other errors (except permission/client issues if fatal)
            if (!isPhoneCallActive) {
                startListening();
            }
        }

        @Override
        public void onResults(Bundle results) {
            isListening = false;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null) {
                for (String match : matches) {
                    if (match.toLowerCase().contains(TRIGGER_PHRASE)) {
                        Log.i(TAG, "Trigger phrase detected!");
                        LocalBroadcastManager.getInstance(VoiceTriggerService.this)
                                .sendBroadcast(new Intent(ACTION_VOICE_SOS_TRIGGERED));
                        break;
                    }
                }
            }
            // Immediately start listening again for continuous background listening
            if (!isPhoneCallActive) {
                startListening();
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null) {
                for (String match : matches) {
                    if (match.toLowerCase().contains(TRIGGER_PHRASE)) {
                        Log.i(TAG, "Trigger phrase detected in partial results!");
                        LocalBroadcastManager.getInstance(VoiceTriggerService.this)
                                .sendBroadcast(new Intent(ACTION_VOICE_SOS_TRIGGERED));
                        // Restart listening immediately
                        stopListening();
                        startListening();
                        break;
                    }
                }
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) { }
    }
}
