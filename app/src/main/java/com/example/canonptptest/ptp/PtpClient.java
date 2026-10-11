package com.example.canonptptest.ptp;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
// import java.io.ByteArrayOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class PtpClient {
  public static final int OP_GET_DEVICE_INFO = 0x1001;
  public static final int OP_OPEN_SESSION = 0x1002;
  public static final int OP_CLOSE_SESSION = 0x1003;

  private static final int RESPONSE_OK = 0x2001;

  private static final int CONTAINER_COMMAND = 1;
  private static final int CONTAINER_DATA = 2;
  private static final int CONTAINER_RESPONSE = 3;

  private static final int USB_TIMEOUT = 5000;

  public static final int OP_GET_STORAGE_IDS = 0x1004;
  public static final int OP_GET_STORAGE_INFO = 0x1005;
  public static final int OP_GET_OBJECT_HANDLES = 0x1007;

  public static final int OP_GET_OBJECT_INFO = 0x1008;
  public static final int OP_GET_OBJECT = 0x1009;

  private final UsbDeviceConnection connection;
  private final UsbEndpoint bulkIn;
  private final UsbEndpoint bulkOut;

  private long transactionId = 0;
  private boolean sessionOpen = false;
  private final Logger logger;

  private static final int USB_MAX_ATTEMPTS = 3;
  private static final long USB_RETRY_DELAY_MS = 300;

  public interface Logger {
    void log(String message);
  }

  // LOG || MOSTRAR MENSAJE LOG
  private void log(String message) {
    if (logger != null) {
      logger.log("PTP: " + message);
    }
  }

  // GET DEVICE INFO || YA CONOCE A PTPDEVICEINFO
  public synchronized PtpDeviceInfo getDeviceInfo() throws Exception {
    if (!sessionOpen) {
      throw new IllegalStateException("PTP session is not open");
    }

    transactionId++;

    long currentTransaction = transactionId;

    log("--------------------------------");
    log("PTP: GetDeviceInfo");
    log("Operation: 0x1001");
    log("Transaction ID: " + currentTransaction);

    byte[] command = PtpContainer.buildCommand(OP_GET_DEVICE_INFO, currentTransaction);

    log("Sending GetDeviceInfo command");

    sendBulkOut(command);

    // DATA CONTAINER
    log("Waiting for DeviceInfo DATA container...");

    PtpContainer data = receiveContainer();

    if (data.getType() != CONTAINER_DATA) {
      throw new Exception("Expected DATA container but received " + data.typeToString());
    }

    if (data.getCode() != OP_GET_DEVICE_INFO) {
      throw new Exception(String.format("Unexpected DATA code: 0x%04X", data.getCode()));
    }

    if (data.getTransactionId() != currentTransaction) {
      throw new Exception(
          "DATA transaction mismatch. Expected "
              + currentTransaction
              + " but received "
              + data.getTransactionId());
    }

    log("DeviceInfo DATA received: " + data.getParameters().length + " bytes");

    // RESPONSE CONTAINER
    log("Waiting for DeviceInfo RESPONSE...");

    PtpContainer response = receiveContainer();

    validateResponse(response, currentTransaction);

    PtpResponse ptpResponse =
        new PtpResponse(response.getCode(), response.getTransactionId(), response.getParameters());

    log(ptpResponse.toString());

    if (!ptpResponse.isSuccess()) {
      throw new Exception("GetDeviceInfo failed: " + ptpResponse);
    }

    // PARSE DATA
    PtpDeviceInfo info = PtpDeviceInfo.parse(data.getParameters());

    log("--------------------------------");
    log("DEVICE INFO");
    log("Standard Version: " + info.standardVersion);
    log(String.format("Vendor Extension ID: 0x%08X", info.vendorExtensionId));
    log("Vendor Extension Version: " + info.vendorExtensionVersion);
    log("Vendor Extension Description: " + info.vendorExtensionDescription);
    log("Functional Mode: " + info.functionalMode);
    log("Manufacturer: " + info.manufacturer);
    log("Model: " + info.model);
    log("Device Version: " + info.deviceVersion);
    log("Serial Number: " + info.serialNumber);
    log("--------------------------------");
    log("Operations Supported: " + info.operationsSupported.size());

    for (Integer operation : info.operationsSupported) {
      log(String.format("  0x%04X %s", operation, operationName(operation)));
    }

    log("--------------------------------");
    log("Events Supported: " + info.eventsSupported.size());

    for (Integer event : info.eventsSupported) {
      log(String.format("  0x%04X", event));
    }

    log("--------------------------------");
    log("Device Properties Supported: " + info.devicePropertiesSupported.size());

    for (Integer property : info.devicePropertiesSupported) {
      log(String.format("  0x%04X", property));
    }

    log("--------------------------------");
    log("GetDeviceInfo SUCCESS");

    return info;
  }

  // USB IN
  /*
  private PtpContainer receiveContainer() throws Exception {
    log("Reading PTP container from endpoint 0x" + String.format("%02X", bulkIn.getAddress()));

    ByteArrayOutputStream received = new ByteArrayOutputStream();

    int declaredLength = -1;
    byte[] buffer = new byte[512];

    while (true) {
      int result = connection.bulkTransfer(bulkIn, buffer, buffer.length, USB_TIMEOUT);

      log("bulkTransfer IN result: " + result);

      if (result < 0) {
        throw new Exception("USB bulk IN failed");
      }

      if (result == 0) {
        throw new Exception("USB bulk IN returned 0 bytes");
      }

      received.write(buffer, 0, result);

      // Read declared PTP container length
      if (declaredLength < 0 && received.size() >= 4) {
        byte[] header = received.toByteArray();
        ByteBuffer headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        declaredLength = headerBuffer.getInt();

        log("PTP declared container length: " + declaredLength);

        if (declaredLength < 12) {
          throw new Exception("Invalid PTP container length: " + declaredLength);
        }
      }

      if (declaredLength > 0 && received.size() >= declaredLength) {
        break;
      }

      if (received.size() > 10 * 1024 * 1024) {
        throw new Exception("PTP container exceeds 10 MB");
      }
    }

    byte[] all = received.toByteArray();

    log("RX [" + declaredLength + " bytes]");

    dumpBytes(all, declaredLength);

    return PtpContainer.parse(all, declaredLength);
  }*/

  private PtpContainer receiveContainer() throws Exception {
    if (connection == null || bulkIn == null) {
      throw new Exception("USB IN no está disponible.");
    }

    log("Reading PTP container from endpoint 0x" + String.format("%02X", bulkIn.getAddress()));

    ByteArrayOutputStream received = new ByteArrayOutputStream();

    int declaredLength = -1;
    byte[] buffer = new byte[512];
    int attempts = 0;

    while (true) {
      int result;

      try {
        result = connection.bulkTransfer(bulkIn, buffer, buffer.length, USB_TIMEOUT);
      } catch (Exception e) {
        if (received.size() > 0) {
          throw new Exception(
              "Falló USB IN después de recibir parte del contenedor. "
                  + "No se puede reiniciar la lectura de forma segura.",
              e);
        }

        if (++attempts >= USB_MAX_ATTEMPTS) {
          throw new Exception("USB IN falló después de " + USB_MAX_ATTEMPTS + " intentos.", e);
        }

        log("USB IN: intento " + attempts + " fallido. Reintentando...");
        Thread.sleep(USB_RETRY_DELAY_MS);
        continue;
      }

      log("bulkTransfer IN result: " + result);

      if (result < 0) {
        if (received.size() > 0) {
          throw new Exception(
              "USB IN falló después de recibir "
                  + received.size()
                  + " bytes. No se reiniciará la lectura.");
        }

        if (++attempts >= USB_MAX_ATTEMPTS) {
          throw new Exception("USB bulk IN falló después de " + USB_MAX_ATTEMPTS + " intentos.");
        }

        log("USB IN: intento " + attempts + " fallido. Reintentando...");
        Thread.sleep(USB_RETRY_DELAY_MS);
        continue;
      }

      if (result == 0) {
        if (received.size() > 0) {
          throw new Exception("USB IN devolvió 0 bytes con un contenedor incompleto.");
        }

        if (++attempts >= USB_MAX_ATTEMPTS) {
          throw new Exception("USB bulk IN devolvió 0 bytes en " + USB_MAX_ATTEMPTS + " intentos.");
        }

        log("USB IN vacío. Reintentando, intento " + attempts);
        Thread.sleep(USB_RETRY_DELAY_MS);
        continue;
      }

      // Se recibieron datos: ya no se reinicia esta lectura.
      attempts = 0;
      received.write(buffer, 0, result);

      if (declaredLength < 0 && received.size() >= 4) {
        byte[] header = received.toByteArray();

        ByteBuffer headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);

        declaredLength = headerBuffer.getInt();

        log("PTP declared container length: " + declaredLength);

        if (declaredLength < 12) {
          throw new Exception("Invalid PTP container length: " + declaredLength);
        }

        if (declaredLength > 32 * 1024 * 1024) {
          throw new Exception("PTP container exceeds 32 MB: " + declaredLength);
        }
      }

      if (declaredLength > 0 && received.size() >= declaredLength) {
        break;
      }

      if (received.size() > 32 * 1024 * 1024) {
        throw new Exception("PTP container exceeds 32 MB");
      }
    }

    byte[] all = received.toByteArray();

    log("RX [" + declaredLength + " bytes]");
    dumpBytes(all, declaredLength);

    return PtpContainer.parse(all, declaredLength);
  }

  // USB OUT
  /*
  private void sendBulkOut(byte[] data) throws Exception {
    log("TX [" + data.length + " bytes]");

    dumpBytes(data);

    int result = connection.bulkTransfer(bulkOut, data, data.length, USB_TIMEOUT);

    log("bulkTransfer OUT result: " + result);

    if (result < 0) {
      throw new Exception("USB bulk OUT failed");
    }

    if (result != data.length) {
      throw new Exception(
          "USB bulk OUT incomplete. " + "Expected " + data.length + " bytes, sent " + result);
    }
  }*/

  // USB OUT
  private void sendBulkOut(byte[] data) throws Exception {
    if (connection == null || bulkOut == null) {
      throw new Exception("USB OUT no está disponible.");
    }

    if (data == null || data.length == 0) {
      throw new Exception("El comando USB está vacío.");
    }

    log("TX [" + data.length + " bytes]");
    dumpBytes(data);

    int result = connection.bulkTransfer(bulkOut, data, data.length, USB_TIMEOUT);

    log("bulkTransfer OUT result: " + result);

    if (result < 0) {
      throw new Exception(
          "USB bulk OUT falló. No se reenvió el comando "
              + "porque no es posible confirmar si la cámara lo recibió.");
    }

    if (result != data.length) {
      throw new Exception(
          "USB bulk OUT incompleto. Esperados: " + data.length + ", enviados: " + result);
    }
  }

  public static class ObjectInfo {

    public long objectHandle;

    public long storageId;
    public int objectFormat;
    public int protectionStatus;

    public long compressedSize;

    public int thumbFormat;
    public long thumbCompressedSize;
    public long thumbWidth;
    public long thumbHeight;

    public long width;
    public long height;
    public long bitDepth;

    public long parentObject;

    public int associationType;
    public long associationDesc;
    public long sequenceNumber;

    public String filename;
    public String captureDate;
    public String modificationDate;
    public String keywords;

    public boolean isImage() {

      switch (objectFormat) {
        case 0x3800: // Undefined
        case 0x3801: // EXIF/JPEG
        case 0x3802: // TIFF EP
        case 0x3803: // FlashPix
        case 0x3804: // BMP
        case 0x3805: // CIFF
        case 0x3807: // GIF
        case 0x3808: // JPEG
        case 0x380B: // PNG
        case 0x380C: // JP2
        case 0x380D: // TIFF
        case 0x380E: // JPX
        case 0x3811: // DNG
        case 0xB103: // Canon RAW
          return true;

        default:
          return false;
      }
    }
  }

  public PtpClient(
      UsbDeviceConnection connection, UsbEndpoint bulkIn, UsbEndpoint bulkOut, Logger logger) {
    this.connection = connection;
    this.bulkIn = bulkIn;
    this.bulkOut = bulkOut;
    this.logger = logger;
  }

  // OPEN SESSION
  public synchronized PtpResponse openSession() throws Exception {
    if (sessionOpen) {
      throw new IllegalStateException("PTP session already open");
    }

    transactionId++;

    log("PTP: Opening session...");
    log("Transaction ID: " + transactionId);

    byte[] command = PtpContainer.buildCommand(OP_OPEN_SESSION, transactionId, 1);

    log("Sending OpenSession command");

    sendBulkOut(command);

    PtpContainer response = receiveContainer();

    validateResponse(response, transactionId);

    PtpResponse result =
        new PtpResponse(response.getCode(), response.getTransactionId(), response.getParameters());

    if (!result.isSuccess()) {
      throw new Exception("OpenSession failed: " + result);
    }

    sessionOpen = true;

    log("PTP OpenSession SUCCESS");
    log(result.toString());

    return result;
  }

  public synchronized PtpResponse recoverSession() throws Exception {
    log("");
    log("================================");
    log("RECUPERACION DE SESION PTP");
    log("================================");

    if (connection == null || bulkIn == null || bulkOut == null) {
      throw new Exception("No se puede recuperar PTP: la conexión USB no está disponible.");
    }

    if (!sessionOpen) {
      throw new Exception(
          "No se puede garantizar la recuperación: " + "la sesión PTP no figura como abierta.");
    }

    try {
      // 1. Solicitar el cierre de la sesión actual.
      transactionId++;
      long closeTransaction = transactionId;

      log("Paso 1: cerrando sesión anterior...");
      log("Transaction ID: " + closeTransaction);

      byte[] closeCommand = PtpContainer.buildCommand(OP_CLOSE_SESSION, closeTransaction);

      sendBulkOut(closeCommand);

      PtpContainer closeResponse = receiveContainer();

      validateResponse(closeResponse, closeTransaction);

      PtpResponse closeResult =
          new PtpResponse(
              closeResponse.getCode(),
              closeResponse.getTransactionId(),
              closeResponse.getParameters());

      if (!closeResult.isSuccess()) {
        throw new Exception("La cámara rechazó CloseSession: " + closeResult);
      }

      sessionOpen = false;

      log("Sesión anterior cerrada correctamente.");

      // 2. Abrir una nueva sesión.
      log("Paso 2: abriendo nueva sesión PTP...");

      PtpResponse openResult = openSession();

      log("RECUPERACION PTP COMPLETADA");
      log(openResult.toString());

      return openResult;

    } catch (Exception e) {
      // No afirmar que la sesión sigue siendo válida.
      sessionOpen = false;

      log("RECUPERACION PTP FALLIDA");
      log(e.getMessage() != null ? e.getMessage() : e.toString());

      throw new Exception(
          "No se pudo recuperar la sesión PTP de forma segura. "
              + "La conexión USB podría seguir activa, pero el estado "
              + "de la sesión es incierto.",
          e);
    }
  }

  // CLOSE
  public synchronized void close() {
    try {
      if (sessionOpen) {
        transactionId++;

        byte[] command = PtpContainer.buildCommand(OP_CLOSE_SESSION, transactionId);

        sendBulkOut(command);

        PtpContainer response = receiveContainer();

        log("CloseSession response: " + String.format("0x%04X", response.getCode()));
      }

    } catch (Exception e) {

      log("CloseSession error: " + e.getMessage());

    } finally {

      sessionOpen = false;
    }
  }

  private PtpContainer receiveContainer(boolean dumpPayload) throws Exception {

    if (connection == null || bulkIn == null) {
      throw new Exception("USB connection or bulk IN endpoint is null");
    }

    final int HEADER_SIZE = 12;
    final int READ_BUFFER_SIZE = 16 * 1024;
    final int MAX_OBJECT_SIZE = 32 * 1024 * 1024;

    log("PTP RX: esperando encabezado y primeros datos");

    /*
     * Leer un bloque inicial amplio.
     * La cámara puede entregar el encabezado y parte
     * de los datos de la imagen en la misma transferencia.
     */
    byte[] firstChunk = new byte[READ_BUFFER_SIZE];

    int received = connection.bulkTransfer(bulkIn, firstChunk, 0, firstChunk.length, USB_TIMEOUT);

    log("PTP RX: primer bloque recibido = " + received);

    if (received < HEADER_SIZE) {
      throw new Exception("PTP header incomplete. Received: " + received);
    }

    ByteBuffer headerBuffer =
        ByteBuffer.wrap(firstChunk, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);

    long length = Integer.toUnsignedLong(headerBuffer.getInt());
    int type = Short.toUnsignedInt(headerBuffer.getShort());
    int code = Short.toUnsignedInt(headerBuffer.getShort());
    long transaction = Integer.toUnsignedLong(headerBuffer.getInt());

    log(
        String.format(
            "RX HEADER: length=%d type=%d code=0x%04X transaction=%d",
            length, type, code, transaction));

    if (length < HEADER_SIZE) {
      throw new Exception("Invalid PTP container length: " + length);
    }

    long payloadLengthLong = length - HEADER_SIZE;

    if (payloadLengthLong > MAX_OBJECT_SIZE) {
      throw new Exception("PTP payload exceeds safety limit: " + payloadLengthLong + " bytes");
    }

    int payloadLength = (int) payloadLengthLong;

    byte[] payload = new byte[payloadLength];

    /*
     * El primer bloque puede contener parte del payload.
     */
    int bytesInFirstChunk = received - HEADER_SIZE;

    if (bytesInFirstChunk > payloadLength) {
      throw new Exception("Received more data than declared in PTP header");
    }

    if (bytesInFirstChunk > 0) {
      System.arraycopy(firstChunk, HEADER_SIZE, payload, 0, bytesInFirstChunk);
    }

    int offset = bytesInFirstChunk;

    log("PTP RX: payload=" + payloadLength + " bytes; recibidos inicialmente=" + offset);

    /*
     * Recibir el resto del payload en bloques.
     */
    byte[] chunk = new byte[READ_BUFFER_SIZE];

    while (offset < payloadLength) {

      int remaining = payloadLength - offset;
      int requested = Math.min(chunk.length, remaining);

      int count = connection.bulkTransfer(bulkIn, chunk, 0, requested, USB_TIMEOUT);

      if (count <= 0) {
        throw new Exception(
            "USB bulk IN failed while receiving PTP payload. "
                + "Received: "
                + count
                + ", remaining: "
                + remaining);
      }

      if (count > remaining) {
        throw new Exception("USB returned more bytes than the remaining PTP payload");
      }

      System.arraycopy(chunk, 0, payload, offset, count);

      offset += count;
    }

    log("PTP RX: payload completo: " + offset + " bytes");

    if (dumpPayload) {
      byte[] completeContainer = new byte[(int) length];

      System.arraycopy(firstChunk, 0, completeContainer, 0, HEADER_SIZE);

      if (payloadLength > 0) {
        System.arraycopy(payload, 0, completeContainer, HEADER_SIZE, payloadLength);
      }

      dumpBytes(completeContainer);
    }

    return new PtpContainer((int) length, type, code, transaction, payload);
  }

  // RESPONSE VALIDATION
  private void validateResponse(PtpContainer response, long expectedTransaction) throws Exception {

    if (response.getType() != CONTAINER_RESPONSE) {

      throw new Exception("Expected RESPONSE container but received " + response.typeToString());
    }

    if (response.getTransactionId() != expectedTransaction) {

      throw new Exception(
          "Transaction mismatch. Expected "
              + expectedTransaction
              + " but received "
              + response.getTransactionId());
    }
  }

  // OPERATION NAMES

  private String operationName(int operation) {

    switch (operation) {
      case 0x1001:
        return "GetDeviceInfo";

      case 0x1002:
        return "OpenSession";

      case 0x1003:
        return "CloseSession";

      case 0x1004:
        return "GetStorageIDs";

      case 0x1005:
        return "GetStorageInfo";

      case 0x1006:
        return "GetNumObjects";

      case 0x1007:
        return "GetObjectHandles";

      case 0x1008:
        return "GetObjectInfo";

      case 0x1009:
        return "GetObject";

      case 0x100A:
        return "GetThumb";

      case 0x100B:
        return "DeleteObject";

      case 0x100E:
        return "InitiateCapture";

      case 0x1014:
        return "GetDevicePropDesc";

      case 0x1015:
        return "GetDevicePropValue";

      case 0x1016:
        return "SetDevicePropValue";

      case 0x101B:
        return "GetPartialObject";

      case 0x9101:
        return "Canon EOS GetStorageIDs";

      case 0x9102:
        return "Canon EOS GetStorageInfo";

      default:
        return "Unknown";
    }
  }

  // DEBUG

  private void dumpBytes(byte[] data) {

    dumpBytes(data, data.length);
  }

  private void dumpBytes(byte[] data, int length) {

    StringBuilder builder = new StringBuilder();

    int max = Math.min(length, data.length);

    for (int i = 0; i < max; i++) {

      builder.append(String.format("%02X ", data[i] & 0xFF));

      if ((i + 1) % 16 == 0) {

        log(builder.toString());
        builder.setLength(0);
      }
    }

    if (builder.length() > 0) {
      log(builder.toString());
    }
  }

  public synchronized List<Long> getStorageIds() throws Exception {

    if (!sessionOpen) {
      throw new IllegalStateException("PTP session is not open");
    }

    transactionId++;

    long currentTransaction = transactionId;

    log("--------------------------------");
    log("PTP: GetStorageIDs");
    log(String.format("Transaction ID: %d", currentTransaction));

    byte[] command = PtpContainer.buildCommand(OP_GET_STORAGE_IDS, currentTransaction);

    sendBulkOut(command);

    log("Waiting for StorageIDs DATA...");

    PtpContainer data = receiveContainer();

    if (data.getType() != PtpContainer.TYPE_DATA) {

      throw new Exception("Expected DATA container, received " + data.typeToString());
    }

    if (data.getCode() != OP_GET_STORAGE_IDS) {

      throw new Exception(String.format("Unexpected DATA code: 0x%04X", data.getCode()));
    }

    if (data.getTransactionId() != currentTransaction) {

      throw new Exception("StorageIDs transaction mismatch");
    }

    ByteBuffer buffer = ByteBuffer.wrap(data.getParameters()).order(ByteOrder.LITTLE_ENDIAN);

    if (buffer.remaining() < 4) {
      throw new Exception("StorageIDs data too small");
    }

    long count = Integer.toUnsignedLong(buffer.getInt());

    if (count > 1000) {
      throw new Exception("Invalid StorageIDs count: " + count);
    }

    List<Long> storageIds = new ArrayList<>();

    for (int i = 0; i < count; i++) {

      if (buffer.remaining() < 4) {
        throw new Exception("Incomplete StorageID list");
      }

      long storageId = Integer.toUnsignedLong(buffer.getInt());

      storageIds.add(storageId);
    }

    log("Storage count: " + count);

    for (Long storageId : storageIds) {

      log(String.format("  Storage ID: 0x%08X", storageId));
    }

    log("Waiting for StorageIDs RESPONSE...");

    PtpContainer response = receiveContainer();

    validateResponse(response, currentTransaction);

    PtpResponse ptpResponse =
        new PtpResponse(response.getCode(), response.getTransactionId(), response.getParameters());

    log(ptpResponse.toString());

    if (!ptpResponse.isSuccess()) {
      throw new Exception("GetStorageIDs failed: " + ptpResponse);
    }

    log("GetStorageIDs SUCCESS");

    return storageIds;
  }

  public synchronized void getStorageInfo(long storageId) throws Exception {

    if (!sessionOpen) {
      throw new IllegalStateException("PTP session is not open");
    }

    transactionId++;
    long currentTransaction = transactionId;

    log("--------------------------------");
    log("PTP: GetStorageInfo");
    log(String.format("Storage ID: 0x%08X", storageId));
    log(String.format("Transaction ID: %d", currentTransaction));

    byte[] command =
        PtpContainer.buildCommand(OP_GET_STORAGE_INFO, currentTransaction, (int) storageId);

    sendBulkOut(command);

    log("Waiting for StorageInfo DATA...");

    PtpContainer data = receiveContainer();

    if (data.getType() != PtpContainer.TYPE_DATA) {
      throw new Exception("Expected DATA, received " + data.typeToString());
    }

    if (data.getCode() != OP_GET_STORAGE_INFO) {
      throw new Exception(String.format("Unexpected DATA code: 0x%04X", data.getCode()));
    }

    if (data.getTransactionId() != currentTransaction) {
      throw new Exception("StorageInfo transaction mismatch");
    }

    parseStorageInfo(data.getParameters());

    log("Waiting for StorageInfo RESPONSE...");

    PtpContainer response = receiveContainer();

    validateResponse(response, currentTransaction);

    PtpResponse ptpResponse =
        new PtpResponse(response.getCode(), response.getTransactionId(), response.getParameters());

    log(ptpResponse.toString());

    if (!ptpResponse.isSuccess()) {
      throw new Exception("GetStorageInfo failed: " + ptpResponse);
    }

    log("GetStorageInfo SUCCESS");
  }

  private void parseStorageInfo(byte[] data) throws Exception {

    ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

    if (buffer.remaining() < 30) {
      throw new Exception("StorageInfo data too small: " + buffer.remaining());
    }

    int storageType = Short.toUnsignedInt(buffer.getShort());

    int filesystemType = Short.toUnsignedInt(buffer.getShort());

    int accessCapability = Short.toUnsignedInt(buffer.getShort());

    long maxCapacity = buffer.getLong();

    long freeSpace = buffer.getLong();

    long freeImages = Integer.toUnsignedLong(buffer.getInt());

    String storageDescription = readPtpString(buffer);

    String volumeLabel = readPtpString(buffer);

    log("--------------------------------");
    log("STORAGE INFO");

    log(String.format("Storage Type: 0x%04X", storageType));

    log(String.format("Filesystem Type: 0x%04X", filesystemType));

    log(String.format("Access Capability: 0x%04X", accessCapability));

    log("Max Capacity: " + maxCapacity + " bytes");

    log("Free Space: " + freeSpace + " bytes");

    log("Free Images: " + freeImages);

    log("Description: " + storageDescription);

    log("Volume Label: " + volumeLabel);

    log("--------------------------------");
  }

  public synchronized List<Long> getObjectHandles(long storageId) throws Exception {

    if (!sessionOpen) {
      throw new IllegalStateException("PTP session is not open");
    }

    transactionId++;
    long currentTransaction = transactionId;

    log("--------------------------------");
    log("PTP: GetObjectHandles");
    log(String.format("Storage ID: 0x%08X", storageId));
    log(String.format("Transaction ID: %d", currentTransaction));

    byte[] command =
        PtpContainer.buildCommand(OP_GET_OBJECT_HANDLES, currentTransaction, (int) storageId, 0, 0);

    sendBulkOut(command);

    log("Waiting for ObjectHandles DATA...");

    PtpContainer data = receiveContainer();

    if (data.getType() != PtpContainer.TYPE_DATA) {
      throw new Exception("Expected DATA, received " + data.typeToString());
    }

    if (data.getCode() != OP_GET_OBJECT_HANDLES) {
      throw new Exception(String.format("Unexpected DATA code: 0x%04X", data.getCode()));
    }

    if (data.getTransactionId() != currentTransaction) {
      throw new Exception("ObjectHandles transaction mismatch");
    }

    ByteBuffer buffer = ByteBuffer.wrap(data.getParameters()).order(ByteOrder.LITTLE_ENDIAN);

    if (buffer.remaining() < 4) {
      throw new Exception("ObjectHandles data too small");
    }

    long count = Integer.toUnsignedLong(buffer.getInt());

    if (count > 100000) {
      throw new Exception("Invalid object count: " + count);
    }

    List<Long> handles = new ArrayList<>();

    for (int i = 0; i < count; i++) {

      if (buffer.remaining() < 4) {
        throw new Exception("Incomplete ObjectHandles list");
      }

      handles.add(Integer.toUnsignedLong(buffer.getInt()));
    }

    log("Objects found: " + count);

    for (Long handle : handles) {
      log(String.format("  Object ID: 0x%08X", handle));
    }

    log("Waiting for ObjectHandles RESPONSE...");

    PtpContainer response = receiveContainer();

    validateResponse(response, currentTransaction);

    PtpResponse ptpResponse =
        new PtpResponse(response.getCode(), response.getTransactionId(), response.getParameters());

    log(ptpResponse.toString());

    if (!ptpResponse.isSuccess()) {
      throw new Exception("GetObjectHandles failed: " + ptpResponse);
    }

    log("GetObjectHandles SUCCESS");

    return handles;
  }

  private String readPtpString(ByteBuffer buffer) throws Exception {

    if (buffer.remaining() < 1) {
      throw new Exception("Unexpected end of PTP string");
    }

    int characterCount = Byte.toUnsignedInt(buffer.get());

    if (characterCount == 0) {
      return "";
    }

    int byteCount = characterCount * 2;

    if (buffer.remaining() < byteCount) {
      throw new Exception("Invalid PTP string length: " + characterCount);
    }

    byte[] stringBytes = new byte[byteCount];

    buffer.get(stringBytes);

    String value = new String(stringBytes, java.nio.charset.StandardCharsets.UTF_16LE);

    int nullIndex = value.indexOf('\0');

    if (nullIndex >= 0) {
      value = value.substring(0, nullIndex);
    }

    return value;
  }

  public synchronized ObjectInfo getObjectInfo(long objectHandle) throws Exception {

    if (!sessionOpen) {
      throw new IllegalStateException("PTP session is not open");
    }

    transactionId++;
    long currentTransaction = transactionId;

    log("--------------------------------");
    log("PTP: GetObjectInfo");
    log(String.format("Object Handle: 0x%08X", objectHandle));
    log("Transaction ID: " + currentTransaction);

    byte[] command =
        PtpContainer.buildCommand(OP_GET_OBJECT_INFO, currentTransaction, (int) objectHandle);

    sendBulkOut(command);

    log("Waiting for ObjectInfo DATA...");

    PtpContainer data = receiveContainer();

    if (data.getType() != PtpContainer.TYPE_DATA) {

      throw new Exception("Expected DATA, received " + data.typeToString());
    }

    if (data.getCode() != OP_GET_OBJECT_INFO) {

      throw new Exception(String.format("Unexpected DATA code: 0x%04X", data.getCode()));
    }

    if (data.getTransactionId() != currentTransaction) {

      throw new Exception("ObjectInfo transaction mismatch");
    }

    // IMPORTANTE: Primero recibimos el RESPONSE. No intentamos parsear antes.

    log("Waiting for ObjectInfo RESPONSE...");

    PtpContainer response = receiveContainer();

    validateResponse(response, currentTransaction);

    PtpResponse ptpResponse =
        new PtpResponse(response.getCode(), response.getTransactionId(), response.getParameters());

    log(ptpResponse.toString());

    if (!ptpResponse.isSuccess()) {

      throw new Exception("GetObjectInfo failed: " + ptpResponse);
    }

    // Ahora sí podemos parsear. Si el parser falla, la sesión PTP ya quedó sincronizada.
    ObjectInfo info = parseObjectInfo(objectHandle, data.getParameters());

    log("ObjectInfo SUCCESS");

    log(String.format("  Format: 0x%04X", info.objectFormat));

    log("  Size: " + info.compressedSize + " bytes");

    log(String.format("  Resolution: %dx%d", info.width, info.height));

    log("  Filename: " + info.filename);

    log("  Capture date: " + info.captureDate);

    return info;
  }

  private void delay(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public synchronized byte[] getObject(long objectHandle) throws Exception {
    log("ENTRO AL GETOBJECT");
    // delay(3000);

    if (!sessionOpen) {
      throw new IllegalStateException("PTP session is not open");
    }

    // delay(3000);

    log("LA SESION SI EXISTE");

    // delay(3000);

    transactionId++;

    long currentTransaction = transactionId;

    log("964");

    log("--------------------------------");
    log("PTP: GetObject");
    log(String.format("Object Handle: 0x%08X", objectHandle));
    log("Transaction ID: " + currentTransaction);

    log("971");

    // delay(3000);

    byte[] command =
        PtpContainer.buildCommand(OP_GET_OBJECT, currentTransaction, (int) objectHandle);

    sendBulkOut(command);

    log("979");

    log("Waiting for Object DATA...");

    // delay(3000);

    // IMPORTANT: The object can be several megabytes. Do not dump the complete payload to the log.
    PtpContainer data = receiveContainer(false);

    log("988");
    // delay(3000);

    if (data.getType() != PtpContainer.TYPE_DATA) {
      throw new Exception("Expected DATA, received " + data.typeToString());
    }

    log("995");
    // delay(3000);

    if (data.getCode() != OP_GET_OBJECT) {
      throw new Exception(String.format("Unexpected DATA code: 0x%04X", data.getCode()));
    }

    log("1002");
    // delay(3000);

    if (data.getTransactionId() != currentTransaction) {
      throw new Exception("GetObject transaction mismatch");
    }

    log("1009");
    // delay(3000);

    byte[] objectData = data.getParameters();

    log("1014");
    // delay(3000);

    log("Object DATA received");
    log("Object size: " + objectData.length + " bytes");

    log("Waiting for Object RESPONSE...");

    // delay(3000);

    PtpContainer response = receiveContainer();

    validateResponse(response, currentTransaction);

    PtpResponse ptpResponse =
        new PtpResponse(response.getCode(), response.getTransactionId(), response.getParameters());

    log(ptpResponse.toString());

    // delay(3000);

    if (!ptpResponse.isSuccess()) {
      throw new Exception("GetObject failed: " + ptpResponse);
    }

    log("GetObject SUCCESS");

    // delay(3000);

    return objectData;
  }

  private ObjectInfo parseObjectInfo(long objectHandle, byte[] data) throws Exception {

    ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

    ObjectInfo info = new ObjectInfo();

    info.objectHandle = objectHandle;

    if (buffer.remaining() < 52) {
      throw new Exception("ObjectInfo data too small: " + buffer.remaining());
    }

    // StorageID
    info.storageId = Integer.toUnsignedLong(buffer.getInt());

    // ObjectFormat
    info.objectFormat = Short.toUnsignedInt(buffer.getShort());

    // ProtectionStatus
    info.protectionStatus = Short.toUnsignedInt(buffer.getShort());

    // ObjectCompressedSize
    info.compressedSize = Integer.toUnsignedLong(buffer.getInt());

    // ThumbFormat
    info.thumbFormat = Short.toUnsignedInt(buffer.getShort());

    // ThumbCompressedSize
    info.thumbCompressedSize = Integer.toUnsignedLong(buffer.getInt());

    // ThumbPixWidth
    info.thumbWidth = Integer.toUnsignedLong(buffer.getInt());

    // ThumbPixHeight
    info.thumbHeight = Integer.toUnsignedLong(buffer.getInt());

    // ImagePixWidth
    info.width = Integer.toUnsignedLong(buffer.getInt());

    // ImagePixHeight
    info.height = Integer.toUnsignedLong(buffer.getInt());

    // ImageBitDepth
    info.bitDepth = Integer.toUnsignedLong(buffer.getInt());

    // ParentObject
    info.parentObject = Integer.toUnsignedLong(buffer.getInt());

    // AssociationType
    info.associationType = Short.toUnsignedInt(buffer.getShort());

    // AssociationDescription
    info.associationDesc = Integer.toUnsignedLong(buffer.getInt());

    // SequenceNumber
    info.sequenceNumber = Integer.toUnsignedLong(buffer.getInt());

    // Filename
    info.filename = readPtpString(buffer);

    // CaptureDate
    info.captureDate = readPtpString(buffer);

    // ModificationDate
    info.modificationDate = readPtpString(buffer);

    // Keywords
    info.keywords = readPtpString(buffer);

    return info;
  }
}
