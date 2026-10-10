package com.example.canonptptest.ptp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PtpDeviceInfo {

    public int standardVersion;

    public long vendorExtensionId;

    public int vendorExtensionVersion;

    public String vendorExtensionDescription;

    public int functionalMode;

    public List<Integer> operationsSupported =
            new ArrayList<>();

    public List<Integer> eventsSupported =
            new ArrayList<>();

    public List<Integer> devicePropertiesSupported =
            new ArrayList<>();

    public List<Integer> captureFormats =
            new ArrayList<>();

    public List<Integer> imageFormats =
            new ArrayList<>();

    public String manufacturer;

    public String model;

    public String deviceVersion;

    public String serialNumber;

    // ============================================================
    // PARSE DEVICE INFO
    // ============================================================

    public static PtpDeviceInfo parse(
            byte[] data
    ) throws Exception {

        if (data == null) {

            throw new Exception(
                    "DeviceInfo data is null"
            );
        }

        ByteBuffer buffer =
                ByteBuffer.wrap(data)
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        PtpDeviceInfo info =
                new PtpDeviceInfo();

        info.standardVersion =
                readUInt16(buffer);

        info.vendorExtensionId =
                readUInt32(buffer);

        info.vendorExtensionVersion =
                readUInt16(buffer);

        info.vendorExtensionDescription =
                readPtpString(buffer);

        info.functionalMode =
                readUInt16(buffer);

        info.operationsSupported =
                readUInt16Array(buffer);

        info.eventsSupported =
                readUInt16Array(buffer);

        info.devicePropertiesSupported =
                readUInt16Array(buffer);

        info.captureFormats =
                readUInt16Array(buffer);

        info.imageFormats =
                readUInt16Array(buffer);

        info.manufacturer =
                readPtpString(buffer);

        info.model =
                readPtpString(buffer);

        info.deviceVersion =
                readPtpString(buffer);

        info.serialNumber =
                readPtpString(buffer);

        return info;
    }

    // ============================================================
    // UINT16
    // ============================================================

    private static int readUInt16(
            ByteBuffer buffer
    ) throws Exception {

        if (buffer.remaining() < 2) {

            throw new Exception(
                    "Unexpected end of DeviceInfo"
            );
        }

        return Short.toUnsignedInt(
                buffer.getShort()
        );
    }

    // ============================================================
    // UINT32
    // ============================================================

    private static long readUInt32(
            ByteBuffer buffer
    ) throws Exception {

        if (buffer.remaining() < 4) {

            throw new Exception(
                    "Unexpected end of DeviceInfo"
            );
        }

        return Integer.toUnsignedLong(
                buffer.getInt()
        );
    }

    // ============================================================
    // UINT16 ARRAY
    // ============================================================

    private static List<Integer> readUInt16Array(
            ByteBuffer buffer
    ) throws Exception {

        long countLong =
                readUInt32(buffer);

        if (countLong > 100000) {

            throw new Exception(
                    "Invalid DeviceInfo array count: " +
                    countLong
            );
        }

        int count =
                (int) countLong;

        List<Integer> result =
                new ArrayList<>();

        for (int i = 0; i < count; i++) {

            result.add(
                    readUInt16(buffer)
            );
        }

        return result;
    }

    // ============================================================
    // PTP STRING
    // ============================================================

    private static String readPtpString(
            ByteBuffer buffer
    ) throws Exception {

        if (buffer.remaining() < 1) {

            throw new Exception(
                    "Unexpected end of DeviceInfo while reading string"
            );
        }

        int characterCount =
                Byte.toUnsignedInt(
                        buffer.get()
                );

        if (characterCount == 0) {
            return "";
        }

        int byteCount =
                characterCount * 2;

        if (buffer.remaining() < byteCount) {

            throw new Exception(
                    "Invalid PTP string length: " +
                    characterCount
            );
        }

        byte[] stringBytes =
                new byte[byteCount];

        buffer.get(stringBytes);

        String value =
                new String(
                        stringBytes,
                        StandardCharsets.UTF_16LE
                );

        int nullIndex =
                value.indexOf('\0');

        if (nullIndex >= 0) {

            value =
                    value.substring(
                            0,
                            nullIndex
                    );
        }

        return value;
    }
}