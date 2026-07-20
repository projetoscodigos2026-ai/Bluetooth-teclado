package com.aircontroller;

import android.bluetooth.*;
import android.content.Context;
import android.os.ParcelUuid;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;
import com.getcapacitor.JSArray;
import java.util.Set;
import java.util.UUID;

@CapacitorPlugin(name = "BluetoothHID")
public class BluetoothHidPlugin extends Plugin {

    private BluetoothAdapter adapter;
    private BluetoothDevice targetDevice;
    private BluetoothSocket socket;
    private boolean connected = false;

    // UUID do perfil HID (Bluetooth Classic)
    private static final UUID HID_UUID =
        UUID.fromString("00001124-0000-1000-8000-00805F9B34FB");

    @Override
    public void load() {
        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    // ========== SCAN ==========
    @PluginMethod
    public void scanDevices(PluginCall call) {
        if (adapter == null) {
            call.reject("Bluetooth não disponível");
            return;
        }
        if (!adapter.isEnabled()) {
            call.reject("Bluetooth desligado");
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

    // ========== CONECTAR (Bluetooth Classic HID) ==========
    @PluginMethod
    public void connect(PluginCall call) {
        String address = call.getString("address");
        if (address == null) {
            call.reject("Endereço MAC não fornecido");
            return;
        }

        new Thread(() -> {
            try {
                targetDevice = adapter.getRemoteDevice(address);
                // Tenta conexão via perfil HID
                socket = targetDevice.createRfcommSocketToServiceRecord(HID_UUID);
                adapter.cancelDiscovery();
                socket.connect();
                connected = true;

                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (Exception e) {
                // Fallback: tenta via reflection (método oculto do Android)
                try {
                    java.lang.reflect.Method m =
                        targetDevice.getClass().getMethod("createRfcommSocket", int.class);
                    socket = (BluetoothSocket) m.invoke(targetDevice, 1);
                    socket.connect();
                    connected = true;

                    JSObject ret = new JSObject();
                    ret.put("success", true);
                    call.resolve(ret);
                } catch (Exception ex) {
                    call.reject("Falha na conexão: " + ex.getMessage());
                }
            }
        }).start();
    }

    // ========== DESCONECTAR ==========
    @PluginMethod
    public void disconnect(PluginCall call) {
        try {
            if (socket != null) socket.close();
            connected = false;
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Erro ao desconectar: " + e.getMessage());
        }
    }

    // ========== ENVIAR TECLA (HID Keyboard Report) ==========
    @PluginMethod
    public void sendKey(PluginCall call) {
        if (!connected || socket == null) {
            call.reject("Não conectado");
            return;
        }

        int keyCode = call.getInt("keyCode");
        String action = call.getString("action"); // "DOWN" ou "UP"

        new Thread(() -> {
            try {
                // HID Keyboard Report: [modifier, reserved, key1..key6]
                byte[] report = new byte[8];

                if ("DOWN".equals(action)) {
                    // Mapeia Android KeyCode para HID Usage ID
                    int hidCode = androidKeyToHid(keyCode);
                    int modifier = getModifier(keyCode);
                    report[0] = (byte) modifier;
                    report[2] = (byte) hidCode;
                }
                // Se action == "UP", report fica tudo zero (tecla solta)

                socket.getOutputStream().write(report);
                socket.getOutputStream().flush();

                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Erro ao enviar tecla: " + e.getMessage());
            }
        }).start();
    }

    // ========== ENVIAR MOUSE (HID Mouse Report) ==========
    @PluginMethod
    public void sendMouse(PluginCall call) {
        if (!connected || socket == null) {
            call.reject("Não conectado");
            return;
        }

        int dx = call.getInt("dx", 0);
        int dy = call.getInt("dy", 0);
        int buttons = call.getInt("buttons", 0);

        new Thread(() -> {
            try {
                // HID Mouse Report: [buttons, X, Y, wheel]
                byte[] report = new byte[4];
                report[0] = (byte) buttons;
                report[1] = (byte) dx;
                report[2] = (byte) dy;
                report[3] = 0;

                socket.getOutputStream().write(report);
                socket.getOutputStream().flush();

                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Erro ao enviar mouse: " + e.getMessage());
            }
        }).start();
    }

    // ========== ENVIAR GAMEPAD (HID Gamepad Report) ==========
    @PluginMethod
    public void sendGamepad(PluginCall call) {
        if (!connected || socket == null) {
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
                // HID Gamepad Report: [buttons(2), LX, LY, RX, RY]
                byte[] report = new byte[6];
                report[0] = (byte) (buttons & 0xFF);
                report[1] = (byte) ((buttons >> 8) & 0xFF);
                report[2] = (byte) lx;
                report[3] = (byte) ly;
                report[4] = (byte) rx;
                report[5] = (byte) ry;

                socket.getOutputStream().write(report);
                socket.getOutputStream().flush();

                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Erro ao enviar gamepad: " + e.getMessage());
            }
        }).start();
    }

    // ========== MAPEAMENTO ANDROID KEYCODE → HID USAGE ==========
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
            case 77: return 0x1F; // @ (Shift+2)
            case 19: return 0x52; // SETA CIMA
            case 20: return 0x51; // SETA BAIXO
            case 21: return 0x50; // SETA ESQUERDA
            case 22: return 0x4F; // SETA DIREITA
            case 4:  return 0x29; // ESCAPE (Back)
            case 3:  return 0x29; // HOME (ESC no HID)
            case 23: return 0x28; // DPAD CENTER (Enter)
            default: return 0x00;
        }
    }

    private int getModifier(int keyCode) {
        if (keyCode == 59) return 0x02; // Shift esquerdo
        return 0x00;
    }
}
