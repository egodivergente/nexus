# Nexus Desktop

Este directorio contiene el host Windows de Nexus Studio y la interfaz local que sirve mediante WebView2.

## Compilación

Desde la raíz del repositorio:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\desktop\build.ps1
python .\desktop\tools\package_native.py
```

`build.ps1` deja juntos en `desktop/dist` el ejecutable, las DLL de WebView2, la interfaz, el host local, los módulos de Flow y el runtime del agente. `package_native.py` valida que esas rutas estén presentes, comprueba CRC y rechaza nombres de rutas de estado, credenciales o medios antes de crear el ZIP.

## Distribución

El paquete nativo necesita mantenerse como una carpeta completa; `Nexus.exe` no es un ejecutable autónomo. Requiere Windows x64, .NET Framework compatible y WebView2 Runtime.

La distribución generada actualmente se marca como `UNSIGNED`. Antes de distribuirla fuera de un entorno controlado, comprueba la firma y sigue la guía de [SIGNING.md](SIGNING.md).
