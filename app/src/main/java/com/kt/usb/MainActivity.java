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
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    private int pid=60000,vid=4292;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private UsbManager manager;
    private PendingIntent permissionIntent;
    private UsbDevice targetDevice;
    private UsbSerialDevice serial;
    private EditText editText;
    private byte[] usbSerialBuffer,tmpUsbSerialBuffer;
    private AtomicBoolean isSerialInit = new AtomicBoolean(false);
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText = findViewById(R.id.et_txt);
        handler = new Handler();
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
                targetDevice = device;
                if (!manager.hasPermission(device)) {
                    manager.requestPermission(device, permissionIntent);
                } else {
                    initAsSerial();
                }
            }
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if(device != null && device.getProductId() == pid && device.getVendorId() == vid){
                      //call method to set up device communication
                        Log.e(TAG, "=====> find device!!!");
                        targetDevice = device;
                        initAsSerial();
                   }
                } else {
                    Log.d(TAG, "permission denied for device " + device);
                }
            }
            if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action) || UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    // call your method that cleans up and closes communication with the device
                    Log.e(TAG, "=====> onReceive:  device detached!!!" );
                    if (device.getProductId() == pid && device.getVendorId() == vid) {
                        serial.close();
                        serial=null;
                        //isSerialInit.set(false);
                    }
                }
            }
        }
    };

    private void initAsSerial() {
        if (targetDevice != null && UsbSerialDevice.isSupported(targetDevice) && isSerialInit.compareAndSet(false,true)) {
            UsbDeviceConnection usbDeviceConnection = manager.openDevice(targetDevice);
            serial = UsbSerialDevice.createUsbSerialDevice(targetDevice,usbDeviceConnection);
            if (!serial.isOpen()) {
                serial.setInitialBaudRate(115200);
                serial.open();
                Log.e(TAG, "=====> open serial success! " );
                serial.setPortName("COM1");
                serial.setBaudRate(115200);
                serial.debug(true);
                serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
                serial.setParity(UsbSerialInterface.PARITY_NONE);
                serial.setStopBits(UsbSerialInterface.STOP_BITS_1);
                serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                serial.read(mCallback);
            }
        }
    }

    private UsbSerialInterface.UsbReadCallback mCallback = data -> {
        // Code here :)
        //Log.i(TAG, "=====> onReceivedData: " + HexSupport.toHexFromBytes(data));
        if (usbSerialBuffer == null) {
            if (HexSupport.toHexFromBytes(data).startsWith("ED")) {
                usbSerialBuffer = data;
            }
        } else if (data.length>0){
            tmpUsbSerialBuffer = new byte[usbSerialBuffer.length + data.length];
            System.arraycopy(usbSerialBuffer, 0, tmpUsbSerialBuffer, 0, usbSerialBuffer.length);
            System.arraycopy(data, 0, tmpUsbSerialBuffer, usbSerialBuffer.length, data.length);
            usbSerialBuffer = tmpUsbSerialBuffer;
        }
        if (usbSerialBuffer != null && usbSerialBuffer.length == 14) {
            Log.e(TAG, "=======> usb back onReceivedData : " + HexSupport.toHexFromBytes(usbSerialBuffer));
            usbSerialBuffer = null;
        }
    };

    @Override
    protected void onDestroy() {
        if (serial != null) {
            serial.close();
            serial = null;
        }
        super.onDestroy();
        unregisterReceiver(usbReceiver);
    }

    public void send(View view) {
        if (serial != null) {
            Log.e(TAG, "======> send: "+editText.getText().toString());
            serial.write(HexSupport.toBytesFromHex(editText.getText().toString()));
        }
    }

}
