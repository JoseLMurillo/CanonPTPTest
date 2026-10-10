package com.example.canonptptest.ptp;

public class PtpResponse {

    public static final int RESPONSE_OK = 0x2001;

    private final int code;
    private final long transactionId;
    private final byte[] parameters;

    public PtpResponse(
            int code,
            long transactionId,
            byte[] parameters
    ) {

        this.code = code;

        this.transactionId =
                transactionId;

        this.parameters =
                parameters != null
                        ? parameters
                        : new byte[0];
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

    public boolean isSuccess() {
        return code == RESPONSE_OK;
    }

    public String getCodeName() {

        switch (code) {

            case 0x2001:
                return "OK";

            case 0x2002:
                return "GENERAL_ERROR";

            case 0x2003:
                return "SESSION_NOT_OPEN";

            case 0x2004:
                return "INVALID_TRANSACTION_ID";

            case 0x2005:
                return "OPERATION_NOT_SUPPORTED";

            case 0x2006:
                return "PARAMETER_NOT_SUPPORTED";

            case 0x2007:
                return "INCOMPLETE_TRANSFER";

            case 0x2008:
                return "INVALID_STORAGE_ID";

            case 0x2009:
                return "INVALID_OBJECT_HANDLE";

            case 0x2019:
                return "DEVICE_BUSY";

            default:
                return "UNKNOWN";
        }
    }

    @Override
    public String toString() {

        return String.format(
                "code=0x%04X (%s), transaction=%d",
                code,
                getCodeName(),
                transactionId
        );
    }
}