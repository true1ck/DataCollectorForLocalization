package com.example.datacollection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerHotspot1, spinnerHotspot2, spinnerHotspot3;
    private Button btnStart;
    private TextView tvStatus, tvSignalStrength1, tvSignalStrength2, tvSignalStrength3, tvEntryCount;
    private WifiManager wifiManager;

    private List<ScanResult> scanResults;
    private List<String> wifiNames;

    private static final int REQUEST_PERMISSION = 1;
    private int dataCount = 0;
    private Timer dataCollectionTimer;
    private Handler handler = new Handler();

    // Variables for previous signal strengths to calculate rate of change
    private int prevSignalStrength1 = -1, prevSignalStrength2 = -1, prevSignalStrength3 = -1;

    // Moving Average Variables (smaller window to increase sensitivity)
    private float movingAverage1 = 0, movingAverage2 = 0, movingAverage3 = 0;
    private static final int MOVING_AVG_WINDOW = 2; // Smaller size for more sensitivity


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        spinnerHotspot1 = findViewById(R.id.spinnerHotspot1);
        spinnerHotspot2 = findViewById(R.id.spinnerHotspot2);
        spinnerHotspot3 = findViewById(R.id.spinnerHotspot3);
        btnStart = findViewById(R.id.btnStart);
        tvStatus = findViewById(R.id.tvStatus);
        tvSignalStrength1 = findViewById(R.id.tvSignalStrength1);
        tvSignalStrength2 = findViewById(R.id.tvSignalStrength2);
        tvSignalStrength3 = findViewById(R.id.tvSignalStrength3);
        tvEntryCount = findViewById(R.id.tvEntryCount);

        // Initialize WifiManager
        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

        // Check for permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION);
        } else {
            loadWifiNetworks();
        }

        // Button click listener
        btnStart.setOnClickListener(v -> startDataCollection());
    }

    private void loadWifiNetworks() {
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(this, "Please enable Wi-Fi", Toast.LENGTH_SHORT).show();
            return;
        }

        wifiManager.startScan();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        scanResults = wifiManager.getScanResults();
        wifiNames = new ArrayList<>();

        for (ScanResult result : scanResults) {
            if (!result.SSID.isEmpty()) {
                wifiNames.add(result.SSID);
            }
        }

        if (wifiNames.isEmpty()) {
            Toast.makeText(this, "No Wi-Fi networks found", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, wifiNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerHotspot1.setAdapter(adapter);
        spinnerHotspot2.setAdapter(adapter);
        spinnerHotspot3.setAdapter(adapter);
    }

    private void startDataCollection() {
        if (wifiNames == null || wifiNames.isEmpty()) {
            Toast.makeText(this, "No Wi-Fi networks selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String ssid1 = spinnerHotspot1.getSelectedItem().toString();
        String ssid2 = spinnerHotspot2.getSelectedItem().toString();
        String ssid3 = spinnerHotspot3.getSelectedItem().toString();

        if (ssid1.equals(ssid2) || ssid1.equals(ssid3) || ssid2.equals(ssid3)) {
            Toast.makeText(this, "Hotspots must be unique", Toast.LENGTH_SHORT).show();
            return;
        }

        tvEntryCount.setText("Entries: 0"); // Reset entry count UI
        tvStatus.setText("Collecting data...");

        // Reset data count for new collection
        dataCount = 0;

        // Start timer for periodic data collection (every 1 second)
        if (dataCollectionTimer != null) {
            dataCollectionTimer.cancel(); // Cancel any existing timer before starting a new one
        }
        dataCollectionTimer = new Timer();
        dataCollectionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                handler.post(() -> collectData(ssid1, ssid2, ssid3));
            }
        }, 0, 500); // 500 milliseconds interval
    }

    // Method to calculate rate of change of signal
    private int calculateRateOfChange(int currentSignalStrength, int prevSignalStrength) {
        return (prevSignalStrength == -1) ? 0 : (currentSignalStrength - prevSignalStrength);
    }

    private void collectData(String ssid1, String ssid2, String ssid3) {
        wifiManager.startScan();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        handler.postDelayed(() -> {
            scanResults = wifiManager.getScanResults();
            int signalStrength1 = getSignalStrength(ssid1);
            int signalStrength2 = getSignalStrength(ssid2);
            int signalStrength3 = getSignalStrength(ssid3);

            // Calculate rate of change
            int rateOfChange1 = calculateRateOfChange(signalStrength1, prevSignalStrength1);
            int rateOfChange2 = calculateRateOfChange(signalStrength2, prevSignalStrength2);
            int rateOfChange3 = calculateRateOfChange(signalStrength3, prevSignalStrength3);

            // Dynamically adjust moving average window based on rate of change
            int avgWindow1 = (Math.abs(rateOfChange1) > 5) ? 1 : MOVING_AVG_WINDOW;
            int avgWindow2 = (Math.abs(rateOfChange2) > 5) ? 1 : MOVING_AVG_WINDOW;
            int avgWindow3 = (Math.abs(rateOfChange3) > 5) ? 1 : MOVING_AVG_WINDOW;

            // Apply moving average filter
            movingAverage1 = (movingAverage1 * (avgWindow1 - 1) + signalStrength1) / avgWindow1;
            movingAverage2 = (movingAverage2 * (avgWindow2 - 1) + signalStrength2) / avgWindow2;
            movingAverage3 = (movingAverage3 * (avgWindow3 - 1) + signalStrength3) / avgWindow3;

            // Update UI with smoothed values and rate of change
            tvSignalStrength1.setText("Strength 1 (Smoothed): " + movingAverage1 + " dBm, Rate: " + rateOfChange1 + " dBm/s");
            tvSignalStrength2.setText("Strength 2 (Smoothed): " + movingAverage2 + " dBm, Rate: " + rateOfChange2 + " dBm/s");
            tvSignalStrength3.setText("Strength 3 (Smoothed): " + movingAverage3 + " dBm, Rate: " + rateOfChange3 + " dBm/s");

            // Save the smoothed data and rate of change to CSV
            saveDataToCSV(ssid1, ssid2, ssid3, movingAverage1, movingAverage2, movingAverage3, rateOfChange1, rateOfChange2, rateOfChange3);

            // Update previous signal strengths for next iteration
            prevSignalStrength1 = signalStrength1;
            prevSignalStrength2 = signalStrength2;
            prevSignalStrength3 = signalStrength3;

            // Increment data count
            dataCount++;
            tvEntryCount.setText("Entries: " + dataCount);

            // Stop data collection after 100 entries
            if (dataCount >= 100) {
                stopDataCollection();
                Toast.makeText(MainActivity.this, "Data collection stopped after 100 entries", Toast.LENGTH_SHORT).show();
            }
        }, 500); // Delay to ensure the scan finishes before processing
    }

    // Method to stop the data collection
    private void stopDataCollection() {
        if (dataCollectionTimer != null) {
            dataCollectionTimer.cancel();
            dataCollectionTimer = null;
            Toast.makeText(this, "Data collection stopped", Toast.LENGTH_SHORT).show();
        }
    }


    private int getSignalStrength(String ssid) {
        for (ScanResult result : scanResults) {
            if (result.SSID.equals(ssid)) {
                return result.level; // Signal strength in dBm
            }
        }
        return -1; // Signal not found
    }

    private void saveDataToCSV(String ssid1, String ssid2, String ssid3, float signalStrength1, float signalStrength2, float signalStrength3, int rateOfChange1, int rateOfChange2, int rateOfChange3) {
        // Use app-specific storage location instead of external storage
        File documentsDir = new File(getExternalFilesDir(null), "csv");
        if (!documentsDir.exists()) {
            documentsDir.mkdirs();
        }

        File csvFile = new File(documentsDir, "wifi_signal_data.csv");

        try {
            if (!csvFile.exists()) {
                csvFile.createNewFile();
                FileWriter writer = new FileWriter(csvFile);
                writer.append("Timestamp,SSID_1,Signal_1,Rate_1,SSID_2,Signal_2,Rate_2,SSID_3,Signal_3,Rate_3\n");
                writer.close();
            }

            // Writing data to the CSV file
            FileWriter writer = new FileWriter(csvFile, true);
            writer.append(System.currentTimeMillis() + "," + ssid1 + "," + signalStrength1 + "," + rateOfChange1 + "," + ssid2 + "," + signalStrength2 + "," + rateOfChange2 + "," + ssid3 + "," + signalStrength3 + "," + rateOfChange3 + "\n");
            writer.close();

            // Toast.makeText(this, "Data saved to CSV file", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
