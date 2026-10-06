from pathlib import Path
import os,subprocess,zipfile
root=Path(__file__).resolve().parent
sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'
jdk=Path('C:/Program Files/Microsoft/jdk-17.0.20.8-hotspot/bin')
build=root/'transfer-build';build.mkdir(exist_ok=True)
classes=build/'classes';classes.mkdir(exist_ok=True)
dex=build/'dex';dex.mkdir(exist_ok=True)
android=sdk/'platforms/android-35/android.jar';bt=sdk/'build-tools/35.0.0'
def run(*args):
 result=subprocess.run(list(map(str,args)),capture_output=True)
 if result.returncode: raise RuntimeError('Falló herramienta '+Path(str(args[0])).name+'; código '+str(result.returncode))
run(jdk/'javac.exe','-J-Xmx128m','--release','8','-cp',android,'-d',classes,root/'Transfer.java')
run(jdk/'java.exe','-Xmx128m','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','26','--output',dex,classes/'com/vidadomu/nexus/transfer/Transfer.class')
apk=build/'transfer.apk'
run(bt/'aapt2.exe','link','--manifest',root/'AndroidManifest.xml','-I',android,'-o',apk)
with zipfile.ZipFile(apk,'a',zipfile.ZIP_DEFLATED) as z:z.write(dex/'classes.dex','classes.dex')
run(jdk/'java.exe','-Xmx128m','-jar',bt/'lib/apksigner.jar','sign','--ks',Path.home()/'.android/debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android',apk)
print('Herramienta temporal de transferencia compilada; sin permisos de red.')
