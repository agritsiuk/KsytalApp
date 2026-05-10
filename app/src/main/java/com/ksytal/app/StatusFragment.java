package com.ksytal.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.ksytal.app.databinding.FragmentStatusBinding;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StatusFragment extends Fragment {
    private FragmentStatusBinding binding;
    private DataModels.DeviceStatus currentStatus = new DataModels.DeviceStatus();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
    public static StatusFragment instance = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStatusBinding.inflate(inflater, container, false);
        instance = this;
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        showWaitingMessage();
        SmsService.addStatusListener(status -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    currentStatus = status;
                    updateStatusDisplay();
                });
            }
        });
    }

    public void notifyNewData() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                currentStatus = SmsService.getCurrentStatus();
                updateStatusDisplay();
                blinkInfoLine();
            });
        }
    }

    private void blinkInfoLine() {
        if (binding.infoLine == null) return;
        final int originalColor = binding.infoLine.getCurrentTextColor();
        binding.infoLine.post(() -> {
            binding.infoLine.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_red));
            binding.infoLine.postDelayed(() -> {
                if (binding.infoLine != null) binding.infoLine.setTextColor(originalColor);
            }, 300);
        });
    }

    public void onInitialSmsComplete() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                currentStatus = SmsService.getCurrentStatus();
                updateStatusDisplay();
            });
        }
    }

    private void showWaitingMessage() {
        binding.tvWaitingMessage.setVisibility(View.VISIBLE);
        binding.statusContainer.setVisibility(View.GONE);
    }

    private void updateStatusDisplay() {
        binding.tvWaitingMessage.setVisibility(View.GONE);
        binding.statusContainer.setVisibility(View.VISIBLE);
        if (currentStatus.voltageTime != null) {
            binding.tvVoltageValue.setText(currentStatus.voltage ? "есть" : "нет");
            binding.tvVoltageValue.setTextColor(ContextCompat.getColor(requireContext(),
                    currentStatus.voltage ? R.color.dark_green : R.color.dark_red));
        } else binding.tvVoltageValue.setText("—");
        if (currentStatus.currentTemperature != null && currentStatus.currentTemperatureTime != null)
            binding.tvCurrentTempValue.setText(String.format(Locale.US, "%.1f °C", currentStatus.currentTemperature));
        else binding.tvCurrentTempValue.setText("—");
        if (currentStatus.requiredTemperature != null && currentStatus.requiredTemperatureTime != null)
            binding.tvRequiredTempValue.setText(currentStatus.requiredTemperature + " °C");
        else binding.tvRequiredTempValue.setText("—");
        if (currentStatus.lastBalance != null && currentStatus.lastBalanceTime != null)
            binding.tvBalanceValue.setText(String.format(Locale.US, "%.2f р.", currentStatus.lastBalance));
        else binding.tvBalanceValue.setText("—");
        if (currentStatus.upperThreshold != null && currentStatus.upperThresholdTime != null)
            binding.tvUpperThresholdValue.setText(currentStatus.upperThreshold + " °C");
        else binding.tvUpperThresholdValue.setText("—");
        if (currentStatus.lowerThreshold != null && currentStatus.lowerThresholdTime != null)
            binding.tvLowerThresholdValue.setText(currentStatus.lowerThreshold + " °C");
        else binding.tvLowerThresholdValue.setText("—");
        updateLastUpdateTime();
    }

    private void updateLastUpdateTime() {
        Date lastUpdate = null;
        if (currentStatus.currentTemperatureTime != null) lastUpdate = currentStatus.currentTemperatureTime;
        if (currentStatus.requiredTemperatureTime != null && (lastUpdate == null || currentStatus.requiredTemperatureTime.after(lastUpdate)))
            lastUpdate = currentStatus.requiredTemperatureTime;
        if (currentStatus.voltageTime != null && (lastUpdate == null || currentStatus.voltageTime.after(lastUpdate)))
            lastUpdate = currentStatus.voltageTime;
        if (currentStatus.lastBalanceTime != null && (lastUpdate == null || currentStatus.lastBalanceTime.after(lastUpdate)))
            lastUpdate = currentStatus.lastBalanceTime;
        if (lastUpdate != null) binding.tvLastUpdateValue.setText(dateFormat.format(lastUpdate));
        else binding.tvLastUpdateValue.setText("—");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        instance = null;
    }
}
