package com.kt.usb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.HashMap;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    private int pid=60000,vid=4292;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private UsbManager manager;
    private PendingIntent permissionIntent;
    private UsbDevice targetDevice;
    private UsbSerialDevice serial;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver,filter);
        enum_devices();
    }

    public void enum_devices() {
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if (device.getProductId() == pid && device.getVendorId() == vid) {
                //Log.e(TAG, "enum_devices: find device!!!");
                manager.requestPermission(device,permissionIntent);
            }
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null && device.getProductId() == pid && device.getVendorId() == vid){
                          //call method to set up device communication
                            Log.e(TAG, "enum_devices: find device!!!");
                            targetDevice = device;
                            initAsSerial();
                       }
                    } else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
            if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    // call your method that cleans up and closes communication with the device
                    Log.e(TAG, "onReceive:  device detached!!!" );
                    if (device.getProductId() == pid && device.getVendorId() == vid) {
                        serial.close();
                        serial=null;
                    }
                }
            }
        }
    };

    private void initAsSerial() {
        if (targetDevice != null && UsbSerialDevice.isSupported(targetDevice)) {
            Log.e(TAG, "=====> initAsSerial " );
            UsbDeviceConnection usbDeviceConnection = manager.openDevice(targetDevice);
            serial = UsbSerialDevice.createUsbSerialDevice(targetDevice,usbDeviceConnection);
            serial.setPortName("COM1");
            serial.open();
            serial.setBaudRate(115200);
            serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
            serial.setParity(UsbSerialInterface.PARITY_ODD);
            serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
            serial.read(mCallback);
        }
    }

    private UsbSerialInterface.UsbReadCallback mCallback = arg0 -> {
        // Code here :)
        Log.e(TAG, "onReceivedData: "+HexSupport.toHexFromBytes(arg0));
    };

    @Override
    protected void onDestroy() {
        serial.close();
        super.onDestroy();
        unregisterReceiver(usbReceiver);
    }

    public void send(View view) {
        if (serial != null) {
            Log.e(TAG, "======> send: data" );
            serial.write("ED08010FFB010F0400000000".getBytes());
        }
    }
}
