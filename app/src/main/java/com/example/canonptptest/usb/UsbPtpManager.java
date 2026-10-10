package com.example.canonptptest.usb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

import java.util.HashMap;

public class UsbPtpManager {

    public static final String ACTION_USB_PERMISSION =
            "com.example.canonptptest.USB_PERMISSION";

    private final Context context;
    private final UsbManager usbManager;

    private final Logger logger;
    private final Callback callback;

    private UsbDevice canonDevice;

    private UsbDeviceConnection connection;
    private UsbInterface ptpInterface;

    private UsbEndpoint bulkIn;
    private UsbEndpoint bulkOut;
    private UsbEndpoint interruptIn;

    public interface Logger {

        void log(String message);
    }

    public interface Callback {

        void onDeviceFound(
                UsbDevice device
        );

        void onPermissionGranted(
                UsbDevice device
        );

        void onPermissionDenied();

        void onPtpConnectionReady(
                UsbDeviceConnection connection,
                UsbInterface ptpInterface,
                UsbEndpoint bulkIn,
                UsbEndpoint bulkOut,
                UsbEndpoint interruptIn
        );

        void onError(
                String message
        );
    }

    public UsbPtpManager(
            Context context,
            Logger logger,
            Callback callback
    ) {

        this.context =
                context.getApplicationContext();

        this.usbManager =
                (UsbManager) this.context.getSystemService(
                        Context.USB_SERVICE
                );

        this.logger = logger;
        this.callback = callback;
    }

    // =========================================================
    // USB PERMISSION RECEIVER
    // =========================================================

    private final BroadcastReceiver usbPermissionReceiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(
                        Context context,
                        Intent intent
                ) {

                    log(
                            "------------------------------------------------"
                    );

                    log(
                            "USB permission broadcast received"
                    );

                    log(
                            "Action: " +
                                    intent.getAction()
                    );

                    if (!ACTION_USB_PERMISSION.equals(
                            intent.getAction()
                    )) {

                        log(
                                "Unknown USB permission action"
                        );

                        return;
                    }

                    UsbDevice device;

                    if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.TIRAMISU) {

                        device =
                                intent.getParcelableExtra(
                                        UsbManager.EXTRA_DEVICE,
                                        UsbDevice.class
                                );

                    } else {

                        device =
                                intent.getParcelableExtra(
                                        UsbManager.EXTRA_DEVICE
                                );
                    }

                    boolean granted =
                            intent.getBooleanExtra(
                                    UsbManager.EXTRA_PERMISSION_GRANTED,
                                    false
                            );

                    log(
                            "Permission result: " +
                                    (
                                            granted
                                                    ? "GRANTED"
                                                    : "DENIED"
                                    )
                    );

                    if (device != null) {

                        log(
                                "Device returned by permission broadcast:"
                        );

                        log(
                                "  Manufacturer: " +
                                        device.getManufacturerName()
                        );

                        log(
                                "  Product: " +
                                        device.getProductName()
                        );

                        log(
                                String.format(
                                        "  VID: %04X",
                                        device.getVendorId()
                                )
                        );

                        log(
                                String.format(
                                        "  PID: %04X",
                                        device.getProductId()
                                )
                        );

                    } else {

                        log(
                                "WARNING: USB device is NULL"
                        );
                    }

