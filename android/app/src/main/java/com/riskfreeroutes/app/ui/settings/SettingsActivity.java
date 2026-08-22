package com.riskfreeroutes.app.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.riskfreeroutes.app.databinding.ActivitySettingsBinding;
import com.riskfreeroutes.app.model.Settings;
import com.riskfreeroutes.app.repository.SettingsRepository;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SettingsRepository settingsRepository;
    private Settings currentSettings;
    
    // Prevent recursive saving during initial UI setup
    private boolean isLoadingSettings = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        settingsRepository = new SettingsRepository();
        
        binding.btnBack.setNavigationOnClickListener(v -> finish());
        
        setupSpinners();
        loadSettings();
    }
    
    private void setupSpinners() {
        // Prepare options for safety mode
        String[] safetyModes = {"Standard", "Student", "Women", "Night Shift"};
        ArrayAdapter<String> safetyModeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, safetyModes);
        safetyModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSafetyMode.setAdapter(safetyModeAdapter);
        
        // Prepare options for map type
        String[] mapTypes = {"Normal", "Satellite", "Terrain", "Hybrid"};
        ArrayAdapter<String> mapTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mapTypes);
        mapTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerMapType.setAdapter(mapTypeAdapter);
    }

    private void loadSettings() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.contentLayout.setVisibility(View.GONE);
        isLoadingSettings = true;
        
        settingsRepository.getSettings(new SettingsRepository.SettingsCallback() {
            @Override
            public void onSuccess(Settings settings) {
                currentSettings = settings;
                if (currentSettings == null) {
                    currentSettings = new Settings();
                }
                
                binding.progressBar.setVisibility(View.GONE);
                binding.contentLayout.setVisibility(View.VISIBLE);
                
                populateUI();
                setupListeners();
                
                isLoadingSettings = false;
            }

            @Override
            public void onFailure(Exception e) {
                binding.progressBar.setVisibility(View.GONE);
                Snackbar.make(binding.getRoot(), "Failed to load settings", Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void populateUI() {
        binding.switchNotifications.setChecked(currentSettings.isNotificationsEnabled());
        binding.switchSmsAlerts.setChecked(currentSettings.isSmsAlertsEnabled());
        binding.switchHeatmap.setChecked(currentSettings.isHeatmapDefaultOn());
        
        setSpinnerSelection(binding.spinnerSafetyMode, currentSettings.getDefaultSafetyMode());
        setSpinnerSelection(binding.spinnerMapType, currentSettings.getMapType());
    }
    
    private void setSpinnerSelection(android.widget.Spinner spinner, String value) {
        if (value == null) return;
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (value.equals(adapter.getItem(i))) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void setupListeners() {
        CompoundButton.OnCheckedChangeListener toggleListener = (buttonView, isChecked) -> {
            if (isLoadingSettings || currentSettings == null) return;
            
            int id = buttonView.getId();
            if (id == binding.switchNotifications.getId()) {
                currentSettings.setNotificationsEnabled(isChecked);
            } else if (id == binding.switchSmsAlerts.getId()) {
                currentSettings.setSmsAlertsEnabled(isChecked);
            } else if (id == binding.switchHeatmap.getId()) {
                currentSettings.setHeatmapDefaultOn(isChecked);
            }
            saveSettings();
        };
        
        binding.switchNotifications.setOnCheckedChangeListener(toggleListener);
        binding.switchSmsAlerts.setOnCheckedChangeListener(toggleListener);
        binding.switchHeatmap.setOnCheckedChangeListener(toggleListener);
        
        AdapterView.OnItemSelectedListener spinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isLoadingSettings || currentSettings == null) return;
                
                String selectedItem = (String) parent.getItemAtPosition(position);
                int parentId = parent.getId();
                
                if (parentId == binding.spinnerSafetyMode.getId()) {
                    currentSettings.setDefaultSafetyMode(selectedItem);
                } else if (parentId == binding.spinnerMapType.getId()) {
                    currentSettings.setMapType(selectedItem);
                }
                saveSettings();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        
        binding.spinnerSafetyMode.setOnItemSelectedListener(spinnerListener);
        binding.spinnerMapType.setOnItemSelectedListener(spinnerListener);
    }

    private void saveSettings() {
        if (currentSettings == null) return;
        
        settingsRepository.saveSettings(currentSettings, new SettingsRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                Snackbar.make(binding.getRoot(), "Settings saved", Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Exception e) {
                Snackbar.make(binding.getRoot(), "Failed to save settings", Snackbar.LENGTH_LONG).show();
            }
        });
    }
}
