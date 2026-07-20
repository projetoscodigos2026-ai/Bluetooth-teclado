package com.aircontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Set;

@CapacitorPlugin(name = "BluetoothHID")
public class BluetoothHidPlugin extends Plugin {

    private static final String TAG = "BluetoothHID";

    private BluetoothAdapter adapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice targetDevice;
    private boolean connected = false;

    // ===== DESCRITOR HID (Teclado Report 1 + Mouse Report 2 + Gamepad Report 3) =====
    private static final byte[] HID_DESCRIPTOR = {
        // ---- TECLADO (Report ID 1) ----
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

        // ---- MOUSE (Report ID 2) ----
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
        (byte) 0xC0,
        (byte) 0xC0,

        // ---- GAMEPAD (Report ID 3) ----
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
        (byte) 0xC0
    };

    @Override
    public void load() {
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(getContext(), new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    hidDevice = (BluetoothHidDevice) proxy;
                    Log.i(TAG, "Perfil HID conectado");
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    hidDevice = null;
                    connected = false;
                    Log.w(TAG, "Perfil HID desconectado");
                }
            }, BluetoothProfile.HID_DEVICE);
        }
    }

    // ========== LISTAR DISPOSITIVOS PAREADOS ==========
    @PluginMethod
    public void scanDevices(PluginCall call) {
        if (adapter == null || !adapter.isEnabled()) {
            call.reject("Bluetooth desligado ou indisponivel");
            return;
        }
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
    }

    // ========== CONECTAR COMO HID NO PROJETOR ==========
    @PluginMethod
    public void connect(PluginCall call) {
        String address = call.getString("address");
        if (address == null) {
            call.reject("Endereco MAC nao fornecido");
            return;
        }
        if (hidDevice == null) {
            call.reject("Perfil HID nao inicializado. Tente novamente.");
            return;
        }

        new Thread(() -> {
            try {
                targetDevice = adapter.getRemoteDevice(address);

                BluetoothHidDeviceAppSdpSettings sdp =
                        new BluetoothHidDeviceAppSdpSettings(
                                "Air Controller",
                                "Controle HID via Bluetooth",
                                "AirController",
                                0x00,
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
                                Log.i(TAG, "App HID registrado: " + isRegistered);
                                if (isRegistered && targetDevice != null) {
                                    hidDevice.connect(targetDevice);
                                }
                            }

                            @Override
                            public void onConnectionStateChanged(BluetoothDevice device, int state) {
                                if (state == BluetoothHidDevice.STATE_CONNECTED) {
                                    connected = true;
                                    Log.i(TAG, "CONECTADO: " + device.getName());
                                } else if (state == BluetoothHidDevice.STATE_DISCONNECTED) {
                                    connected = false;
                                    Log.w(TAG, "DESCONECTADO");
                                }
                            }
                        }
                );

                if (!registered) {
                    call.reject("Falha ao registrar app HID");
                    return;
                }

                for (int i = 0; i < 50; i++) {
                    if (connected) break;
                    Thread.sleep(100);
                }

                JSObject ret = new JSObject();
                ret.put("success", connected);
                call.resolve(ret);

            } catch (Exception e) {
                Log.e(TAG, "Erro na conexao", e);
                call.reject("Erro: " + e.getMessage());
            }
        }).start();
    }

    // ========== DESCONECTAR ==========
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

    // ========== ENVIAR TECLA (Report ID 1) ==========
    @PluginMethod
    public void sendKey(PluginCall call) {
        if (!connected || hidDevice == null || targetDevice == null) {
            call.reject("Nao conectado");
            return;
        }
        int keyCode = call.getInt("keyCode", 0);
        String action = call.getString("action", "UP");

        new Thread(() -> {
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
            } catch (Exception e) {
                call.reject("Erro: " + e.getMessage());
            }
        }).start();
    }

    // ========== ENVIAR MOUSE (Report ID 2) ==========
    @PluginMethod
    public void sendMouse(PluginCall call) {
        if (!connected || hidDevice == null || targetDevice == null) {
            call.reject("Nao conectado");
            return;
        }
        int dx = call.getInt("dx", 0);
        int dy = call.getInt("dy", 0);
        int buttons = call.getInt("buttons", 0);

        new Thread(() -> {
            try {
                byte[] report = new byte[4];
                report[0] = (byte) buttons;
                report[1] = (byte) dx;
                report[2] = (byte) dy;
                report[3] = 0;
                hidDevice.sendReport(targetDevice, 2, report);
                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Erro: " + e.getMessage());
            }
        }).start();
    }

    // ========== ENVIAR GAMEPAD (Report ID 3) ==========
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

        new Thread(() -> {
            try {
                byte[] report = new byte[6];
                report[0] = (byte) (buttons & 0xFF);
                report[1] = (byte) ((buttons >> 8) & 0xFF);
                report[2] = (byte) lx;
                report[3] = (byte) ly;
                report[4] = (byte) rx;
                report[5] = (byte) ry;
                hidDevice.sendReport(targetDevice, 3, report);
                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Erro: " + e.getMessage());
            }
        }).start();
    }

    // ========== MAPEAMENTO ANDROID KEYCODE -> HID USAGE ==========
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
            case 77: return 0x1F;
            case 19: return 0x52;
            case 20: return 0x51;
            case 21: return 0x50;
            case 22: return 0x4F;
            case 4:  return 0x29;
            case 3:  return 0x29;
            case 23: return 0x28;
            case 24: return 0x80;
            case 25: return 0x81;
            case 26: return 0x30;
            default: return 0x00;
        }
    }

    private int getModifier(int keyCode) {
        if (keyCode == 59) return 0x02;
        return 0x00;
    }
}
