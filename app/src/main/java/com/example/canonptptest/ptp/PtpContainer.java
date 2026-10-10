package com.example.canonptptest.ptp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PtpContainer {

    public static final int TYPE_COMMAND = 1;
    public static final int TYPE_DATA = 2;
    public static final int TYPE_RESPONSE = 3;
    public static final int TYPE_EVENT = 4;

    private final int length;
    private final int type;
    private final int code;
    private final long transactionId;
    private final byte[] parameters;

    public PtpContainer(
            int length,
            int type,
            int code,
            long transactionId,
            byte[] parameters
    ) {
        this.length = length;
        this.type = type;
        this.code = code;
        this.transactionId = transactionId;
        this.parameters =
                parameters != null
                        ? parameters
                        : new byte[0];
    }

    public int getLength() {
        return length;
    }

    public int getType() {
        return type;
    }

    public int getCode() {
        return code;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public byte[] getParameters() {
        return parameters;
    }

    // ============================================================
    // BUILD COMMAND
    // ============================================================

    public static byte[] buildCommand(
            int operationCode,
            long transactionId,
            int... parameters
    ) {

        int parameterCount =
                parameters != null
                        ? parameters.length
                        : 0;

        int length =
                12 + (parameterCount * 4);

        ByteBuffer buffer =
                ByteBuffer.allocate(length)
                        .order(
                                ByteOrder.LITTLE_ENDIAN
                        );

        buffer.putInt(length);

        buffer.putShort(
                (short) TYPE_COMMAND
        );

        buffer.putShort(
                (short) operationCode
        );

        buffer.putInt(
                (int) transactionId
        );

        if (parameters != null) {

            for (int parameter : parameters) {

                buffer.putInt(parameter);
            }
        }

        return buffer.array();
    }

    // ============================================================
    // PARSE
    // ============================================================

    public static PtpContainer parse(
            byte[] data,
            int actualLength
    ) {

        if (data == null) {

            throw new IllegalArgumentException(
                    "PTP data is null"
            );
        }

        if (actualLength < 12) {

            throw new IllegalArgumentException(
                    "PTP container too small: " +
                    actualLength
            );
        }

        if (actualLength > data.length) {

            throw new IllegalArgumentException(
                    "actualLength exceeds data length"
            );
        }

        ByteBuffer buffer =
                ByteBuffer.wrap(
                        data,
                        0,
                        actualLength
                ).order(
                        ByteOrder.LITTLE_ENDIAN
                );

        int length =
                buffer.getInt();

        int type =
                Short.toUnsignedInt(
                        buffer.getShort()
                );

        int code =
                Short.toUnsignedInt(
                        buffer.getShort()
                );

        long transactionId =
                Integer.toUnsignedLong(
                        buffer.getInt()
                );

        if (length < 12) {

            throw new IllegalArgumentException(
                    "Invalid PTP container length: " +
                    length
            );
        }

        int parameterBytes =
                Math.max(
                        0,
                        Math.min(
                                length - 12,
                                actualLength - 12
                        )
                );

        byte[] parameters =
                new byte[parameterBytes];

        if (parameterBytes > 0) {

            buffer.get(parameters);
        }

        return new PtpContainer(
                length,
                type,
                code,
                transactionId,
                parameters
        );
    }

    // ============================================================
    // TYPE NAME
    // ============================================================

    public String typeToString() {

        switch (type) {

            case TYPE_COMMAND:
                return "COMMAND";

            case TYPE_DATA:
                return "DATA";

            case TYPE_RESPONSE:
                return "RESPONSE";

            case TYPE_EVENT:
                return "EVENT";

            default:
                return "UNKNOWN";
        }
    }
}