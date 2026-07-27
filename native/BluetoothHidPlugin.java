package com.aircontroller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(
    name = "BluetoothHID",
    permissions = {
        @Permission(
            strings = { Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN },
            alias = "bluetooth"
        )
    }
)
public class BluetoothHidPlugin extends Plugin {

    private static final String TAG = "BluetoothHID";

    private BluetoothAdapter adapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice targetDevice;
    private boolean connected = false;

    // Fila serial: garante que sendKey/sendMouse/sendGamepad/sendConsumer
    // nunca disparem em paralelo brigando pelo canal HID.
    private final ExecutorService hidExecutor = Executors.newSingleThreadExecutor();

    // Report ID 1 = Teclado (8 bytes: modifier, reserved, 6 keys)
    // Report ID 2 = Mouse   (4 bytes: buttons+pad, X, Y, Wheel)
    // Report ID 3 = Gamepad (5 bytes: buttons(1 byte=8 botoes), lx, ly, rx, ry)
    // Report ID 4 = Consumer Control (2 bytes: usage code 16-bit) -> HOME / POWER / VOLUME
    private static final byte[] HID_DESCRIPTOR = {
        // ===== TECLADO (Report ID 1) =====
        (byte) 0x05, (byte) 0x01,
        (byte) 0x09, (byte) 0x06,
        (byte) 0xA1, (byte) 0x01,
        (byte) 0x85, (byte) 0x01,
        (byte) 0x05, (byte) 0x07,
        (byte) 0x19, (byte) 0xE0,
        (byte) 0x29, (byte) 0xE7,
        (byte) 0x15, (byte) 0x00,
        (byte) 0x25, (byte) 0x01,
        (byte) 0x75, (byte) 0x01,
        (byte) 0x95, (byte) 0x08,
        (byte) 0x81, (byte) 0x02,
        (byte) 0x95, (byte) 0x01,
        (byte) 0x75, (byte) 0x08,
        (byte) 0x81, (byte) 0x01,
        (byte) 0x95, (byte) 0x06,
        (byte) 0x75, (byte) 0x08,
        (byte) 0x15, (byte) 0x00,
        (byte) 0x25, (byte) 0x65,
        (byte) 0x05, (byte) 0x07,
        (byte) 0x19, (byte) 0x00,
        (byte) 0x29, (byte) 0x65,
        (byte) 0x81, (byte) 0x00,
        (byte) 0xC0,

        // ===== MOUSE (Report ID 2) — 4 bytes reais: botoes, X, Y, Wheel =====
        (byte) 0x05, (byte) 0x01,
        (byte) 0x09, (byte) 0x02,
        (byte) 0xA1, (byte) 0x01,
        (byte) 0x85, (byte) 0x02,
        (byte) 0x09, (byte) 0x01,
        (byte) 0xA1, (byte) 0x00,
        (byte) 0x05, (byte) 0x09,
        (byte) 0x19, (byte) 0x01,
        (byte) 0x29, (byte) 0x03,
        (byte) 0x15, (byte) 0x00,
        (byte) 0x25, (byte) 0x01,
        (byte) 0x95, (byte) 0x03,
        (byte) 0x75, (byte) 0x01,
        (byte) 0x81, (byte) 0x02,
        (byte) 0x95, (byte) 0x01,
        (byte) 0x75, (byte) 0x05,
        (byte) 0x81, (byte) 0x01,
        (byte) 0x05, (byte) 0x01,
        (byte) 0x09, (byte) 0x30,
        (byte) 0x09, (byte) 0x31,
        (byte) 0x15, (byte) 0x81,
        (byte) 0x25, (byte) 0x7F,
        (byte) 0x75, (byte) 0x08,
        (byte) 0x95, (byte) 0x02,
        (byte) 0x81, (byte) 0x06,
        (byte) 0x09, (byte) 0x38,
        (byte) 0x15, (byte) 0x81,
        (byte) 0x25, (byte) 0x7F,
        (byte) 0x75, (byte) 0x08,
        (byte) 0x95, (byte) 0x01,
        (byte) 0x81, (byte) 0x06,
        (byte) 0xC0,
        (byte) 0xC0,

        // ===== GAMEPAD (Report ID 3) — 5 bytes: 1 byte botoes(8) + lx+ly+rx+ry =====
        (byte) 0x05, (byte) 0x01,
        (byte) 0x09, (byte) 0x05,
        (byte) 0xA1, (byte) 0x01,
        (byte) 0x85, (byte) 0x03,
        (byte) 0x05, (byte) 0x09,
        (byte) 0x19, (byte) 0x01,
        (byte) 0x29, (byte) 0x08,
        (byte) 0x15, (byte) 0x00,
        (byte) 0x25, (byte) 0x01,
        (byte) 0x95, (byte) 0x08,
        (byte) 0x75, (byte) 0x01,
        (byte) 0x81, (byte) 0x02,
        (byte) 0x05, (byte) 0x01,
        (byte) 0x09, (byte) 0x30,
        (byte) 0x09, (byte) 0x31,
        (byte) 0x09, (byte) 0x32,
        (byte) 0x09, (byte) 0x35,
        (byte) 0x15, (byte) 0x81,
        (byte) 0x25, (byte) 0x7F,
        (byte) 0x75, (byte) 0x08,
        (byte) 0x95, (byte) 0x04,
        (byte) 0x81, (byte) 0x02,
        (byte) 0xC0,

        // ===== CONSUMER CONTROL (Report ID 4) — HOME / POWER / VOLUME =====
        (byte) 0x05, (byte) 0x0C,
        (byte) 0x09, (byte) 0x01,
        (byte) 0xA1, (byte) 0x01,
        (byte) 0x85, (byte) 0x04,
        (byte) 0x15, (byte) 0x00,
        (byte) 0x26, (byte) 0xFF, (byte) 0x03,
        (byte) 0x19, (byte) 0x00,
        (byte) 0x2A, (byte) 0xFF, (byte) 0x03,
        (byte) 0x75, (byte) 0x10,
        (byte) 0x95, (byte) 0x01,
        (byte) 0x81, (byte) 0x00,
        (byte) 0xC0
    };

