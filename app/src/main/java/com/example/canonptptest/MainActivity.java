package com.example.canonptptest;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.example.canonptptest.ptp.PtpClient;
import com.example.canonptptest.ptp.PtpDeviceInfo;
import com.example.canonptptest.ptp.PtpResponse;
import com.example.canonptptest.usb.UsbPtpManager;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends Activity {
  // UI
  private TextView statusText;
  private TextView logText;

  private Button permissionButton;
  private Button ptpButton;
  private Button deviceInfoButton;
  private Button storageIdsButton;

  // USB
  private UsbPtpManager usbPtpManager;

  // PTP
  private PtpClient ptpClient;

  // ACTIVITY || PREPARACION INICIAL
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    createInterface();

    usbPtpManager =
        new UsbPtpManager(
            this,
            new UsbPtpManager.Logger() {

              @Override
              public void log(String message) {

                MainActivity.this.log(message);
              }
            },
            new UsbPtpManager.Callback() {

              @Override
              public void onDeviceFound(UsbDevice device) {

                log("USB device ready for permission");

                setStatus("CÁMARA DETECTADA");

                permissionButton.setEnabled(true);
              }

              @Override
              public void onPermissionGranted(UsbDevice device) {

                log("MainActivity: USB permission granted");

                setStatus("PERMISO USB CONCEDIDO");
              }

              @Override
              public void onPermissionDenied() {

                log("MainActivity: USB permission denied");

                setStatus("PERMISO USB DENEGADO");
              }

              @Override
              public void onPtpConnectionReady(
                  UsbDeviceConnection connection,
                  UsbInterface ptpInterface,
                  UsbEndpoint bulkIn,
                  UsbEndpoint bulkOut,
                  UsbEndpoint interruptIn) {

                log("MainActivity: USB PTP connection ready");

                // Creamos el cliente PTP.

                ptpClient =
                    new PtpClient(
                        connection,
                        bulkIn,
                        bulkOut,
                        new PtpClient.Logger() {
                          @Override
                          public void log(String message) {
                            MainActivity.this.log(message);
                          }
                        });

                log("PtpClient initialized");

                setStatus("USB PTP LISTO");

                ptpButton.setEnabled(true);
              }

              @Override
              public void onError(String message) {

                log("USB ERROR: " + message);

                setStatus("ERROR USB");
              }
            });

    // Registramos el receiver de permisos USB.

    usbPtpManager.registerReceiver();

    log("Application started");

    log("USB Host initialized");
  }

  // UI || CREA Y AÑADE LOS ELEMENTOS A LA PANTALLA JUNTO CON LOS METODOS DE LOS BOTONES
  private void createInterface() {
    LinearLayout root = new LinearLayout(this);

    root.setOrientation(LinearLayout.VERTICAL);

    root.setPadding(32, 32, 32, 32);

    deviceInfoButton = new Button(this);
    deviceInfoButton.setText("GET DEVICE INFO");
    deviceInfoButton.setEnabled(false);

    deviceInfoButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            getDeviceInfo();
          }
        });

    root.addView(deviceInfoButton);

    storageIdsButton = new Button(this);
    storageIdsButton.setText("GET STORAGE IDs");
    storageIdsButton.setEnabled(false);

    storageIdsButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            getStorageIds();
          }
        });

    root.addView(storageIdsButton);

    // TITLE
    TextView title = new TextView(this);
    title.setText("CANON PTP TEST");
    title.setTextSize(26);
    root.addView(title);

    // STATUS
    statusText = new TextView(this);
    statusText.setText("Estado: INICIANDO");
    statusText.setTextSize(18);

    LinearLayout.LayoutParams statusParams =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

    statusParams.setMargins(0, 24, 0, 24);

    root.addView(statusText, statusParams);

    // DETECT USB
    Button detectButton = new Button(this);
    detectButton.setText("DETECTAR USB");

    detectButton.setOnClickListener(
        new View.OnClickListener() {

          @Override
          public void onClick(View v) {

            usbPtpManager.detectUsbDevices();
          }
        });

    root.addView(detectButton);

    // PERMISSION
    permissionButton = new Button(this);
    permissionButton.setText("SOLICITAR PERMISO");
    permissionButton.setEnabled(false);

    permissionButton.setOnClickListener(
        new View.OnClickListener() {

          @Override
          public void onClick(View v) {

            UsbDevice device = usbPtpManager.getCanonDevice();

            if (device == null) {

              log("No Canon device selected");

              log("Running USB detection...");

              usbPtpManager.detectUsbDevices();

              return;
            }

            if (usbPtpManager.hasPermission(device)) {

              log("USB permission already granted");

              // detectUsbDevices() también puede encargarse de abrir la conexión.

              usbPtpManager.detectUsbDevices();

            } else {

              usbPtpManager.requestPermission(device);
            }
          }
        });

    root.addView(permissionButton);

    // OPEN PTP SESSION
    ptpButton = new Button(this);
    ptpButton.setText("ABRIR SESIÓN PTP");
    ptpButton.setEnabled(false);

    ptpButton.setOnClickListener(
        new View.OnClickListener() {

          @Override
          public void onClick(View v) {
            openPtpSession();
          }
        });

    root.addView(ptpButton);

    // LOG TITLE
    TextView logTitle = new TextView(this);
    logTitle.setText("\nLOG");
    logTitle.setTextSize(20);
    root.addView(logTitle);

    // LOG
    logText = new TextView(this);
    logText.setTextSize(14);
    logText.setTextIsSelectable(true);
    ScrollView scrollView = new ScrollView(this);
    scrollView.addView(logText);

    LinearLayout.LayoutParams scrollParams =
        new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);

    root.addView(scrollView, scrollParams);

    setContentView(root);
  }

  // STATUS || RETORNA EL ESTADO || DE QUE NO SE
  private void setStatus(String status) {
    runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            if (statusText != null) {
              statusText.setText("Estado: " + status);
            }
          }
        });
  }

  // LOG || MUESTRA MENSAJE DE LOG
  private void log(String message) {
    runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            if (logText == null) {
              return;
            }

            logText.append(message + "\n");
          }
        });
  }

  // OPEN PTP SESSION
  private void openPtpSession() {

    if (ptpClient == null) {

      log("ERROR: PtpClient is null");

      return;
    }

    ptpButton.setEnabled(false);

    setStatus("ABRIENDO SESIÓN PTP...");

    log("------------------------------------------------");

    log("Starting PTP OpenSession test...");

    // PTP usa USB y puede tardar. Nunca hacemos la operación en el hilo principal de Android.

    new Thread(
            new Runnable() {

              @Override
              public void run() {

                try {

                  PtpResponse response = ptpClient.openSession();

                  runOnUiThread(
                      new Runnable() {

                        @Override
                        public void run() {

                          log("");

                          log("PTP OpenSession SUCCESS");

                          log(response.toString());

                          setStatus("SESIÓN PTP ABIERTA");

                          deviceInfoButton.setEnabled(true);
                          storageIdsButton.setEnabled(true);

                          ptpButton.setEnabled(true);
                        }
                      });

                } catch (Exception e) {

                  runOnUiThread(
                      new Runnable() {

                        @Override
                        public void run() {

                          log("");

                          log("PTP OpenSession ERROR");

                          log("Exception: " + e.getClass().getSimpleName());

                          log("Message: " + e.getMessage());

                          setStatus("ERROR EN OPEN SESSION");

                          ptpButton.setEnabled(true);
                        }
                      });
                }
              }
            })
        .start();
  }

  // DESTROY
  @Override
  protected void onDestroy() {
    if (ptpClient != null) {
      ptpClient.close();
      ptpClient = null;
    }

    if (usbPtpManager != null) {
      usbPtpManager.close();
      usbPtpManager = null;
    }

    super.onDestroy();
  }

  private void delay(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void getStorageIds() {
    storageIdsButton.setEnabled(false);
    setStatus("OBTENIENDO STORAGE IDs...");
    // delay(3000);

    new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  List<Long> ids = ptpClient.getStorageIds();

                  if (!ids.isEmpty()) {
                    long storageId = ids.get(0);

                    runOnUiThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            log("");
                            log("STORAGE ENCONTRADO");
                            log(String.format("Storage ID: 0x%08X", storageId));
                            // delay(3000);
                          }
                        });

                    ptpClient.getStorageInfo(storageId);

                    List<Long> handles = ptpClient.getObjectHandles(storageId);

                    log("");
                    log("================================");
                    log("ANALIZANDO OBJETOS");
                    log("================================");
                    // delay(3000);

                    int images = 0;

                    for (Long handle : handles) {
                      try {
                        PtpClient.ObjectInfo info = ptpClient.getObjectInfo(handle);

                        try {
                          downloadImage(info);
                        } catch (Exception e) {
                          log("ERROR descargando " + info.filename);
                          log(e.getMessage() != null ? e.getMessage() : e.toString());
                        }

                        if (info.objectFormat == 0x3801
                            && info.filename != null
                            && info.filename.toUpperCase(java.util.Locale.ROOT).endsWith(".JPG")) {

                          log("");
                          log("================================");
                          log("DESCARGANDO " + info.filename);
                          log("================================");

                          try {
                            byte[] imageData = ptpClient.getObject(info.objectHandle);

                            log("Imagen recibida correctamente");
                            log("Bytes recibidos: " + imageData.length);

                            Uri savedUri = saveImageToGallery(info.filename, imageData);

                            log("================================");
                            log("IMAGEN GUARDADA EN LA GALERIA");
                            log("================================");
                            log("Archivo: " + info.filename);
                            log("URI: " + savedUri);
                            log("Tamaño: " + imageData.length + " bytes");
                            log("================================");

                          } catch (Exception e) {
                            log("ERROR DESCARGANDO " + info.filename);
                            log(e.getMessage() != null ? e.getMessage() : e.toString());
                          }
                        }

                        if (info.isImage()) {
                          images++;

                          log("");
                          log(String.format("IMAGE #%d", images));
                          log(String.format("  ID: 0x%08X", info.objectHandle));
                          log(String.format("  Format: 0x%04X", info.objectFormat));
                          log(String.format("  Size: %d bytes", info.compressedSize));
                          log(String.format("  Resolution: %dx%d", info.width, info.height));
                          log("  Filename: " + info.filename);
                          log("  Capture date: " + info.captureDate);
                          // delay(3000);
                        }

                      } catch (Exception e) {
                        log(String.format("ERROR Object 0x%08X: %s", handle, e.getMessage()));
                        // delay(3000);
                      }
                    }

                    int finalImages = images;

                    runOnUiThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            log("");
                            log("================================");
                            log("RESULTADO");
                            log("Objetos: " + handles.size());
                            log("Imagenes: " + finalImages);
                            log("================================");
                            // delay(3000);

                            setStatus("IMAGENES ENCONTRADAS: " + finalImages);

                            storageIdsButton.setEnabled(true);
                          }
                        });

                  } else {
                    throw new Exception("La cámara no reportó ningún storage");
                  }

                } catch (Exception e) {
                  log("");
                  log("GET STORAGE IDs ERROR");
                  log("Exception: " + e.getClass().getSimpleName());
                  log("Message: " + (e.getMessage() != null ? e.getMessage() : e.toString()));

                  log("Intentando recuperar la sesión PTP...");

                  boolean recovered = false;

                  try {
                    ptpClient.recoverSession();
                    recovered = true;

                    log("La sesión PTP se recuperó correctamente.");
                    log("Puedes volver a intentar la operación.");

                  } catch (Exception recoveryError) {
                    log("No fue posible recuperar la sesión.");
                    log(
                        recoveryError.getMessage() != null
                            ? recoveryError.getMessage()
                            : recoveryError.toString());
                  }

                  final boolean sessionRecovered = recovered;

                  runOnUiThread(
                      () -> {
                        setStatus(
                            sessionRecovered
                                ? "SESION RECUPERADA. INTENTA DE NUEVO"
                                : "ERROR PTP. REVISA EL LOG");

                        storageIdsButton.setEnabled(true);
                      });
                }
              }
            })
        .start();
  }

  private void getDeviceInfo() {
    // ptpCliente variable privada que contiene al objeto ptpClient EXTERNO
    // SI SE IMPORTA CORRECTAMENTE CONTINUA
    if (ptpClient == null) {
      log("ERROR: PtpClient is null");
      return;
    }
    deviceInfoButton.setEnabled(false);

    setStatus("OBTENIENDO DEVICE INFO...");

    log("");
    log("--------------------------------");
    log("Starting PTP GetDeviceInfo...");
    log("--------------------------------");

    new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  PtpDeviceInfo info = ptpClient.getDeviceInfo();

                  runOnUiThread(
                      new Runnable() {
                        @Override
                        public void run() {
                          log("");
                          log("GET DEVICE INFO SUCCESS");
                          log("Manufacturer: " + info.manufacturer);
                          log("Model: " + info.model);
                          log("Device Version: " + info.deviceVersion);
                          log("Serial: " + info.serialNumber);

                          setStatus("DEVICE INFO RECIBIDA");

                          deviceInfoButton.setEnabled(true);
                        }
                      });

                } catch (Exception e) {
                  runOnUiThread(
                      new Runnable() {
                        @Override
                        public void run() {
                          log("");
                          log("GET DEVICE INFO ERROR");
                          log("Exception: " + e.getClass().getSimpleName());
                          log("Message: " + e.getMessage());
                          setStatus("ERROR EN DEVICE INFO");
                          deviceInfoButton.setEnabled(true);
                        }
                      });
                }
              }
            })
        .start();
  }

  private Uri saveImageToGallery(String filename, byte[] imageData) throws Exception {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      throw new Exception("Este método requiere Android 10 o superior.");
    }

    ContentValues values = new ContentValues();
    values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
    values.put(
        MediaStore.Images.Media.RELATIVE_PATH,
        android.os.Environment.DIRECTORY_PICTURES + "/CanonPTPTest");
    values.put(MediaStore.Images.Media.IS_PENDING, 1);

    Uri imageUri =
        getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

    if (imageUri == null) {
      throw new Exception("No se pudo crear el archivo en la galería.");
    }

    try {
      try (OutputStream output = getContentResolver().openOutputStream(imageUri, "w")) {

        if (output == null) {
          throw new Exception("No se pudo abrir el archivo de destino.");
        }

        output.write(imageData);
        output.flush();
      }

      ContentValues completed = new ContentValues();
      completed.put(MediaStore.Images.Media.IS_PENDING, 0);

      int updated = getContentResolver().update(imageUri, completed, null, null);

      if (updated == 0) {
        throw new Exception("No se pudo finalizar el archivo de imagen.");
      }

      return imageUri;

    } catch (Exception e) {
      getContentResolver().delete(imageUri, null, null);
      throw e;
    }
  }

  private boolean imageAlreadyExists(String filename) throws Exception {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
      throw new Exception("Se requiere Android 10 o superior.");
    }

    String relativePath = android.os.Environment.DIRECTORY_PICTURES + "/CanonPTPTest/";

    String[] projection = {MediaStore.Images.Media._ID};

    String selection =
        MediaStore.Images.Media.DISPLAY_NAME
            + " = ? AND "
            + MediaStore.Images.Media.RELATIVE_PATH
            + " = ?";

    String[] selectionArgs = {filename, relativePath};

    try (Cursor cursor =
        getContentResolver()
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null)) {

      return cursor != null && cursor.moveToFirst();
    }
  }

  private boolean downloadImage(long objectHandle) throws Exception {
    PtpClient.ObjectInfo info = ptpClient.getObjectInfo(objectHandle);
    return downloadImage(info);
  }

  private boolean downloadImage(PtpClient.ObjectInfo info) throws Exception {
    if (info == null || info.filename == null) {
      throw new Exception("No se pudo obtener la información de la imagen.");
    }

    // Solo descargamos JPEG; por ahora omitimos los archivos RAW/CR2.
    if (info.objectFormat != 0x3801
        || !info.filename.toUpperCase(java.util.Locale.ROOT).endsWith(".JPG")) {
      log("OMITIDA: no es un archivo JPG: " + info.filename);
      return false;
    }

    log("================================");
    log("VERIFICANDO: " + info.filename);
    log(String.format("Object Handle: 0x%08X", info.objectHandle));

    if (imageAlreadyExists(info.filename)) {
      log("OMITIDA: la imagen ya existe en el celular.");
      log("Archivo: " + info.filename);
      return false;
    }

    log("Descargando desde la cámara...");

    byte[] imageData = ptpClient.getObject(info.objectHandle);

    if (imageData == null || imageData.length == 0) {
      throw new Exception("La cámara devolvió una imagen vacía.");
    }

    Uri savedUri = saveImageToGallery(info.filename, imageData);

    log("DESCARGA COMPLETADA");
    log("Archivo: " + info.filename);
    log("Tamaño: " + imageData.length + " bytes");
    log("Ubicación: " + savedUri);

    return true;
  }
}
