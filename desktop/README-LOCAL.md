# Nexus Studio privado para este EliteBook

Nexus ya está instalado como acceso directo en el escritorio: **Nexus Studio**. Se abre en una ventana independiente de Microsoft Edge y funciona mediante un servicio local de Python, ambos firmados e instalados en este equipo. El servicio escucha exclusivamente en `127.0.0.1` y usa un puerto variable. No se han cambiado las protecciones de Windows.

Las tres claves API siguen en `%LOCALAPPDATA%/NexusDesktop/credentials.dpapi`, cifradas para el usuario actual. El paquete no las contiene. También permanecen allí el estado, las 85 tareas importadas y las 84 referencias de resultados. Si extraes el ZIP en otro ordenador, las claves no se trasladarán con él.

Para recrear el acceso directo después de extraer el paquete, ejecuta `powershell -NoProfile -File install-local.ps1` desde la carpeta extraída. En este EliteBook ya está hecho. Cerrar la ventana de Edge no borra los datos; el servicio local puede seguir activo hasta cerrar sesión o reiniciar. Abrir de nuevo el acceso directo reutiliza la instancia si sigue abierta.

Verificado en este EliteBook: ventana `NEXUS Studio` visible, catálogo de 838 modelos, historial de 85 tareas, tres credenciales descifradas por el usuario actual, consulta de saldo/organización de Atlas Cloud, Runway y Crun mediante rutas de solo lectura, rechazo de peticiones locales sin sesión. 20 pruebas de motor pasaron en el ASUS. No se hicieron generaciones ni subidas y no se consumió saldo.

La interfaz de escritorio y el host local son una adaptación del proyecto Android; no se ha demostrado la compatibilidad individual de los 838 modelos ni los flujos completos de subida y generación. Generar exige una estimación de coste vigente. El ejecutable nativo `Nexus.exe` sigue bloqueado por Smart App Control porque está sin firmar; este acceso directo utiliza la edición local y **no ejecuta ese archivo**.

Actualización visual y de catálogo: ahora la guía rápida enlaza 40 rutas reales agrupadas por modelo, mientras el catálogo completo permanece accesible debajo y por búsqueda. No se ocultó automáticamente ningún modelo. Cada ficha individual muestra para qué sirve y cuándo evitarla según entradas y capacidades; esos textos no equivalen a una prueba comparativa de calidad. Se añadieron menús oscuros con teclado, correcciones de nombres y precios, y un inicio más directo en el lienzo. La auditoría con capturas está en `docs/Desktop-audit-20261002.md` del proyecto de origen.
