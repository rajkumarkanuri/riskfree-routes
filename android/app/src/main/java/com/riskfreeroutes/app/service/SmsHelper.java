package com.riskfreeroutes.app.service;

import android.content.Context;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * SmsHelper — A dedicated utility to handle sending SMS messages for emergencies.
 *
 * This class abstracts away the Android SmsManager API and handles breaking long
 * messages into multiple parts automatically.
 */
public class SmsHelper {

    private static final String TAG = "SmsHelper";

    /**
     * Sends the provided message to a list of phone numbers.
     *
     * IMPORTANT: The caller MUST ensure the android.permission.SEND_SMS has been
     * granted before calling this method. If the permission is missing, this will
     * throw a SecurityException.
     *
     * @param context      The application context (used for Toasts if needed).
     * @param phoneNumbers A list of phone numbers (e.g. from Trusted Contacts).
     * @param message      The emergency alert text. It can be longer than 160 characters.
     */
    public static void sendEmergencySms(Context context, List<String> phoneNumbers, String message) {
        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            Log.w(TAG, "No phone numbers provided. Skipping SMS.");
            return;
        }

        if (message == null || message.trim().isEmpty()) {
            Log.w(TAG, "Empty message provided. Skipping SMS.");
            return;
        }

        try {
            // Get the default SmsManager instance
            SmsManager smsManager = SmsManager.getDefault();

            // SMS messages have a ~160 character limit. For longer messages
            // (especially ones containing Google Maps URLs), we need to split them.
            ArrayList<String> parts = smsManager.divideMessage(message);

            for (String phone : phoneNumbers) {
                if (phone == null || phone.trim().isEmpty()) continue;
                
                String cleanPhone = phone.trim();
                Log.d(TAG, "Sending SMS to: " + cleanPhone);

                if (parts.size() > 1) {
                    // Send as a multi-part message
                    smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null);
                } else {
                    // Send as a single message
                    smsManager.sendTextMessage(cleanPhone, null, message, null, null);
                }
            }
            
            Log.i(TAG, "Successfully dispatched SMS to " + phoneNumbers.size() + " contacts.");
            
        } catch (SecurityException e) {
            Log.e(TAG, "SEND_SMS permission missing! Cannot send SMS.", e);
            Toast.makeText(context, "Failed: SMS permission denied.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Unknown error while sending SMS", e);
        }
    }
}
