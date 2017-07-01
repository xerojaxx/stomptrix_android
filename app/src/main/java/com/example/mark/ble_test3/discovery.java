package com.example.mark.ble_test3;

import android.app.Activity;
import android.Manifest;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;



import android.widget.Button;
import android.widget.ToggleButton;
import android.widget.TextView;

import android.location.LocationManager;
import android.provider.Settings;

import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;



import android.content.Intent;
import android.content.pm.PackageManager;

import android.view.Menu;
import android.view.MenuItem;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.util.UUID;

import android.util.SparseArray;
import android.widget.Toast;


class BLEScanCallback extends ScanCallback {
    private static final String TAG = "ScanCB";


    public List<ScanResult> results  = new ArrayList<ScanResult>();

    public void clear_results()
    {
        this.results.clear();
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {

        for (ScanResult scan:results)
        {
            String previous_result = scan.getDevice().getAddress();
            String new_result = result.getDevice().getAddress();
            if(previous_result.equals(new_result))
            {
                return;
            }
        }

        Log.d(TAG, "Result" + result.toString());
        results.add(result);
    }

    @Override
    public void onBatchScanResults(List<ScanResult> results) {
        for (ScanResult sr : results) {
            Log.d(TAG, "ScanResult - batch results" + sr.toString());
        }
    }

    @Override
    public void onScanFailed(int errorCode) {
        Log.e(TAG, "Scan Failed" + "Error Code: " + errorCode);
    }
}

class ble_gatt_controller extends BluetoothGattCallback
{
    private static final String TAG = "GATT_CB";
    private Handler mHandler;
    ble_gatt_controller(Handler handler) {
        this.mHandler = handler;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
    {
        switch (newState)
        {
            case BluetoothProfile.STATE_CONNECTED:
                Log.d(TAG, "gattCallback STATE_CONNECTED");
                gatt.discoverServices();
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                Log.d(TAG, "gattCallback STATE_DISCONNECTED");
                break;
            default:

        }

    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        List<BluetoothGattService> services = gatt.getServices();

        Message msg = new Message();
        msg.obj = "Services discovered";
        mHandler.sendMessage(msg);
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic
                                             characteristic, int status) {
        Message msg = new Message();
        msg.obj = characteristic;
        mHandler.sendMessage(msg);
        //this.characteristics.add(characteristic);
        //debug_out.append("onCharacteristicRead" + characteristic.toString() + "\r\n");
//        if (characteristic. == 0x2A19)
//        {
//
//        }
        //gatt.disconnect();
    }




}

public class discovery extends Activity
{
    private static final String TAG = "Disc";

    private static final short STOMP_SERVICE_ID = 0x1815;
    private static final short STOMP_CHAR_BATTERY = 0x2A19;
    private static final short STOMP_CHAR_COLOUR = 0x2A20;


    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mGatt;
    private ble_gatt_controller mgatt_controller;
    private BluetoothLeScanner mLEScanner;
    private BLEScanCallback mScanCallback;

    private SparseArray<BluetoothDevice> mDevices;
    private boolean currently_scanning;
    private ToggleButton trix_tog_btn;

    private Handler mHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            if (msg.obj instanceof BluetoothGattCharacteristic)
            {
                BluetoothGattCharacteristic charac = (BluetoothGattCharacteristic)msg.obj;
                Log.d(TAG, charac.toString());
                byte[] value = charac.getValue();
                int battery_mv = 0;
                for (byte i = 0; i < value.length; i++ )
                {
                    battery_mv += value[i] * (i * 256);
                }
                Log.d(TAG, String.format("Battery_mv: %d", battery_mv));
            }
            else
            {
                String cmd = (String) msg.obj;
                Log.d(TAG, cmd);
                super.handleMessage(msg);

                if (cmd == "Services discovered") {
                    process_discovered_services();
                }
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovery);
        currently_scanning = false;