    // Usage codes da pagina Consumer (0x0C) usados pelo app JS:
    // HOME  = 0x0223 (AC Home)
    // POWER = 0x0030 (Power)
    // VOL+  = 0x00E9 (Volume Increment)
    // VOL-  = 0x00EA (Volume Decrement)

    @Override
    public void load() {
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(getContext(), new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    hidDevice = (BluetoothHidDevice) proxy;
                    Log.i(TAG, "Perfil HID pronto");
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    hidDevice = null;
                    connected = false;
                }
            }, BluetoothProfile.HID_DEVICE);
        }
    }

    @PluginMethod
    public void scanDevices(PluginCall call) {
        if (getPermissionState("bluetooth") != PermissionState.GRANTED) {
            requestPermissionForAlias("bluetooth", call, "btPermissionCallback");
            return;
        }
        scanInternal(call);
    }

    @PermissionCallback
    private void btPermissionCallback(PluginCall call) {
        if (getPermissionState("bluetooth") == PermissionState.GRANTED) {
            scanInternal(call);
        } else {
            call.reject("Permissao de Bluetooth negada. Ative em Ajustes > Apps > Air Controller > Permissoes.");
        }
    }

    private void scanInternal(PluginCall call) {
        if (adapter == null || !adapter.isEnabled()) {
            call.reject("Bluetooth desligado");
            return;
        }
        try {
            Set<BluetoothDevice> paired = adapter.getBondedDevices();
            JSArray devices = new JSArray();
            if (paired != null) {
                for (BluetoothDevice dev : paired) {
                    JSObject obj = new JSObject();
                    obj.put("name", dev.getName() != null ? dev.getName() : "Dispositivo");
                    obj.put("address", dev.getAddress());
                    devices.put(obj);
                }
            }
            JSObject ret = new JSObject();
            ret.put("devices", devices);
            call.resolve(ret);
        } catch (SecurityException e) {
            call.reject("Permissao de Bluetooth nao concedida: " + e.getMessage());
        }
    }

    @PluginMethod
    public void connect(PluginCall call) {
        if (getPermissionState("bluetooth") != PermissionState.GRANTED) {
            call.reject("Permissao de Bluetooth nao concedida. Toque em Parear novamente.");
            return;
        }
        String address = call.getString("address");
        if (address == null) {
            call.reject("MAC nao fornecido");
            return;
        }
        if (hidDevice == null) {
            call.reject("HID nao inicializado");
            return;
        }

        hidExecutor.submit(() -> {
            try {
                targetDevice = adapter.getRemoteDevice(address);

                BluetoothHidDeviceAppSdpSettings sdp =
                        new BluetoothHidDeviceAppSdpSettings(
                                "Air Controller",
                                "Controle HID Bluetooth",
                                "AirController",
                                (byte) 0x00,
                                HID_DESCRIPTOR
                        );

                BluetoothHidDeviceAppQosSettings qosIn =
                        new BluetoothHidDeviceAppQosSettings(
                                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                                800, 9, 0, 11250, 0xffffffff
                        );

                BluetoothHidDeviceAppQosSettings qosOut =
                        new BluetoothHidDeviceAppQosSettings(
                                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                                800, 9, 0, 11250, 0xffffffff
                        );

                boolean registered = hidDevice.registerApp(
                        sdp, qosIn, qosOut,
                        getContext().getMainExecutor(),
                        new BluetoothHidDevice.Callback() {
                            @Override
                            public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean isRegistered) {
                                Log.i(TAG, "Registrado: " + isRegistered);
                                if (isRegistered && targetDevice != null) {
                                    try {
                                        hidDevice.connect(targetDevice);
                                    } catch (SecurityException se) {
                                        Log.e(TAG, "Sem permissao para conectar", se);
                                    }
                                }
                            }

                            @Override
                            public void onConnectionStateChanged(BluetoothDevice device, int state) {
                                if (state == BluetoothHidDevice.STATE_CONNECTED) {
                                    connected = true;
                                    Log.i(TAG, "CONECTADO: " + device.getName());
                                } else if (state == BluetoothHidDevice.STATE_DISCONNECTED) {
                                    connected = false;
                                }
                            }
                        }
                );

                if (!registered) {
                    call.reject("Falha ao registrar HID");
                    return;
                }

                for (int i = 0; i < 50; i++) {
                    if (connected) break;
                    Thread.sleep(100);
                }

                JSObject ret = new JSObject();
                ret.put("success", connected);
                call.resolve(ret);

            } catch (SecurityException se) {
                Log.e(TAG, "Permissao negada", se);
                call.reject("Permissao negada: " + se.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Erro", e);
                call.reject("Erro: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        try {
            if (hidDevice != null && targetDevice != null) {
                hidDevice.disconnect(targetDevice);
            }
            connected = false;
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Erro: " + e.getMessage());
        }
    }

    @PluginMethod
    public void sendKey(PluginCall call) {
        if (!connected || hidDevice == null || targetDevice == null) {
            call.reject("Nao conectado");
            return;
        }
        int keyCode = call.getInt("keyCode", 0);
        String action = call.getString("action", "UP");

        hidExecutor.submit(() -> {
            try {
                byte[] report = new byte[8];
                if ("DOWN".equals(action)) {
                    report[0] = (byte) getModifier(keyCode);
                    report[2] = (byte) androidKeyToHid(keyCode);
                }
                hidDevice.sendReport(targetDevice, 1, report);
                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (SecurityException se) {
                call.reject("Permissao negada: " + se.getMessage());
            } catch (Exception e) {
                call.reject("Erro: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void sendMouse(PluginCall call) {
        if (!connected || hidDevice == null || targetDevice == null) {
            call.reject("Nao conectado");
            return;
        }
        int dx = call.getInt("dx", 0);
        int dy = call.getInt("dy", 0);
        int wheel = call.getInt("wheel", 0);
        int buttons = call.getInt("buttons", 0);

        hidExecutor.submit(() -> {
            try {
                byte[] report = new byte[4];
                report[0] = (byte) buttons;
                report[1] = (byte) dx;
                report[2] = (byte) dy;
                report[3] = (byte) wheel;
                hidDevice.sendReport(targetDevice, 2, report);
                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (SecurityException se) {
                call.reject("Permissao negada: " + se.getMessage());
            } catch (Exception e) {
                call.reject("Erro: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void sendGamepad(PluginCall call) {
        if (!connected || hidDevice == null || targetDevice == null) {
            call.reject("Nao conectado");
            return;
        }
        int buttons = call.getInt("buttons", 0);
        int lx = call.getInt("lx", 0);
        int ly = call.getInt("ly", 0);
        int rx = call.getInt("rx", 0);
        int ry = call.getInt("ry", 0);

        hidExecutor.submit(() -> {
            try {
                byte[] report = new byte[5];
                report[0] = (byte) (buttons & 0xFF);
                report[1] = (byte) lx;
                report[2] = (byte) ly;
                report[3] = (byte) rx;
                report[4] = (byte) ry;
                hidDevice.sendReport(targetDevice, 3, report);
                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (SecurityException se) {
                call.reject("Permissao negada: " + se.getMessage());
            } catch (Exception e) {
                call.reject("Erro: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void sendConsumer(PluginCall call) {
        if (!connected || hidDevice == null || targetDevice == null) {
            call.reject("Nao conectado");
            return;
        }
        int usage = call.getInt("usage", 0);
        String action = call.getString("action", "UP");

        hidExecutor.submit(() -> {
            try {
                byte[] report = new byte[2];
                if ("DOWN".equals(action)) {
                    report[0] = (byte) (usage & 0xFF);
                    report[1] = (byte) ((usage >> 8) & 0xFF);
                }
                hidDevice.sendReport(targetDevice, 4, report);
                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (SecurityException se) {
                call.reject("Permissao negada: " + se.getMessage());
            } catch (Exception e) {
                call.reject("Erro: " + e.getMessage());
            }
        });
    }

    private int androidKeyToHid(int keyCode) {
        switch (keyCode) {
            case 29: return 0x04;
            case 30: return 0x05;
            case 31: return 0x06;
            case 32: return 0x07;
            case 33: return 0x08;
            case 34: return 0x09;
            case 35: return 0x0A;
            case 36: return 0x0B;
            case 37: return 0x0C;
            case 38: return 0x0D;
            case 39: return 0x0E;
            case 40: return 0x0F;
            case 41: return 0x10;
            case 42: return 0x11;
            case 43: return 0x12;
            case 44: return 0x13;
            case 45: return 0x14;
            case 46: return 0x15;
            case 47: return 0x16;
            case 48: return 0x17;
            case 49: return 0x18;
            case 50: return 0x19;
            case 51: return 0x1A;
            case 52: return 0x1B;
            case 53: return 0x1C;
            case 54: return 0x1D;
            case 7:  return 0x27;
            case 8:  return 0x1E;
            case 9:  return 0x1F;
            case 10: return 0x20;
            case 11: return 0x21;
            case 12: return 0x22;
            case 13: return 0x23;
            case 14: return 0x24;
            case 15: return 0x25;
            case 16: return 0x26;
            case 62: return 0x2C;
            case 66: return 0x28;
            case 67: return 0x2A;
            case 61: return 0x2B;
            case 55: return 0x36;
            case 56: return 0x37;
            case 77: return 0x1F; // @ = mesma tecla fisica do "2", com Shift (ver getModifier)
            case 19: return 0x52;
            case 20: return 0x51;
            case 21: return 0x50;
            case 22: return 0x4F;
            case 4:  return 0x29; // VOLTAR = Escape
            case 23: return 0x28;
            default: return 0x00;
        }
    }

    private int getModifier(int keyCode) {
        if (keyCode == 59) return 0x02; // Shift segurando
        if (keyCode == 77) return 0x02; // @ precisa de Shift+2
        return 0x00;
    }
}
