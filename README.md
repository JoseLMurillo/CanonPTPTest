# CanonPTPTest

Aplicación Android desarrollada en Java para probar la comunicación USB entre un dispositivo Android y cámaras digitales Canon compatibles con el protocolo PTP (*Picture Transfer Protocol*).

El proyecto busca establecer una conexión directa con la cámara, consultar sus capacidades y explorar las fotografías almacenadas en ella desde el celular, como base para desarrollar posteriormente una aplicación de visualización y gestión de imágenes.

## Objetivos del proyecto

- Detectar cámaras Canon conectadas al celular mediante USB OTG.
- Solicitar y gestionar los permisos de acceso al dispositivo USB.
- Establecer una sesión de comunicación PTP.
- Consultar la información del dispositivo y sus unidades de almacenamiento.
- Obtener los identificadores de los objetos almacenados en la cámara.
- Investigar la lectura de metadatos y miniaturas sin transferir los archivos originales.
- Explorar la posibilidad de visualizar fotografías directamente desde la memoria de la cámara.
- Evaluar la viabilidad de implementar Live View para utilizar el celular como pantalla externa de la cámara.

## Tecnologías utilizadas

- **Lenguaje:** Java.
- **Plataforma:** Android.
- **Comunicación:** USB Host y USB OTG.
- **Protocolo de comunicación:** PTP (*Picture Transfer Protocol*).
- **Dispositivo de prueba:** Canon EOS Rebel T3.
- **Herramientas de desarrollo:** Android SDK y Gradle.

## Arquitectura y funcionamiento

La aplicación se comunica directamente con la cámara a través de una conexión USB. Android identifica el dispositivo y permite solicitar los permisos necesarios para acceder a sus interfaces USB.

El flujo de comunicación previsto es el siguiente:

1. Detectar los dispositivos USB conectados.
2. Identificar la cámara mediante sus identificadores USB (VID/PID).
3. Solicitar autorización para acceder al dispositivo.
4. Obtener y configurar la interfaz USB compatible con PTP.
5. Abrir una sesión PTP.
6. Consultar la información de la cámara.
7. Obtener los identificadores de almacenamiento disponibles.
8. Explorar los objetos almacenados y probar la lectura de sus metadatos.
9. Investigar la obtención de miniaturas y la visualización de fotografías.

## Compatibilidad

### Dispositivo probado

**Canon EOS Rebel T3**

La cámara ha sido identificada mediante USB y se han probado las operaciones iniciales de comunicación PTP.

La compatibilidad con otros modelos Canon todavía debe verificarse individualmente. La disponibilidad de funciones específicas depende del modelo de cámara, el firmware y las operaciones PTP que admita.

### Requisitos

- Un dispositivo Android compatible con USB Host.
- Un cable USB OTG o adaptador compatible con el celular y la cámara.
- Una cámara Canon compatible con las operaciones PTP utilizadas.
- Android SDK y Gradle para compilar el proyecto.

## Estado del desarrollo

El proyecto se encuentra en fase de pruebas y desarrollo.

| Funcionalidad | Estado |
|---|---|
| Detección del dispositivo USB | Probada |
| Identificación de la cámara Canon | Probada |
| Solicitud de permisos USB | Implementada y probada |
| Apertura de sesión PTP | Probada |
| Consulta de información del dispositivo | En pruebas |
| Consulta de identificadores de almacenamiento | En pruebas |
| Listado completo de fotografías | Pendiente de validación |
| Lectura de metadatos de imágenes | Pendiente de validación |
| Obtención de miniaturas mediante PTP | Pendiente de implementación y pruebas |
| Visualización de fotografías sin almacenamiento permanente | Pendiente |
| Visualización Live View en tiempo real | En investigación |

Los estados anteriores corresponden al avance conocido del proyecto y no implican que todas las operaciones funcionen de manera estable en cada dispositivo.

## Próximas mejoras

### 1. Exploración de la galería

Implementar la consulta de los objetos almacenados en la cámara y mostrar las fotografías en una interfaz de galería.

### 2. Visualización de miniaturas

Investigar y utilizar las operaciones PTP compatibles con la cámara para obtener miniaturas, reduciendo la cantidad de datos transferidos por USB.

### 3. Visualización de fotografías

Permitir abrir fotografías seleccionadas en pantalla completa sin guardar necesariamente una copia permanente en el almacenamiento del celular.

### 4. Estabilidad de la comunicación USB

Mejorar el manejo de transferencias, errores, permisos, desconexiones y liberación de recursos para evitar bloqueos o reinicios del dispositivo Android.

### 5. Investigación de Live View

Evaluar la posibilidad de mostrar en tiempo real la imagen captada por la cámara y utilizar el celular como pantalla auxiliar.

Esta funcionalidad requiere investigación adicional y no está garantizada mediante las operaciones PTP básicas.

## Consideraciones técnicas

- No todas las cámaras implementan las mismas operaciones PTP.
- La detección USB no garantiza que todas las funciones de la cámara estén disponibles.
- Las operaciones que transfieren imágenes completas pueden consumir cantidades importantes de memoria.
- Las transferencias USB deben ejecutarse de forma controlada, evitando operaciones bloqueantes en el hilo principal de Android.
- Los permisos USB deben solicitarse y gestionarse de acuerdo con el modelo de permisos de Android.
- Las operaciones PTP deben respetar el ciclo de vida de la conexión y cerrar correctamente las sesiones y los recursos utilizados.
- Las funciones de Live View pueden requerir comandos específicos del fabricante y no deben confundirse con la transferencia convencional de fotografías.

## Instalación y ejecución

1. Clona el repositorio:

   ```bash
   git clone <URL_DEL_REPOSITORIO>
   ```

2. Abre el proyecto en un entorno compatible con Android y Gradle.

3. Configura el Android SDK requerido por el proyecto.

4. Compila e instala la aplicación en un dispositivo Android compatible con USB Host.

5. Conecta la cámara mediante USB OTG.

6. Abre la aplicación, detecta el dispositivo y concede los permisos USB cuando Android los solicite.

7. Ejecuta las operaciones PTP disponibles desde la interfaz de pruebas.

> **Importante:** La configuración exacta de compilación depende de los archivos Gradle y del SDK definidos en el repositorio.

## Seguridad y manejo de errores

Durante las pruebas de comunicación USB se debe tener especial cuidado con las transferencias de datos y el manejo de memoria.

Una operación PTP que no se complete correctamente puede provocar errores en la aplicación o en la comunicación con el dispositivo. Por ello, las pruebas deben realizarse de forma incremental y con registros de diagnóstico.

Se recomienda verificar cada operación antes de continuar con la siguiente y liberar adecuadamente los recursos USB al cerrar la aplicación o desconectar la cámara.

## Contribuciones

Las contribuciones, pruebas de compatibilidad y reportes de errores son bienvenidos.

Para reportar un problema, incluye:

- Modelo de la cámara.
- Modelo y versión de Android del celular.
- Tipo de conexión o adaptador USB utilizado.
- Operación PTP ejecutada.
- Mensajes de error o registros relevantes.

## Licencia

La licencia del proyecto está pendiente de definir.

Hasta que se agregue un archivo de licencia al repositorio, no debe asumirse que el código se encuentra bajo una licencia de software libre específica.

---

**CanonPTPTest** — Proyecto experimental de comunicación USB/PTP entre Android y cámaras Canon.