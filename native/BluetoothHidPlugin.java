package com.aircontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.ParcelUuid;
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

    // ===== DESCRITOR HID REAL (Teclado + Mouse + Gamepad) =====
    private static final byte[] HID_DESCRIPTOR = {
        // ---- TECLADO (Report ID 1) ----
        (byte)0x05, (byte)0x01,       // Usage Page (Generic Desktop)
        (byte)0x09, (byte)0x06,       // Usage (Keyboard)
        (byte)0xA1, (byte)0x01,       // Collection (Application)
        (byte)0x85, (byte)0x01,       //   Report ID (1)
        (byte)0x05, (byte)0x07,       //   Usage Page (Key Codes)
        (byte)0x19, (byte)0xE0,       //   Usage Minimum (224)
        (byte)0x29, (byte)0xE7,       //   Usage Maximum (231)
        (byte)0x15, (byte)0x00,       //   Logical Minimum (0)
        (byte)0x25, (byte)0x01,       //   Logical Maximum (1)
        (byte)0x75, (byte)0x01,       //   Report Size (1)
        (byte)0x95, (byte)0x08,       //   Report Count (8)
        (byte)0x81, (byte)0x02,       //   Input (Data,Var,Abs) → Modifier
        (byte)0x95, (byte)0x01,       //   Report Count (1)
        (byte)0x75, (byte)0x08,       //   Report Size (8)
        (byte)0x81, (byte)0x01,       //   Input (Const) → Reserved
        (byte)0x95, (byte)0x06,       //   Report Count (6)
        (byte)0x75, (byte)0x08,       //   Report Size (8)
        (byte)0x15, (byte)0x00,       //   Logical Minimum (0)
        (byte)0x25, (byte)0x65,       //   Logical Maximum (101)
        (byte)0x05, (byte)0x07,       //   Usage Page (Key Codes)
        (byte)0x19, (byte)0x00,       //   Usage Minimum (0)
        (byte)0x29, (byte)0x65,       //   Usage Maximum (101)
        (byte)0x81, (byte)0x00,       //   Input (Data,Array) → 6 teclas
        (byte)0xC0,                   // End Collection

        // ---- MOUSE (Report ID 2) ----
        (byte)0x05, (byte)0x01,       // Usage Page (Generic Desktop)
        (byte)0x09, (byte)0x02,       // Usage (Mouse)
        (byte)0xA1, (byte)0x01,       // Collection (Application)
        (byte)0x85, (byte)0x02,       //   Report ID (2)
        (byte)0x09, (byte)0x01,       //   Usage (Pointer)
        (byte)0xA1, (byte)0x00,       //   Collection (Physical)
        (byte)0x05, (byte)0x09,       //     Usage Page (Buttons)
        (byte)0x19, (byte)0x01,       //     Usage Minimum (1)
        (byte)0x29, (byte)0x03,       //     Usage Maximum (3)
        (byte)0x15, (byte)0x00,       //     Logical Minimum (0)
        (byte)0x25, (byte)0x01,       //     Logical Maximum (1)
        (byte)0x95, (byte)0x03,       //     Report Count (3)
        (byte)0x75, (byte)0x01,       //     Report Size (1)
        (byte)0x81, (byte)0x02,       //     Input (Data,Var,Abs) → Botões
        (byte)0x95, (byte)0x01,       //     Report Count (1)
        (byte)0x75, (byte)0x05,       //     Report Size (5)
        (byte)0x81, (byte)0x01,       //     Input (Const) → Padding
        (byte)0x05, (byte)0x01,       //     Usage Page (Generic Desktop)
        (byte)0x09, (byte)0x30,       //     Usage (X)
        (byte)0x09, (byte)0x31,       //     Usage (Y)
        (byte)0x15, (byte)0x81,       //     Logical Minimum (-127)
        (byte)0x25, (byte)0x7F,       //     Logical Maximum (127)
        (byte)0x75, (byte)0x08,       //     Report Size (8)
        (byte)0x95, (byte)0x02,       //     Report Count (2)
        (byte)0x81, (byte)0x06,       //     Input (Data,Var,Rel) → X,Y
        (byte)0xC0,                   //   End Collection
        (byte)0xC0,                   // End Collection

        // ---- GAMEPAD (Report ID 3) ----
        (byte)0x05, (byte)0x01,       // Usage Page (Generic Desktop)
        (byte)0x09, (byte)0x05,       // Usage (Game Pad)
        (byte)0xA1, (byte)0x01,       // Collection (Application)
        (byte)0x85, (byte)0x03,       //   Report ID (3)
        (byte)0x05, (byte)0x09,       //   Usage Page (Buttons)
        (byte)0x19, (byte)0x01,       //   Usage Minimum (1)
        (byte)0x29, (byte)0x08,       //   Usage Maximum (8)
        (byte)0x15, (byte)0x00,       //   Logical Minimum (0)
        (byte)0x25, (byte)0x01,       //   Logical Maximum (1)
        (byte)0x95, (byte)0x08,       //   Report Count (8)
        (byte)0x75, (byte)0x01,       //   Report Size (1)
        (byte)0x81, (byte)0x02,       //   Input (Data,Var,Abs) → 8 botões
        (byte)0x05, (byte)0x01,       //   Usage Page (Generic Desktop)
        (byte)0x09, (byte)0x30,       //   Usage (X)
        (byte)0x09, (byte)0x31,       //   Usage (Y)
        (byte)0x09, (byte)0x32,       //   Usage (Z)
        (byte)0x09, (byte)0x35,       //   Usage (Rz)
        (byte)0x15, (byte)0x81,       //   Logical Minimum (-127)
        (byte)0x25, (byte)0x7F,       //   Logical Maximum (127)
        (byte)0x75, (byte)0x08,       //   Report Size (8)
        (byte)0x95, (byte)0x04,       //   Report Count (4)
        (byte)0x81, (byte)0x02,       //   Input (Data,Var,Abs) → 4 eixos
        (byte)0xC0                    // End Collection
    };

    @Override
    public void load() {
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(getContext(), new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    hidDevice = (BluetoothHidDevice) proxy;
                    Log.i(TAG, "BluetoothHidDevice proxy conectado");
                }
                @Override
                public void onServiceDisconnected(int profile) {
                    hidDevice = null;
                    connected = false;
                    Log.w(TAG, "BluetoothHidDevice proxy desconectado");
                }
            }, BluetoothProfile.HID_DEVICE);
        }
    }

    // ========== LISTAR DISPOSITIVOS PAREADOS ==========
    @PluginMethod
    public void scanDevices(PluginCall call) {
        if (adapter == null || !adapter.isEnabled()) {
            call.reject("Bluetooth desligado ou indisponível");
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
            call.reject("Endereço MAC não fornecido");
            return;
        }
        if (hidDevice == null) {
            call.reject("Perfil HID não inicializado. Tente novamente.");
            return;
        }

        new Thread(() -> {
            try {
                targetDevice = adapter.getRemoteDevice(address);

                // Registra o celular como dispositivo HID real
                BluetoothHidDeviceAppSdpSettings sdp =
                    new BluetoothHidDeviceAppSdpSettings(
                        "Air Controller",
                        "Controle HID via Bluetooth",
                        "AirController",
                        BluetoothHidDeviceAppSdpSettings.SUBCLASS1_COMBO,
                        HID_DESCRIPTOR
                    );

                BluetoothHidDeviceAppQosSettings qosIn =
                    new BluetoothHidDeviceAppQosSettings(
                        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                        800, 9, 0, 11250,
                        BluetoothHidDeviceAppQosSettings.MAX
                    );

                BluetoothHidDeviceAppQosSettings qosOut =
                    new BluetoothHidDeviceAppQosSettings(
                        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                        800, 9, 0, 11250,
                        BluetoothHidDeviceAppQosSettings.MAX
                    );

                boolean registered = hidDevice.registerApp(
                    sdp, qosIn, qosOut,
                    getContext().getMainExecutor(),
                    new BluetoothHidDevice.Callback() {
                        @Override
                        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
                            Log.i(TAG, "App HID registrado: " + registered);
                            if (registered) {
                                hidDevice.connect(targetDevice);
                            }
                        }
                        @Override
                        public void onConnectionStateChanged(BluetoothDevice device, int state) {
                            if (state == BluetoothHidDevice.STATE_CONNECTED) {
                                connected = true;
                                Log.i(TAG, "CONECTADO ao projetor: " + device.getName());
                            } else if (state == BluetoothHidDevice.STATE_DISCONNECTED) {
                                connected = false;
                                Log.w(TAG, "Desconectado do projetor");
                            }
                        }
                    }
                );

                if (!registered) {
                    call.reject("Falha ao registrar app HID");
                    return;
                }

                // Aguarda conexão (timeout 5s)
                for (int i = 0; i < 50; i++) {
                    if (connected) break;
                    Thread.sleep(100);
                }

                JSObject ret = new JSObject();
                ret.put("success", connected);
                call.resolve(ret);

            } catch (Exception e) {
                Log.e(TAG, "Erro na conexão", e);
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
            call.reject("Não conectado");
            return;
        }
        int keyCode = call.getInt("keyCode", 0);
        String action = call.getString("action", "UP");

        new Thread(() -> {
            try {
                byte[] report = new byte[8]; // [modifier, reserved, key1..key6]
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
            call.reject("Não conectado");
            return;
        }
        int dx = call.getInt("dx", 0);
        int dy = call.getInt("dy", 0);
        int buttons = call.getInt("buttons", 0);

        new Thread(() -> {
            try {
                byte[] report = new byte[4]; // [buttons, X, Y, wheel]
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
            call.reject("Não conectado");
            return;
        }
        int buttons = call.getInt("buttons", 0);
        int lx = call.getInt("lx", 0);
        int ly = call.getInt("ly", 0);
        int rx = call.getInt("rx", 0);
        int ry = call.getInt("ry", 0);

        new Thread(() -> {
            try {
                byte[] report = new byte[6]; // [buttons, LX, LY, RX, RY]
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

    // ========== MAPEAMENTO ANDROID KEYCODE → HID ==========
    private int androidKeyToHid(int keyCode) {
        switch (keyCode) {
            case 29: return 0x04; // A
            case 30: return 0x05; // B
            case 31: return 0x06; // C
            case 32: return 0x07; // D
            case 33: return 0x08; // E
            case 34: return 0x09; // F
            case 35: return 0x0A; // G
            case 36: return 0x0B; // H
            case 37: return 0x0C; // I
            case 38: return 0x0D; // J
            case 39: return 0x0E; // K
            case 40: return 0x0F; // L
            case 41: return 0x10; // M
            case 42: return 0x11; // N
            case 43: return 0x12; // O
            case 44: return 0x13; // P
            case 45: return 0x14; // Q
            case 46: return 0x15; // R
            case 47: return 0x16; // S
            case 48: return 0x17; // T
            case 49: return 0x18; // U
            case 50: return 0x19; // V
            case 51: return 0x1A; // W
            case 52: return 0x1B; // X
            case 53: return 0x1C; // Y
            case 54: return 0x1D; // Z
            case 7:  return 0x27; // 0
            case 8:  return 0x1E; // 1
            case 9:  return 0x1F; // 2
            case 10: return 0x20; // 3
            case 11: return 0x21; // 4
            case 12: return 0x22; // 5
            case 13: return 0x23; // 6
            case 14: return 0x24; // 7
            case 15: return 0x25; // 8
            case 16: return 0x26; // 9
            case 62: return 0x2C; // ESPAÇO
            case 66: return 0x28; // ENTER
            case 67: return 0x2A; // BACKSPACE
            case 61: return 0x2B; // TAB
            case 55: return 0x36; // VÍRGULA
            case 56: return 0x37; // PONTO
            case 77: return 0x1F; // @
            case 19: return 0x52; // SETA CIMA
            case 20: return 0x51; // SETA BAIXO
            case 21: return 0x50; // SETA ESQUERDA
            case 22: return 0x4F; // SETA DIREITA
            case 4:  return 0x29; // BACK (ESC)
            case 3:  return 0x29; // HOME (ESC)
            case 23: return 0x28; // DPAD CENTER (ENTER)
            case 24: return 0x80; // VOLUME UP
            case 25: return 0x81; // VOLUME DOWN
            case 26: return 0x30; // POWER
            default: return 0x00;
        }
    }

    private int getModifier(int keyCode) {
        if (keyCode == 59) return 0x02; // Shift esquerdo
        return 0x00;
    }
}
