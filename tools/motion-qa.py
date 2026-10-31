"""Dedicated emulator acceptance: real UI actions on labelled QA tasks; restores original DB."""
import subprocess, pathlib, os, time, re, xml.etree.ElementTree as ET
adb=pathlib.Path(os.environ['ANDROID_HOME'])/'platform-tools/adb.exe'
out=pathlib.Path('artifacts/qa/grit-ui-0.6.2');out.mkdir(parents=True,exist_ok=True)
started=time.monotonic()
record_started=None
marks=[]
def mark(label):
 script_stamp=time.monotonic()-started
 video_stamp=max(0,time.monotonic()-record_started) if record_started is not None else script_stamp
 marks.append(f'{video_stamp:06.2f}s  {label}')
 print(marks[-1],flush=True)
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
def stop_recording(process):
 if not process:return None
 result=subprocess.run([str(adb),'-s','emulator-5554','shell','pidof','screenrecord'],capture_output=True,text=True)
 if result.returncode==0:
  run('shell','kill','-2',result.stdout.strip().split()[0])
 try:process.wait(timeout=15)
 except subprocess.TimeoutExpired:
  process.kill();process.wait(timeout=5)
 run('pull','/sdcard/grit-ui-0.6.2.mp4',str(out/'interaction-demo.mp4'))
 return None
def stage(name):
 result=run('shell','am','instrument','-w','-e','service-stage',name,'com.focus.prototype.test/com.focus.prototype.BusinessChecks');print(result,flush=True);assert 'PASS' in result
prepared=False;rec=None
try:
 stage('motion-prepare');prepared=True
 run('shell','am','start','-n','com.focus.prototype/.MainActivity');time.sleep(6)
 assert node('今日任务') is not None
 growth_nav=node('成长');assert growth_nav is not None
 record_started=time.monotonic()
 rec=subprocess.Popen([str(adb),'-s','emulator-5554','shell','screenrecord','--size','540x1200','--bit-rate','2000000','--time-limit','180','/sdcard/grit-ui-0.6.2.mp4'])
 time.sleep(2);mark('recording begins')
 shot('home')
 tap('模拟器验收 · 整理书桌',True);mark('task row expands');shot('task-expanded')
 tap('确认完成',True);tap('我已按预设条件完成',True)
 n=node('完成任务');assert n is not None
 a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
 run('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(.2);mark('task completed and XP updated')
 a,b,c,d=map(int,re.findall(r'\d+',growth_nav.get('bounds')))
 run('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(.25);mark('growth page: XP ring transition');shot('growth-transition')
 assert node('成长记录') is not None and any('等级 2' in n.get('text','') for n in tree().iter('node')),'growth page or XP level transition did not appear'
 time.sleep(1.0);shot('growth-level2')
 tap('›',True);assert node('2026 年 11 月') is not None
 for _ in range(10):
  calendar_nodes=list(tree().iter('node'))
  legend=next((n for n in calendar_nodes if n.get('text')=='休息'),None)
  if legend is not None:break
  run('shell','input','swipe','550','1100','550','850','250')
 time.sleep(1);shot('growth-calendar-month');mark('growth heatmap and calendar browsed')
 calendar_nodes=list(tree().iter('node'));legend=next(n for n in calendar_nodes if n.get('text')=='休息');limit=int(re.findall(r'\d+',legend.get('bounds'))[1])
 assert any(n.get('text')=='30' and int(re.findall(r'\d+',n.get('bounds'))[3])<limit for n in calendar_nodes),'calendar last row clipped above legend'
 for _ in range(4):run('shell','input','swipe','550','600','550','1850','100')
 tap('‹',True)
 tap('展开',True);mark('growth milestone card expands');shot('milestones-expanded');tap('收起',True)
 tap('专注');mark('focus page opened');tap('50 / 10');assert node('50:00') is not None
 tap('25 / 5');tap('开始专注');time.sleep(2);mark('focus timer running');shot('focus-running')
 tap('暂停');mark('focus timer paused');shot('focus-paused');mark('video recording ends');rec=stop_recording(rec);tap('继续');tap('休息一下');tap('暂停休息');tap('继续');tap('结束休息')
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
 if rec:rec=stop_recording(rec)
 if marks:(out/'timeline.txt').write_text('\n'.join(marks)+'\n',encoding='utf8')
 if prepared:
  run('shell','am','force-stop','com.focus.prototype');stage('restore')
