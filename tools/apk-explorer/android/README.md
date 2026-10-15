# APK Explorer para Android — 0.1.0 preview

App nativa Java para Android 14+, paquete `com.axel.apkexplorer`.
Selecciona una aplicación instalada y ejecuta una exploración limitada en una
pantalla virtual del mismo teléfono. El motor corre en un UserService de Shizuku:
no requiere Python, scrcpy ni un PC dirigiendo el recorrido.

## Uso

1. Instala la APK e inicia [Shizuku](https://shizuku.rikka.app/guide/setup/)
   mediante depuración inalámbrica (o el método admitido por tu dispositivo).
2. Abre APK Explorer, pulsa **Conectar Shizuku** y concede su autorización.
3. Selecciona una app y el máximo de capturas. Pulsa **Explorar en pantalla virtual**.
4. Puedes pasar a otra app. **Detener** solicita terminar y liberar la pantalla.
5. Al finalizar, pulsa **Recuperar y ver capturas**, y después **Exportar ZIP**.
   Incluye PNG, XML, `index.html` y `agent.json` con estados y acciones.

**GitHub:** el botón abre las releases del repositorio elegido en el navegador,
usando la sesión del usuario. Descarga e instalación se realizan allí mediante
Android; vuelve y actualiza la lista. Esta versión no tiene OAuth, descarga
privada integrada, importación de fuentes ni renderizado Paparazzi.

## Límites de esta primera APK

- No es una conversión completa de la herramienta Python: captura apps instaladas;
  el renderizado desde fuentes y el adaptador de proyectos siguen en el PC.
- Requiere Shizuku iniciado y autorizado. La app no concede ese permiso por sí misma.
- Las apps deben admitir pantallas secundarias. Se utilizan APIs internas de Android;
  una incompatibilidad produce error, sin pasar a controlar la pantalla física.
- Comparte datos, proceso y servicios con la app real. Evita usar esa misma app en
  la pantalla principal. No se ha validado escribir simultáneamente en dos pantallas.
- Navega únicamente mediante clics accesibles con etiquetas reconocibles; omite
  controles de edición y acciones sensibles por texto. No garantiza cobertura
  completa ni identifica el significado de todos los controles de una app.
- Hasta 20 capturas, 40 acciones, 3 minutos y 40 MiB de capturas por sesión. No
  reinicia la app, fuerza detenciones ni borra datos. La pantalla tiene política
  de destrucción de sus actividades al liberarse y teclado local.
- Resultados en memoria del servicio hasta recuperarlos. Recupera/exporta antes de
  cerrar el proceso o iniciar otra sesión; un reinicio del servicio pierde esa sesión.
  El último ZIP recuperado queda en el almacenamiento privado de la app.
- El acceso HTTP/API para agentes no está incluido: se entrega el ZIP por el selector
  de archivos de Android. No se publican capturas ni se almacenan tokens de GitHub.

## Compilación

En AXELPC, JDK 17 y SDK 35 (`ANDROID_HOME`):

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --max-workers=2 --no-parallel
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Es una compilación firmada de
depuración para pruebas privadas, no una entrega de producción para Play Store.
Las claves, cachés, resultados privados y archivos compilados se excluyen de Git.

El servicio usa [Shizuku API 13.1.5](https://github.com/RikkaApps/Shizuku-API),
su proveedor y el permiso concedido por el usuario. La pantalla se crea directamente
con DisplayManager y se consulta mediante UiAutomation asociado a su ID.

## Verificación — 2026-10-15

- Compilación en AXELPC con JDK 17, SDK 35 y Gradle 8.9: PASS.
- Tres tests JUnit de política de acciones y escape HTML: PASS.
- Firma APK v2, identidad `com.axel.apkexplorer`, minSdk 34 y targetSdk 35: PASS.
- Instalación de prueba en Pixel 8 Pro físico (API 37): PASS.
- Motor Java del APK ejecutado por `app_process` bajo UID shell, con ALEXIA:
  tres capturas distintas; navegación accesible y liberación del display. La app
  NEXUS de la pantalla física conservó su actividad y foco tras la sesión.
- Revisión visual de la interfaz del explorador en pantalla virtual: PASS.
- Se corrigieron la superposición del reloj del sistema en el display virtual y
  una caché de accesibilidad obsoleta. La captura final de ALEXIA no tiene ese reloj.
- El arranque mediante autorización y Binder de Shizuku **NO PROBADO**: no estaba
  instalado/iniciado en los teléfonos comprobados. El ensayo shell valida el motor,
  pero no sustituye la prueba completa desde los botones de la APK.
- Recuperación/galería/exportación desde la UI y escritura simultánea: NO PROBADO.
  El ZIP del motor sí se recuperó, abrió y revisó desde el PC.
- Evidencia privada: `../sessions/native-apk-pixel-20261015/final/` y `ui/`.
- APK entregado: `APK-Explorer-0.1.0-preview.apk`, SHA-256:
  `731575b46a4082faff57a105b92bd753f4da8c7f56be8b5b1aaee245426b25f8`.
