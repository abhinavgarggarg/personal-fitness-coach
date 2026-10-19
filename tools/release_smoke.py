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


def strings(root=ROOT):
    """The app's English screen texts by name, from the res/values of the checkout at `root` (an older build has its own)."""
    out = {}
    for f in sorted((Path(root) / "app/src/main/res/values").glob("strings*.xml")):
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


# The size of the screen as last seen, in its current orientation (the root node's bounds): swipes and taps stay on it when the
# phone is turned sideways.
SIZE = []


def screen():
    """The nodes on screen now (UI Automator), or [] when the dump fails (the screen was changing)."""
    for _ in range(3):
        adb("shell", "rm", "-f", "/sdcard/ui.xml")
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=40)
        xml = adb("exec-out", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml:
            try:
                root = ET.fromstring(xml[xml.index("<hierarchy"):])
                nodes = [Node(e) for e in root.iter("node")]
                first = root.find("node")
                if first is not None:
                    b = list(map(int, re.findall(r"\d+", first.get("bounds") or "")))
                    if len(b) == 4 and b[2] > 0 and b[3] > 0:
                        SIZE[:] = [b[2], b[3]]
                return nodes
            except ET.ParseError:
                pass
        time.sleep(1)
    return []


def words(nodes):
    return " | ".join(dict.fromkeys(n.text or n.desc for n in nodes if (n.text or n.desc)))[:600]


class Smoke:
    """Drives the app from outside. `pkg`/`activity`: the release build by default; `texts`: the screen texts of the build under test."""

    def __init__(self, pkg=PKG, activity=ACTIVITY, texts=None, title="Release smoke"):
        size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
        self.w, self.h = (int(size.group(1)), int(size.group(2))) if size else (1080, 1920)
        self.step = "start"
        self.pkg, self.activity, self.title = pkg, activity, title
        self.S = texts or S

    def fail(self, why, nodes=None):
        nodes = screen() if nodes is None else nodes
        print(f"::error title={self.title}: {self.step}::{why}. On screen: {words(nodes) or '(nothing readable)'}")
        crash = adb("logcat", "-d", "-b", "crash", timeout=30).strip()
        if crash:
            print(f"::error title={self.title}: crash log::{crash[:1500].replace(chr(10), ' / ')}")
        sys.exit(1)

    def alive(self):
        if not adb("shell", "pidof", self.pkg).strip():
            self.fail("the app is no longer running")

    def find(self, text, nodes, enabled=False):
        hits = [n for n in nodes if n.says(text) and n.pkg == self.pkg and (n.enabled or not enabled)]
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
            # Another app (the launcher, on a slow emulator) not responding: closed, so the app under test goes on.
            if n.says("Close app") and n.pkg != self.pkg and any("isn't responding" in (m.text or "") and "Fitness" not in (m.text or "") for m in nodes):
                self.tap(n)
                return True
        return False

    def tap(self, n):
        adb("shell", "input", "tap", str(n.x), str(n.y))
        time.sleep(1.2)

    def size(self):
        return (SIZE[0], SIZE[1]) if SIZE else (self.w, self.h)

    def scroll(self):
        w, h = self.size()
        adb("shell", "input", "swipe", str(w // 2), str(int(h * 0.72)), str(w // 2), str(int(h * 0.30)), "350")
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
        w, h = self.size()
        for _ in range(6):
            adb("shell", "input", "swipe", str(w // 2), str(int(h * 0.30)), str(w // 2), str(int(h * 0.80)), "250")
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
            # On screen, not under the bottom edge (a node partly scrolled out of view is scrolled in first).
            if n and n.h > 0 and n.y < self.size()[1] - 8:
                self.tap(n)
                return
            if not self.dismiss_system(nodes):
                self.scroll()
        self.fail(f"could not find '{text}' to tap")

    def install(self, apk, replace=True):
        self.step = "install"
        adb("install", *(["-r"] if replace else []), apk, timeout=240, check=True)

    def launch(self):
        adb("logcat", "-c", timeout=30)
        adb("shell", "am", "start", "-W", "-n", self.activity, timeout=90, check=True)

    def onboard(self, birth_year="1985"):
        """From the welcome screen to Today: an adult with one to three years of training, no condition, the defaults elsewhere."""
        S = self.S
        self.step = "launch"
        self.wait(S["onb_welcome_title"], 60)
        self.alive()

        self.step = "onboarding: welcome"
        self.tap_text(S["onb_welcome_accept"])
        self.wait(S["onb_screen_title"])

        self.step = "onboarding: health questions"
        for _ in range(12):
            nodes = screen()
            if self.find(S["onb_about_title"], nodes) or self.find(S["onb_birth_year"], nodes) or self.find(S["exp_1_3"], nodes):
                break
            for n in [n for n in nodes if n.says(S["action_no"]) and n.pkg == self.pkg]:
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
        adb("shell", "input", "text", birth_year)
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
        # On a small screen the Start button may be below the fold: the tab bar says Today is open.
        self.wait_any([S["today_start"], S["tab_history"]], 60)
        self.alive()

    def to_preview(self):
        """Today → check-in → today's session (or a rest day)."""
        S = self.S
        self.step = "check-in"
        self.tap_text(S["today_start"])
        self.tap_text(S["checkin_submit"], scrolls=15)

        self.step = "session preview"
        end = time.time() + 60
        while time.time() < end:
            nodes = screen()
            if self.find(S["preview_start"], nodes) or self.find(S["preview_rest_day"], nodes):
                return
            self.scroll()
        self.fail("today's session was not shown")

    def run(self, apk):
        S = self.S
        self.install(apk)
        self.launch()
        self.onboard()
        print("::notice title=Release smoke::started, showed the welcome screen and finished onboarding on Today")

        self.step = "cold start (NFR-01)"
        times = []
        for _ in range(3):
            adb("shell", "am", "force-stop", self.pkg)
            time.sleep(2)
            out = adb("shell", "am", "start", "-W", "-n", self.activity, timeout=90)
            m = re.search(r"TotalTime:\s*(\d+)", out)
            if m:
                times.append(int(m.group(1)))
            self.wait(S["today_start"], 60)
        if times:
            print(f"::notice title=Measured::cold start to Today on the emulator (software graphics, R8 release): "
                  f"{sorted(times)[len(times) // 2]} ms median of {len(times)} ({', '.join(map(str, times))} ms); the 1.5 s target is checked on the phone in UAT")

        self.to_preview()
        self.alive()
        crash = adb("logcat", "-d", "-b", "crash", timeout=30).strip()
        if self.pkg in crash:
            self.fail("a crash was logged")
        print("::notice title=Release smoke::PASS: installed, onboarded, planned and showed today's session on the R8-shrunk release build")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    Smoke().run(sys.argv[1])