        Button start_discovery = (Button) findViewById(R.id.start_discovery);
        start_discovery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start_ble_disocvery();
            }
        });

        trix_tog_btn = (ToggleButton) findViewById(R.id.trix_on_off);
        trix_tog_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trix_toggle_on_off(trix_tog_btn.isChecked());
            }
        });

        mScanCallback = new BLEScanCallback();
        mgatt_controller = new ble_gatt_controller(mHandler);

        LocationManager lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        boolean gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!gps_enabled && !network_enabled)
        {
            Intent enableLocationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(enableLocationIntent);
            finish();

        }

        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();
        mDevices = new SparseArray<BluetoothDevice>();

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
    }

    protected void start_ble_disocvery(){
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            // Bluetooth is disabled.
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            return;
        }
        else
        {
            Log.d(TAG, "BT adapter is ON.");
        }

        /*
         * Check for Bluetooth LE Support.  In production, our manifest entry will keep this
         * from installing on these devices, but this will allow test devices or other
         * sideloads to report whether or not the feature exists.
         */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        else
        {
            Toast.makeText(this, "LE Supported.", Toast.LENGTH_SHORT).show();
        }

        if (Build.VERSION.SDK_INT >= 21) {
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            List<ScanFilter> filters  = new ArrayList<ScanFilter>();
            ScanSettings settings = new ScanSettings.Builder()
                    .setReportDelay(0)
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

            if (!currently_scanning) {
                currently_scanning = true;
                mLEScanner.startScan(filters, settings, mScanCallback);
                mHandler.postDelayed(mStopRunnable, 2000);
                Log.d(TAG, "LE Scanner started.");
            }
            else
            {
                Log.d(TAG, "already scanning.");
            }
        }
        else
        {
            Log.d(TAG, "SDK version not supported.");
            finish();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
    }

    private void stopScan() {
        mLEScanner.stopScan(mScanCallback);
        currently_scanning = false;
        Log.d(TAG, "LE scan stopped (timed out)");
        Log.d(TAG, String.format("Found %d device/s", mScanCallback.results.size()));

        if ( !mScanCallback.results.isEmpty())
        {
            Intent myIntent = new Intent(discovery.this, scan_results.class);

            int num_of_results = mScanCallback.results.size();
            String[] results_strs = new String[num_of_results];
            for (int i=0; i<num_of_results; i++)
            {
                results_strs[i] = mScanCallback.results.get(0).getDevice().getName();//  .getAddress();
            }

            myIntent.putExtra("devices", results_strs);

            //myIntent.put("scan_result", mScanCallback.results); //Optional parameters
            startActivityForResult(myIntent, 0);
        }
    }

    private Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(this, true, mgatt_controller);
        }
    }

    private BluetoothGattService find_stomptrix_service(){
        List<BluetoothGattService> services = mGatt.getServices();
        for (BluetoothGattService service: services) {
            short service_id = (short) ((long) service.getUuid().getMostSignificantBits() >> 32);
            if (service_id == STOMP_SERVICE_ID)
            {
                return service;
            }
        }
        return null;
    }

    private BluetoothGattCharacteristic find_stomptrix_char(short uuid_to_find){
        BluetoothGattService stomptrix_service = find_stomptrix_service();
        if ( stomptrix_service != null)
        {
            for (BluetoothGattCharacteristic next_char:stomptrix_service.getCharacteristics())
            {
                short char_id = (short) ((long) next_char.getUuid().getMostSignificantBits() >> 32);
                if(uuid_to_find == char_id)
                {
                    return next_char;
                }
            }
        }
        return null;
    }

    private void process_discovered_services(){
        UUID STOMPTRIX_SERVICE_UUID = new UUID(0x181500001000L ,0x800000805F9B34FBL);

        BluetoothGattService stomptrix_service = mGatt.getService(STOMPTRIX_SERVICE_UUID);//find_stomptrix_service();//
        if ( stomptrix_service != null)
        {
            Log.d(TAG, "Found steptrix service!!");
            ToggleButton trix_tog_btn = (ToggleButton) findViewById(R.id.trix_on_off);
            trix_tog_btn.setVisibility(View.VISIBLE);

            BluetoothGattCharacteristic battery_char = find_stomptrix_char(STOMP_CHAR_BATTERY);
            UUID STOMP_CHAR_COLOUR_UUID = new UUID(0x2A2000001000L ,0x800000805F9B34FBL);
            BluetoothGattCharacteristic colour_char = stomptrix_service.getCharacteristic(STOMP_CHAR_COLOUR_UUID);//find_stomptrix_char(STOMP_CHAR_COLOUR);
//            if (battery_char != null)
//            {
//                if (mGatt.readCharacteristic(battery_char))
//                {
//                    debug_print("Read char sent.");
//                }
//                else
//                {
//                    debug_print("Read char failed to start.");
//                    debug_print(String.format("permissions: %4x", colour_char.getPermissions()));
//                }
//            }
            if (colour_char != null)
            {

                ByteBuffer b = ByteBuffer.allocate(4);
                b.putInt(0x00AA5544);

                byte[] new_colour = b.array();

                colour_char.setValue(new_colour);

                if (mGatt.writeCharacteristic(colour_char))
                {
                    Log.d(TAG, "write char sent.");
                }
                else
                {
                    Log.d(TAG, "write char failed to start.");
                    Log.d(TAG, String.format("permissions: %4x", colour_char.getPermissions()));
                }
            }
//            BluetoothGattCharacteristic charac = stomptrix_service.getCharacteristics().get(0);
//            if (mGatt.readCharacteristic(charac))
//            {
//                Log.d(TAG, "Read char sent.");
//            }
//            else
//            {
//                Log.d(TAG, "Read char failed to start.");
//            }

        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (resultCode == RESULT_OK)
        {
            if (data.hasExtra("selected_device_index"))
            {
                int device_index = data.getExtras().getInt("selected_device_index");
                Log.d(TAG, String.format("selected index %d", device_index));
                BluetoothDevice btDevice = mScanCallback.results.get(device_index).getDevice();
                connectToDevice(btDevice);
            }
        }
        mScanCallback.clear_results();
    }

    protected void trix_toggle_on_off(boolean btn_state)
    {
        process_discovered_services();
    }

}
