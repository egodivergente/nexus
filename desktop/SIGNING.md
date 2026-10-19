# Firma de Nexus Desktop

La distribución creada por `desktop/tools/package_native.py` se marca como `UNSIGNED`. No debe presentarse como una versión firmada ni como una entrega lista para distribución pública.

Antes de publicar una versión nativa, firma el ejecutable y los artefactos de instalación con una identidad de editor válida para el canal de distribución elegido. Verifica cada binario antes de entregarlo:

```powershell
Get-AuthenticodeSignature .\desktop\dist\Nexus.exe | Format-List Status, StatusMessage, SignerCertificate
```

Una firma válida confirma la identidad e integridad del binario; la compilación y las pruebas funcionales siguen siendo comprobaciones separadas.
