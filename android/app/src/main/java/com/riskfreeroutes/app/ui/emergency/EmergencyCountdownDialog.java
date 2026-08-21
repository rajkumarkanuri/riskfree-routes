package com.riskfreeroutes.app.ui.emergency;

import android.app.Dialog;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.riskfreeroutes.app.R;

public class EmergencyCountdownDialog extends BottomSheetDialogFragment {

    public interface CountdownListener {
        void onSafeClicked();
        void onEmergencyNowClicked();
        void onCountdownFinished();
        void onCancelClicked();
        void onFindNearbyHelpClicked();
    }

    private CountdownListener listener;
    private CountDownTimer timer;
    private TextView tvCountdown;
    private int durationSeconds = 30; // Default 30s for automated triggers

    public static EmergencyCountdownDialog newInstance(CountdownListener listener) {
        return newInstance(listener, 30);
    }

    public static EmergencyCountdownDialog newInstance(CountdownListener listener, int durationSeconds) {
        EmergencyCountdownDialog fragment = new EmergencyCountdownDialog();
        fragment.listener = listener;
        fragment.durationSeconds = durationSeconds;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_emergency_countdown, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvCountdown = view.findViewById(R.id.tv_countdown);

        view.findViewById(R.id.btn_safe).setOnClickListener(v -> {
            cancelTimer();
            if (listener != null) listener.onSafeClicked();
            dismiss();
        });

        view.findViewById(R.id.btn_emergency_now).setOnClickListener(v -> {
            cancelTimer();
            if (listener != null) listener.onEmergencyNowClicked();
            dismiss();
        });

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            cancelTimer();
            if (listener != null) listener.onCancelClicked();
            dismiss();
        });

        View btnFindNearby = view.findViewById(R.id.btn_find_nearby);
        if (btnFindNearby != null) {
            btnFindNearby.setOnClickListener(v -> {
                cancelTimer();
                if (listener != null) listener.onFindNearbyHelpClicked();
                dismiss();
            });
        }

        startTimer();
    }

    private void startTimer() {
        long millis = durationSeconds * 1000L;
        timer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                if (tvCountdown != null) {
                    tvCountdown.setText(String.valueOf(seconds));
                }
            }

            @Override
            public void onFinish() {
                if (listener != null) listener.onCountdownFinished();
                dismiss();
            }
        }.start();
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
    }
}
