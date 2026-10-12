#!/usr/bin/env python3
"""Release smoke test on an emulator (Part 8): the R8-shrunk release APK, signed with a throw-away key, installs, starts, goes
through onboarding to Today and opens today's session, without a crash. It is driven from outside with adb and UI Automator, so
nothing in the shipped app is changed for it.

    python3 -I tools/release_smoke.py <signed-release.apk>

The screen texts are read from the app's own string resources. Each step prints a GitHub annotation; on failure the annotation
names the step and the words on screen, and the exit code is 1.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PKG = "com.personalfitnesscoach"
ACTIVITY = f"{PKG}/.app.MainActivity"


def strings():
    out = {}
    for f in sorted((ROOT / "app/src/main/res/values").glob("strings*.xml")):
        for e in ET.parse(f).getroot().findall("string"):
            t = "".join(e.itertext()).replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
            out[e.get("name")] = t
    return out


S = strings()


def adb(*args, timeout=60, check=False):
    r = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}: {r.stderr.strip() or r.stdout.strip()}")
    return r.stdout


class Node:
    def __init__(self, e):
        self.text = (e.get("text") or "").strip()
        self.desc = (e.get("content-desc") or "").strip()
        self.cls = e.get("class") or ""
        self.pkg = e.get("package") or ""
        self.rid = e.get("resource-id") or ""
        self.enabled = e.get("enabled") == "true"
        self.clickable = e.get("clickable") == "true"
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", e.get("bounds") or "[0,0][0,0]"))
        self.x, self.y, self.h = (x1 + x2) // 2, (y1 + y2) // 2, y2 - y1

    def says(self, t):
        return self.text == t or self.desc == t


def screen():
    """The nodes on screen now (UI Automator), or [] when the dump fails (the screen was changing)."""
    for _ in range(3):
        adb("shell", "rm", "-f", "/sdcard/ui.xml")
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=40)
        xml = adb("exec-out", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml:
            try:
                return [Node(e) for e in ET.fromstring(xml[xml.index("<hierarchy"):]).iter("node")]
            except ET.ParseError:
                pass
        time.sleep(1)
    return []


def words(nodes):
    return " | ".join(dict.fromkeys(n.text or n.desc for n in nodes if (n.text or n.desc)))[:600]


class Smoke:
    def __init__(self):
        size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
        self.w, self.h = (int(size.group(1)), int(size.group(2))) if size else (1080, 1920)
        self.step = "start"

    def fail(self, why, nodes=None):
        nodes = screen() if nodes is None else nodes
        print(f"::error title=Release smoke: {self.step}::{why}. On screen: {words(nodes) or '(nothing readable)'}")
        crash = adb("logcat", "-d", "-b", "crash", timeout=30).strip()
        if crash:
            print(f"::error title=Release smoke: crash log::{crash[:1500].replace(chr(10), ' / ')}")
        sys.exit(1)

    def alive(self):
        if not adb("shell", "pidof", PKG).strip():
            self.fail("the app is no longer running")

    def find(self, text, nodes, enabled=False):
        hits = [n for n in nodes if n.says(text) and n.pkg == PKG and (n.enabled or not enabled)]
        return hits[0] if hits else None

    def wait(self, text, seconds=30):
        end = time.time() + seconds
        while time.time() < end:
            nodes = screen()
            n = self.find(text, nodes)
            if n:
                return n, nodes
            self.dismiss_system(nodes)
            time.sleep(1)
        self.fail(f"'{text}' did not appear within {seconds} s")

    def dismiss_system(self, nodes):
        """A system dialog (notifications, 'app isn't responding') is answered so the app can go on."""
        for n in nodes:
            if "permissioncontroller" in n.pkg and n.rid.endswith(("permission_deny_button", "permission_deny_and_dont_ask_again_button")):
                self.tap(n)
                return True
            if n.pkg == "android" and n.says("Wait"):
                self.tap(n)
                return True
        return False

    def tap(self, n):
        adb("shell", "input", "tap", str(n.x), str(n.y))
        time.sleep(1.2)

    def scroll(self):
        adb("shell", "input", "swipe", str(self.w // 2), str(int(self.h * 0.72)), str(self.w // 2), str(int(self.h * 0.30)), "350")
        time.sleep(0.8)

    def hide_keyboard(self):
        """Closes the on-screen keyboard (it would take the swipes). Back is pressed only while the keyboard shows, so the step stays."""
        for _ in range(3):
            if "mInputShown=true" not in adb("shell", "dumpsys", "input_method"):
                return
            adb("shell", "input", "keyevent", "4")
            time.sleep(1)

    def scroll_top(self):
        """A new step may open where the last one was scrolled to: back to its top."""
        for _ in range(6):
            adb("shell", "input", "swipe", str(self.w // 2), str(int(self.h * 0.30)), str(self.w // 2), str(int(self.h * 0.80)), "250")
        time.sleep(0.8)

    def wait_any(self, texts, seconds=30):
        """Waits until one of the texts is on screen, scrolling back to the top of the page if needed."""
        end = time.time() + seconds
        tries = 0
        while time.time() < end:
            nodes = screen()
            for t in texts:
                n = self.find(t, nodes)
                if n:
                    return n, nodes
            if not self.dismiss_system(nodes):
                tries += 1
                if tries % 3 == 0:
                    self.scroll_top()
                else:
                    time.sleep(1)
        self.fail(f"none of {texts} appeared within {seconds} s")

    def tap_text(self, text, scrolls=25, enabled=True):
        """Scrolls down until the text is on screen (and enabled), then taps it."""
        for _ in range(scrolls):
            nodes = screen()
            n = self.find(text, nodes, enabled=enabled)
            if n and n.h > 0:
                self.tap(n)
                return
            if not self.dismiss_system(nodes):
                self.scroll()
        self.fail(f"could not find '{text}' to tap")

    def run(self, apk):
        self.step = "install"
        adb("install", "-r", apk, timeout=240, check=True)
        adb("logcat", "-c", timeout=30)
        adb("shell", "am", "start", "-W", "-n", ACTIVITY, timeout=90, check=True)

        self.step = "launch"
        self.wait(S["onb_welcome_title"], 60)
        self.alive()
        print("::notice title=Release smoke::started and showed the welcome screen")

        self.step = "onboarding: welcome"
        self.tap_text(S["onb_welcome_accept"])
        self.wait(S["onb_screen_title"])

        self.step = "onboarding: health questions"
        for _ in range(12):
            nodes = screen()
            if self.find(S["onb_about_title"], nodes) or self.find(S["onb_birth_year"], nodes) or self.find(S["exp_1_3"], nodes):
                break
            for n in [n for n in nodes if n.says(S["action_no"]) and n.pkg == PKG]:
                self.tap(n)
            nodes = screen()
            go = self.find(S["action_continue"], nodes, enabled=True)
            if go:
                self.tap(go)
            else:
                self.scroll()
        self.wait_any([S["onb_about_title"], S["onb_birth_year"], S["exp_1_3"]])

        self.step = "onboarding: about you"
        self.scroll_top()
        field, _ = self.wait_any([S["onb_birth_year"]])
        self.tap(field)
        adb("shell", "input", "text", "1985")
        self.hide_keyboard()
        self.tap_text(S["exp_1_3"], enabled=False)
        self.tap_text(S["action_continue"])
        self.wait_any([S["onb_cond_title"], S["onb_cond_none"]])

        self.step = "onboarding: health conditions"
        self.scroll_top()
        self.tap_text(S["onb_cond_none"], enabled=False)
        self.tap_text(S["action_continue"], scrolls=60)

        self.step = "onboarding: goal to plan"
        # Steps 5 to 10 keep their defaults: Continue on each (scrolling to it) until the alerts step offers "Not now".
        for _ in range(150):
            nodes = screen()
            if self.find(S["onb_alerts_skip"], nodes):
                break
            go = self.find(S["action_continue"], nodes, enabled=True)
            if go:
                self.tap(go)
                self.alive()
            elif not self.dismiss_system(nodes):
                self.scroll()
        else:
            self.fail("the alerts step did not appear")

        self.step = "onboarding: alerts"
        self.tap_text(S["onb_alerts_skip"])

        self.step = "today"
        self.wait(S["today_start"], 60)
        self.alive()
        print("::notice title=Release smoke::onboarding finished on Today")

        self.step = "cold start (NFR-01)"
        times = []
        for _ in range(3):
            adb("shell", "am", "force-stop", PKG)
            time.sleep(2)
            out = adb("shell", "am", "start", "-W", "-n", ACTIVITY, timeout=90)
            m = re.search(r"TotalTime:\s*(\d+)", out)
            if m:
                times.append(int(m.group(1)))
            self.wait(S["today_start"], 60)
        if times:
            print(f"::notice title=Measured::cold start to Today on the emulator (software graphics, R8 release): "
                  f"{sorted(times)[len(times) // 2]} ms median of {len(times)} ({', '.join(map(str, times))} ms); the 1.5 s target is checked on the phone in UAT")

        self.step = "check-in"
        self.tap_text(S["today_start"])
        self.tap_text(S["checkin_submit"], scrolls=15)

        self.step = "session preview"
        end = time.time() + 60
        while time.time() < end:
            nodes = screen()
            if self.find(S["preview_start"], nodes) or self.find(S["preview_rest_day"], nodes):
                break
            self.scroll()
        else:
            self.fail("today's session was not shown")
        self.alive()
        crash = adb("logcat", "-d", "-b", "crash", timeout=30).strip()
        if PKG in crash:
            self.fail("a crash was logged")
        print("::notice title=Release smoke::PASS: installed, onboarded, planned and showed today's session on the R8-shrunk release build")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    Smoke().run(sys.argv[1])
