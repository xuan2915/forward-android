"""Capture real 0.7.0 screens and exercise task, growth, and focus flows on the disposable emulator."""
import os
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = pathlib.Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb.exe"
DEVICE = "emulator-5554"
PACKAGE = "com.focus.prototype"
RUNNER = "com.focus.prototype.test/com.focus.prototype.BusinessChecks"
OUT = pathlib.Path("artifacts/qa/grit-ui-0.7.0")
OUT.mkdir(parents=True, exist_ok=True)
started = time.monotonic()
record_started = None
marks = []
recorder = None
prepared = False


def adb(*args):
    return subprocess.check_output([str(ADB), "-s", DEVICE, *args])


def stage(name):
    result = adb("shell", "am", "instrument", "-w", "-e", "service-stage", name, RUNNER).decode("utf-8", "replace")
    print(result, flush=True)
    if "PASS" not in result:
        raise RuntimeError(f"emulator fixture stage failed: {name}")


def nodes():
    for _ in range(6):
        adb("shell", "uiautomator", "dump", "/sdcard/ui-ux.xml")
        try:
            root = ET.fromstring(adb("shell", "cat", "/sdcard/ui-ux.xml").decode("utf-8", "replace"))
            return list(root.iter("node"))
        except ET.ParseError:
            time.sleep(.7)
    raise RuntimeError("accessibility tree did not settle")


def find(label):
    return next((node for node in nodes() if node.get("text") == label), None)


def tap(label, scroll=False):
    for _ in range(8 if scroll else 1):
        item = find(label)
        if item is not None:
            left, top, right, bottom = map(int, re.findall(r"\d+", item.get("bounds")))
            adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            time.sleep(.45)
            return
        if scroll:
            adb("shell", "input", "swipe", "550", "1850", "550", "620", "300")
            time.sleep(.25)
    raise RuntimeError(f"not visible: {label}")


def mark(label):
    stamp = time.monotonic() - record_started if record_started is not None else time.monotonic() - started
    marks.append(f"{stamp:05.1f}s  {label}")
    print(marks[-1], flush=True)


def shot(name):
    (OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))


def stop_recording():
    global recorder
    if recorder is None:
        return
    result = subprocess.run([str(ADB), "-s", DEVICE, "shell", "pidof", "screenrecord"], capture_output=True, text=True)
    if result.returncode == 0:
        adb("shell", "kill", "-2", result.stdout.strip().split()[0])
    try:
        recorder.wait(timeout=15)
    except subprocess.TimeoutExpired:
        recorder.kill()
        recorder.wait(timeout=5)
    subprocess.run([str(ADB), "-s", DEVICE, "pull", "/sdcard/self-discipline-0.7.0.mp4", str(OUT / "interaction-demo.mp4")], check=True)
    recorder = None


try:
    stage("motion-prepare")
    prepared = True
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(7)
    if find("今日任务") is None:
        raise RuntimeError("home did not render")
    shot("home")

    record_started = time.monotonic()
    recorder = subprocess.Popen([
        str(ADB), "-s", DEVICE, "shell", "screenrecord", "--size", "540x1200",
        "--bit-rate", "1800000", "--time-limit", "140", "/sdcard/self-discipline-0.7.0.mp4",
    ])
    time.sleep(1.5)
    mark("recording starts")

    tap("模拟器验收 · 整理书桌")
    time.sleep(.5)
    if find("完成证明") is None or find("完成自检") is None:
        raise RuntimeError("task detail sheet content missing")
    time.sleep(2)
    shot("task-detail")
    mark("compact task opens detail sheet")
    tap("开始专注")
    if find("专注时间") is None and find("正在专注") is None:
        time.sleep(.7)
    mark("task focus starts and opens focus screen")
    shot("focus-running")
    tap("暂停")
    mark("focus pauses")
    shot("focus-paused")
    tap("继续")
    tap("结束本次")
    mark("focus resumes and ends")

    tap("今天")
    tap("模拟器验收 · 整理书桌")
    tap("完成自检", scroll=True)
    tap("我已按预设条件完成", scroll=True)
    tap("确认完成任务", scroll=True)
    time.sleep(1)
    shot("completion-check")
    if find("已完成") is None:
        print("visible after completion attempt:", [node.get("text") for node in nodes() if node.get("text")], flush=True)
        raise RuntimeError("task completion state did not update")
    mark("task completes and XP settles")
    shot("task-completed")
    adb("shell", "input", "keyevent", "4")

    tap("成长")
    time.sleep(1.5)
    if not any((node.get("text") or "").startswith("等级 2") for node in nodes()):
        raise RuntimeError("XP/level did not update after task completion")
    mark("growth level and completion ring update")
    shot("growth")
    tap("趋势")
    time.sleep(.7)
    shot("growth-trends")
    tap("里程碑")
    time.sleep(.7)
    shot("growth-milestones")
    mark("growth calendar, trends, and milestones tabs switch")

    tap("专注")
    time.sleep(1)
    shot("focus-ready")
    tap("开始专注")
    time.sleep(1.2)
    tap("暂停")
    shot("focus-paused")
    mark("focus play/pause morph is exercised")
    tap("继续")
    tap("结束本次")

    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(8)
    tap("成长")
    growth_text = [node.get("text") or "" for node in nodes()]
    if not any(text.startswith("等级 2") for text in growth_text) or not any(text == "500" for text in growth_text):
        raise RuntimeError("saved XP did not survive app restart")
    mark("XP remains after force-stop and relaunch")
    mark("recording ends")
    stop_recording()
    (OUT / "timeline.txt").write_text("\n".join(marks) + "\n", encoding="utf-8")
    print("PASS UI: home, task detail, completion, XP growth, tab transitions, focus start/pause/resume/end, persisted XP", flush=True)
finally:
    stop_recording()
    if prepared:
        adb("shell", "am", "force-stop", PACKAGE)
        stage("restore")
