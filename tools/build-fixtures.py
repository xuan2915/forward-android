"""Build blank test hosts for the shared-pool package matcher. These are NOT real apps."""
import os, pathlib, subprocess, zipfile
sdk=pathlib.Path(os.environ['ANDROID_HOME']);java=pathlib.Path(os.environ['JAVA_HOME'])/'bin';out=pathlib.Path('app/build/fixtures');out.mkdir(parents=True,exist_ok=True)
for package,label in [('com.ss.android.ugc.aweme','Meter Test A'),('tv.danmaku.bili','Meter Test B')]:
 path=out/package;path.mkdir(exist_ok=True)
 (path/'AndroidManifest.xml').write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:label="{label}" android:theme="@android:style/Theme.Material.Light"><activity android:name="android.app.Activity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>''',encoding='utf-8')
 subprocess.run([str(sdk/'build-tools/35.0.0/aapt2.exe'),'link','-I',str(sdk/'platforms/android-35/android.jar'),'--manifest',str(path/'AndroidManifest.xml'),'-o',str(path/'host.apk')],check=True)
 source=path/'FixtureActivity.java';source.write_text('package fixture; public class FixtureActivity extends android.app.Activity {}',encoding='utf-8')
 classes=path/'classes';classes.mkdir(exist_ok=True)
 subprocess.run([str(java/'javac.exe'),'-cp',str(sdk/'platforms/android-35/android.jar'),'-d',str(classes),str(source)],check=True)
 dex=path/'dex';dex.mkdir(exist_ok=True)
 subprocess.run([str(sdk/'build-tools/35.0.0/d8.bat'),'--lib',str(sdk/'platforms/android-35/android.jar'),'--min-api','26','--output',str(dex),str(classes/'fixture/FixtureActivity.class')],check=True)
 with zipfile.ZipFile(path/'host.apk','a',zipfile.ZIP_DEFLATED) as archive:archive.write(dex/'classes.dex','classes.dex')
 subprocess.run([str(sdk/'build-tools/35.0.0/apksigner.bat'),'sign','--ks',str(pathlib.Path.home()/'.android/debug.keystore'),'--ks-pass','pass:android','--key-pass','pass:android',str(path/'host.apk')],check=True)
 print(path/'host.apk')
