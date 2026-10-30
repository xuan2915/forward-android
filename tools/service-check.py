"""Exercise the shared-pool AccessibilityService against explicitly fake app hosts on a disposable emulator."""
import os, pathlib, sqlite3, subprocess, sys, time, tempfile, shutil, uuid
sdk=pathlib.Path(os.environ['ANDROID_HOME']);adb=sdk/'platform-tools/adb.exe';device='emulator-5554';root=pathlib.Path('artifacts/qa');root.mkdir(parents=True,exist_ok=True)
def run(*args):return subprocess.check_output([str(adb),'-s',device,*args],stderr=subprocess.STDOUT).decode('utf-8','replace').strip()
def stage(name):print(run('shell','am','instrument','-w','-e','service-stage',name,'com.focus.prototype.test/com.focus.prototype.BusinessChecks'))
def status():
 folder=pathlib.Path(tempfile.gettempdir())/('focus-runtime-'+uuid.uuid4().hex);folder.mkdir()
 try:
  for name in ['journey.db','journey.db-wal','journey.db-shm']:
   result=subprocess.run([str(adb),'-s',device,'exec-out','run-as','com.focus.prototype','cat','databases/'+name],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
   if result.returncode==0:(folder/name).write_bytes(result.stdout)
  connection=sqlite3.connect(folder/'journey.db')
  try:
   day=connection.execute("select max(day) from ledger where kind='video'").fetchone()[0]
   return connection.execute("select coalesce(sum(amount),0) from ledger where day=? and kind='video'",(day,)).fetchone()[0]
  finally:connection.close()
 finally:shutil.rmtree(folder,ignore_errors=True)
prepared=False;enabled=False
try:
 assert device in run('devices'), 'The configured emulator is offline'
 run('install','-r',str(pathlib.Path('app/build/outputs/apk/debug/app-debug.apk').resolve()))
 run('install','-r',str(pathlib.Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk').resolve()))
 for pkg in ['com.ss.android.ugc.aweme','tv.danmaku.bili']:run('install','-r',str(pathlib.Path('app/build/fixtures')/pkg/'host.apk'))
 run('shell','input','keyevent','224');run('shell','input','keyevent','82');run('shell','wm','dismiss-keyguard');time.sleep(1)
 prepared=True;stage('prepare')
 run('shell','settings','put','secure','enabled_accessibility_services','com.focus.prototype/.FocusService');enabled=True
 run('shell','settings','put','secure','accessibility_enabled','1')
 time.sleep(2);print('service binding:',run('shell','dumpsys','accessibility')[-500:])
 print('launch A:',run('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n','com.ss.android.ugc.aweme/android.app.Activity'))
 time.sleep(2);first=status();print('fixture A foreground balance:',first);print('host window events after A:',run('shell','logcat','-d','-s','MeterHost:I'))
 run('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n','tv.danmaku.bili/android.app.Activity')
 time.sleep(2);second=status();print('fixture B foreground balance:',second);print('host window events after B:',run('shell','logcat','-d','-s','MeterHost:I'))
 assert 0<second<first<=20000, 'switch did not consume one shared allowance'
 run('shell','input','keyevent','26');time.sleep(2);screen_off=status();print('screen-off balance:',screen_off)
 time.sleep(2);screen_stable=status();print('screen-off steady balance:',screen_stable)
 assert abs(screen_off-second)<=2000 and abs(screen_stable-screen_off)<=250,'screen-off kept charging after the display turned off'
 run('shell','input','keyevent','26');run('shell','input','keyevent','82');time.sleep(1)
 run('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n','com.ss.android.ugc.aweme/android.app.Activity')
 time.sleep(25);windows=run('shell','dumpsys','window','windows');print('exhausted windows:',[x for x in windows.splitlines() if 'ACCESSIBILITY_OVERLAY' in x or 'com.focus.prototype' in x][-8:])
 assert 'ACCESSIBILITY_OVERLAY' in windows and 'com.focus.prototype' in windows,'empty shared pool did not present the lock overlay'
 image=root/'video-exhaustion.png';image.write_bytes(subprocess.check_output([str(adb),'-s',device,'exec-out','screencap','-p']));print('lock screenshot:',image)
finally:
 if prepared:print(run('shell','am','instrument','-w','-e','service-stage','restore','com.focus.prototype.test/com.focus.prototype.BusinessChecks'))
 if enabled and device in run('devices'):
  run('shell','settings','delete','secure','enabled_accessibility_services')
  run('shell','settings','put','secure','accessibility_enabled','0')