                    if (granted && device != null) {

                        log(
                                "USB permission: GRANTED"
                        );

                        canonDevice =
                                device;

                        if (callback != null) {

                            callback.onPermissionGranted(
                                    device
                            );
                        }

                        connectUsbDevice(
                                device
                        );

                    } else {

                        log(
                                "USB permission: DENIED"
                        );

                        if (callback != null) {

                            callback.onPermissionDenied();
                        }
                    }
                }
            };

    // =========================================================
    // REGISTER / UNREGISTER
    // =========================================================

    public void registerReceiver() {

        IntentFilter filter =
                new IntentFilter(
                        ACTION_USB_PERMISSION
                );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            context.registerReceiver(
                    usbPermissionReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED
            );

        } else {

            context.registerReceiver(
                    usbPermissionReceiver,
                    filter
            );
        }

        log(
                "USB permission receiver registered"
        );
    }

    public void unregisterReceiver() {

        try {

            context.unregisterReceiver(
                    usbPermissionReceiver
            );

            log(
                    "USB permission receiver unregistered"
            );

        } catch (IllegalArgumentException ignored) {

            // Ya estaba desregistrado.
        }
    }

    // =========================================================
    // DETECT USB DEVICES
    // =========================================================

    public void detectUsbDevices() {

        log(
                "------------------------------------------------"
        );

        log(
                "Scanning USB devices..."
        );

        HashMap<String, UsbDevice> devices =
                usbManager.getDeviceList();

        log(
                "USB devices found: " +
                        devices.size()
        );

        UsbDevice candidate =
                null;

        for (UsbDevice device :
                devices.values()) {

            log("");
            log("Device:");

            log(
                    "  Name: " +
                            device.getDeviceName()
            );

            log(
                    "  Manufacturer: " +
                            device.getManufacturerName()
            );

            log(
                    "  Product: " +
                            device.getProductName()
            );

            log(
                    String.format(
                            "  VID: %04X",
                            device.getVendorId()
                    )
            );

            log(
                    String.format(
                            "  PID: %04X",
                            device.getProductId()
                    )
            );

            boolean isPtp =
                    inspectInterfaces(
                            device
                    );

            if (isPtp) {

                candidate =
                        device;

                log("");
                log(
                        ">>> CANDIDATE DEVICE FOUND <<<"
                );
            }
        }

        if (candidate == null) {

            log("");
            log(
                    "No Canon/PTP device found"
            );

            if (callback != null) {

                callback.onError(
                        "No Canon/PTP device found"
                );
            }

            return;
        }

        canonDevice =
                candidate;

        log("");
        log(
                "Selected device:"
        );

        log(
                String.format(
                        "VID = %04X",
                        candidate.getVendorId()
                )
        );

        log(
                String.format(
                        "PID = %04X",
                        candidate.getProductId()
                )
        );

        if (usbManager.hasPermission(candidate)) {

            log(
                    "USB permission already granted"
            );

            connectUsbDevice(
                    candidate
            );

        } else {

            log(
                    "USB permission required"
            );

            if (callback != null) {

                callback.onDeviceFound(
                        candidate
                );
            }
        }
    }

    // =========================================================
    // INSPECT INTERFACES
    // =========================================================

    private boolean inspectInterfaces(
            UsbDevice device
    ) {

        boolean foundPtp =
                false;

        log(
                "  Interfaces: " +
                        device.getInterfaceCount()
        );

        for (
                int i = 0;
                i < device.getInterfaceCount();
                i++
        ) {

            UsbInterface usbInterface =
                    device.getInterface(i);

            int clazz =
                    usbInterface.getInterfaceClass();

            int subclass =
                    usbInterface.getInterfaceSubclass();

            int protocol =
                    usbInterface.getInterfaceProtocol();

            log("");

            log(
                    "  Interface " +
                            i
            );

            log(
                    String.format(
                            "    Class: %02X",
                            clazz
                    )
            );

            log(
                    String.format(
                            "    Subclass: %02X",
                            subclass
                    )
            );

            log(
                    String.format(
                            "    Protocol: %02X",
                            protocol
                    )
            );

            if (
                    clazz == 0x06 &&
                    subclass == 0x01 &&
                    protocol == 0x01
            ) {

                foundPtp =
                        true;

                log(
                        "    >>> STILL IMAGE / PTP <<<"
                );

                inspectEndpoints(
                        usbInterface
                );
            }
        }

        return foundPtp;
    }

    // =========================================================
    // INSPECT ENDPOINTS
    // =========================================================

    private void inspectEndpoints(
            UsbInterface usbInterface
    ) {

        /*
         * Limpiamos referencias anteriores.
         */

        bulkIn = null;
        bulkOut = null;
        interruptIn = null;

        for (
                int i = 0;
                i < usbInterface.getEndpointCount();
                i++
        ) {

            UsbEndpoint endpoint =
                    usbInterface.getEndpoint(i);

            int address =
                    endpoint.getAddress();

            int direction =
                    endpoint.getDirection();

            int type =
                    endpoint.getType();

            log(
                    String.format(
                            "    Endpoint: 0x%02X",
                            address
                    )
            );

            log(
                    "      Direction: " +
                            directionToString(
                                    direction
                            )
            );

            log(
                    "      Type: " +
                            typeToString(
                                    type
                            )
            );

            if (
                    type ==
                            UsbConstants.USB_ENDPOINT_XFER_BULK
            ) {

                if (
                        direction ==
                                UsbConstants.USB_DIR_IN
                ) {

                    bulkIn =
                            endpoint;

                } else {

                    bulkOut =
                            endpoint;
                }
            }

            if (
                    type ==
                            UsbConstants.USB_ENDPOINT_XFER_INT
                    &&
                    direction ==
                            UsbConstants.USB_DIR_IN
            ) {

                interruptIn =
                        endpoint;
            }
        }

        if (bulkIn != null) {

            log(
                    String.format(
                            "    BULK IN  = 0x%02X",
                            bulkIn.getAddress()
                    )
            );
        }

        if (bulkOut != null) {

            log(
                    String.format(
                            "    BULK OUT = 0x%02X",
                            bulkOut.getAddress()
                    )
            );
        }

        if (interruptIn != null) {

            log(
                    String.format(
                            "    INT IN   = 0x%02X",
                            interruptIn.getAddress()
                    )
            );
        }

        if (bulkIn == null) {

            log(
                    "    ERROR: BULK IN endpoint missing"
            );
        }

        if (bulkOut == null) {

            log(
                    "    ERROR: BULK OUT endpoint missing"
            );
        }

        if (interruptIn == null) {

            log(
                    "    WARNING: INTERRUPT IN endpoint missing"
            );
        }
    }

    // =========================================================
    // REQUEST USB PERMISSION
    // =========================================================

    public void requestPermission(
            UsbDevice device
    ) {

        if (device == null) {

            log(
                    "ERROR: Cannot request permission for null device"
            );

            return;
        }

        log(
                "Requesting USB permission..."
        );

        Intent permissionIntentAction =
                new Intent(
                        ACTION_USB_PERMISSION
                );

        /*
         * Hacemos el Intent explícito para
         * nuestro paquete.
         */

        permissionIntentAction.setPackage(
                context.getPackageName()
        );

        PendingIntent permissionIntent =
                PendingIntent.getBroadcast(
                        context,
                        0,
                        permissionIntentAction,
                        PendingIntent.FLAG_MUTABLE |
                                PendingIntent.FLAG_UPDATE_CURRENT
                );

        log(
                "Calling UsbManager.requestPermission()..."
        );

        log(
                "PendingIntent mutable: YES"
        );

        log(
                "Target package: " +
                        context.getPackageName()
        );

        usbManager.requestPermission(
                device,
                permissionIntent
        );

        log(
                "requestPermission() called"
        );
    }

    // =========================================================
    // CONNECT USB DEVICE
    // =========================================================

    private void connectUsbDevice(
            UsbDevice device
    ) {

        log(
                "------------------------------------------------"
        );

        log(
                "Opening USB device..."
        );

        if (device == null) {

            log(
                    "ERROR: Device is null"
            );

            if (callback != null) {

                callback.onError(
                        "USB device is null"
                );
            }

            return;
        }

        connection =
                usbManager.openDevice(
                        device
                );

        if (connection == null) {

            log(
                    "openDevice(): FAILED"
            );

            if (callback != null) {

                callback.onError(
                        "Could not open USB device"
                );
            }

            return;
        }

        log(
                "UsbDeviceConnection created"
        );

        log(
                "openDevice(): SUCCESS"
        );

        /*
         * Buscamos nuevamente la interfaz PTP.
         */

        ptpInterface =
                null;

        for (
                int i = 0;
                i < device.getInterfaceCount();
                i++
        ) {

            UsbInterface usbInterface =
                    device.getInterface(i);

            if (
                    usbInterface.getInterfaceClass()
                            == 0x06
                    &&
                    usbInterface.getInterfaceSubclass()
                            == 0x01
                    &&
                    usbInterface.getInterfaceProtocol()
                            == 0x01
            ) {

                ptpInterface =
                        usbInterface;

                break;
            }
        }

        if (ptpInterface == null) {

            log(
                    "ERROR: PTP interface not found"
            );

            closeConnection();

            if (callback != null) {

                callback.onError(
                        "PTP interface not found"
                );
            }

            return;
        }

        log(
                "PTP interface found: " +
                        ptpInterface.getId()
        );

        log(
                "Claiming PTP interface..."
        );

        boolean claimed =
                connection.claimInterface(
                        ptpInterface,
                        true
                );

        if (!claimed) {

            log(
                    "claimInterface(): FAILED"
            );

            closeConnection();

            if (callback != null) {

                callback.onError(
                        "Could not claim PTP interface"
                );
            }

            return;
        }

        log(
                "claimInterface(): SUCCESS"
        );

        /*
         * Encontramos los endpoints.
         */

        inspectEndpoints(
                ptpInterface
        );

        if (
                bulkIn == null ||
                bulkOut == null
        ) {

            log(
                    "ERROR: Required PTP endpoints missing"
            );

            closeConnection();

            if (callback != null) {

                callback.onError(
                        "Required PTP endpoints missing"
                );
            }

            return;
        }

        log("");
        log(
                "USB PTP CONNECTION READY"
        );

        /*
         * Entregamos todos los recursos a MainActivity.
         */

        if (callback != null) {

            callback.onPtpConnectionReady(
                    connection,
                    ptpInterface,
                    bulkIn,
                    bulkOut,
                    interruptIn
            );
        }
    }

    // =========================================================
    // CLOSE
    // =========================================================

    public void closeConnection() {

        if (connection != null) {

            try {

                if (ptpInterface != null) {

                    connection.releaseInterface(
                            ptpInterface
                    );
                }

            } catch (Exception ignored) {
            }

            try {

                connection.close();

            } catch (Exception ignored) {
            }

            connection =
                    null;
        }

        ptpInterface =
                null;

        bulkIn =
                null;

        bulkOut =
                null;

        interruptIn =
                null;

        log(
                "USB connection closed"
        );
    }

    public void close() {

        closeConnection();

        unregisterReceiver();
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public UsbDevice getCanonDevice() {
        return canonDevice;
    }

    public UsbDeviceConnection getConnection() {
        return connection;
    }

    public UsbInterface getPtpInterface() {
        return ptpInterface;
    }

    public UsbEndpoint getBulkIn() {
        return bulkIn;
    }

    public UsbEndpoint getBulkOut() {
        return bulkOut;
    }

    public UsbEndpoint getInterruptIn() {
        return interruptIn;
    }

    public boolean hasPermission(
        UsbDevice device
    ) {

        if (device == null) {

            return false;
        }

        return usbManager.hasPermission(
                device
        );
    }

    public boolean isConnected() {
        return connection != null;
    }

    // =========================================================
    // UTILITIES
    // =========================================================

    private String directionToString(
            int direction
    ) {

        if (
                direction ==
                        UsbConstants.USB_DIR_IN
        ) {

            return "IN";
        }

        return "OUT";
    }

    private String typeToString(
            int type
    ) {

        switch (type) {

            case UsbConstants.USB_ENDPOINT_XFER_CONTROL:
                return "CONTROL";

            case UsbConstants.USB_ENDPOINT_XFER_ISOC:
                return "ISOCHRONOUS";

            case UsbConstants.USB_ENDPOINT_XFER_BULK:
                return "BULK";

            case UsbConstants.USB_ENDPOINT_XFER_INT:
                return "INTERRUPT";

            default:
                return "UNKNOWN";
        }
    }

    private void log(
            String message
    ) {

        if (logger != null) {

            logger.log(
                    message
            );
        }
    }
}