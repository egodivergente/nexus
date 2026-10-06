# Puente local de NEXUS Agente

Fuente del plugin instalado en `C:/Users/User/plugins/nexus-studio/scripts`.
Dependencia: SDK Python `mcp`, disponible en el entorno local del plugin.

El destino explicito es `com.vidadomu.nexus.agent` del S25 configurado por
`NEXUS_DEVICE_SERIAL`. No cambia de dispositivo ni usa la instalacion antigua
`com.vidadomu.nexus`. El certificado y los datos de ambas apps permanecen separados.

Pruebas: `python -m unittest test_bridge` en esta carpeta con el entorno del plugin.
Estado real: ejecutar `nexus_status`; comprobar appVersion y catalogReady.
Los procesos MCP ya abiertos deben recargar el plugin para usar cambios en Python.

Este puente funciona en Codex local por ADB. No es un conector remoto de ChatGPT
movil. Consulta catalogo y prepara borradores; no adjunta medios ni ejecuta pagos.
No presentar estas limitaciones como una integracion completa.
