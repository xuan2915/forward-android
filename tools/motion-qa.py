"""Dedicated emulator acceptance: real UI actions on labelled QA tasks; restores original DB."""
import subprocess, pathlib, os, time, re, xml.etree.ElementTree as ET
adb=pathlib.Path(os.environ['ANDROID_HOME'])/'platform-tools/adb.exe'
out=pathlib.Path('artifacts/qa/motion-0.6.1');out.mkdir(parents=True,exist_ok=True)
def run(*args):return subprocess.check_output([str(adb),'-s','emulator-5554',*args]).decode('utf8','replace')
def tree():
 for _ in range(3):
  run('shell','uiautomator','dump','/sdcard/motion.xml')
  try:return ET.fromstring(run('shell','cat','/sdcard/motion.xml'))
  except (ET.ParseError,subprocess.CalledProcessError):time.sleep(1)
 raise RuntimeError('UI tree unavailable')
def node(label):return next((n for n in tree().iter('node') if n.get('text')==label),None)
def tap(label,scroll=False):
 for _ in range(6 if scroll else 1):
  n=node(label)
  if n is not None:
   a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')));run('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(.25);return
  run('shell','input','swipe','550','1800','550','650','300')
 raise RuntimeError('not visible: '+label)
def shot(name): (out/(name+'.png')).write_bytes(subprocess.check_output([str(adb),'-s','emulator-5554','exec-out','screencap','-p']))
def stage(name):
 result=run('shell','am','instrument','-w','-e','service-stage',name,'com.focus.prototype.test/com.focus.prototype.BusinessChecks');print(result,flush=True);assert 'PASS' in result
prepared=False;rec=None
try:
 stage('motion-prepare');prepared=True
 run('shell','am','start','-n','com.focus.prototype/.MainActivity');time.sleep(6)
 assert node('今日进度') is not None
 rec=subprocess.Popen([str(adb),'-s','emulator-5554','shell','screenrecord','--size','540x1200','--bit-rate','2000000','--time-limit','180','/sdcard/motion-0.6.1.mp4'])
 shot('home')
 tap('模拟器验收 · 整理书桌',True);shot('task-expanded')
 tap('确认完成',True);tap('我已按预设条件完成',True)
 n=node('完成任务');assert n is not None
 a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
 run('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(1.4)
 run('shell','input','tap','740','2190');time.sleep(1.5);shot('growth-level2')
 assert any('等级 2' in n.get('text','') for n in tree().iter('node')),'XP level did not advance'
 tap('›',True);assert node('2026 年 11 月') is not None
 run('shell','input','swipe','550','1600','550','1300','1000');time.sleep(1);shot('six-week-calendar')
 calendar_nodes=list(tree().iter('node'));legend=next(n for n in calendar_nodes if n.get('text')=='休息');limit=int(re.findall(r'\d+',legend.get('bounds'))[1])
 assert any(n.get('text')=='30' and int(re.findall(r'\d+',n.get('bounds'))[3])<limit for n in calendar_nodes),'six-week calendar last row clipped'
 for _ in range(4):run('shell','input','swipe','550','600','550','1850','100')
 tap('‹',True)
 tap('展开',True);shot('milestones-expanded');tap('收起',True)
 tap('专注');tap('50 / 10');assert node('50:00') is not None
 tap('25 / 5');tap('开始专注');time.sleep(2);shot('focus-running')
 tap('暂停');shot('focus-paused');tap('继续');tap('休息一下');tap('暂停休息');tap('继续');tap('结束休息')
 tap('奖励');shot('rewards');tap('复盘');shot('review')
 tap('今天');tap('模拟器验收 · 整理书桌',True);tap('撤销误完成',True);tap('确认撤销',True)
 tap('成长');time.sleep(1.5)
 assert any('等级 1' in n.get('text','') for n in tree().iter('node')),'revoke did not restore XP'
 time.sleep(3);tap('奖励');tap('成长')
 assert not any('已升级' in n.get('text','') for n in tree().iter('node')),'settled reward replayed on page entry'
 tap('专注');tap('自由');tap('开始专注');time.sleep(1);tap('暂停');tap('结束本次')
 run('shell','settings','put','global','animator_duration_scale','0')
 tap('专注');tap('开始专注');tap('暂停');assert node('继续') is not None;tap('结束本次')
 print('PASS UI: completion, level 400->500, milestone expand/collapse, 50/10 and 25/5 and free, revoke, no reward replay, start/pause/resume/break/end, reduced motion state convergence',flush=True)
finally:
 run('shell','settings','put','global','animator_duration_scale','1')
 if rec:
  pid=run('shell','pidof','screenrecord').strip()
  if pid:run('shell','kill','-2',pid)
  rec.wait(timeout=15);run('pull','/sdcard/motion-0.6.1.mp4',str(out/'interaction-demo.mp4'))
 if prepared:
  run('shell','am','force-stop','com.focus.prototype');stage('restore')
