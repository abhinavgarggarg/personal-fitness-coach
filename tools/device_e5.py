#!/usr/bin/env python3
"""Phase 4 Part 3 (E5): interruptions, data integrity, Android backup and an upgrade over older data, on an emulator.

The debug build is driven from outside with adb and UI Automator, like the release smoke test, so the app is killed, rebooted and
reinstalled for real. Its own database, read with run-as after every step, is the check for "zero sets lost" (NFR-06): every set
logged is in it at once, unchanged afterwards, and the saved resume point (stage, exercise, next weights, rest) is the same after each
interruption. A crash or "not responding" of the app anywhere fails the run.

    python3 -I tools/device_e5.py all <debug.apk> <older-debug.apk> <older-checkout>
    python3 -I tools/device_e5.py upgrade <older-debug.apk> <older-checkout> <debug.apk>
    python3 -I tools/device_e5.py interruptions <debug.apk>
    python3 -I tools/device_e5.py backup <debug.apk>          (uses the data `interruptions` left)
    python3 -I tools/device_e5.py quick-backup <debug.apk>    (a short session, then the backup checks)

Each check is printed; the list goes into one GitHub notice at the end (GitHub keeps 10 a step). On failure an error annotation names
the step and the words on screen, and the exit code is 1.
"""
import io
import json
import sqlite3
import subprocess
import sys
import tarfile
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from release_smoke import Smoke, adb, screen, strings  # noqa: E402  (the tools folder, added above; run with -I)

PKG = "com.personalfitnesscoach.dev"
ACTIVITY = f"{PKG}/com.personalfitnesscoach.app.MainActivity"
TRANSPORT = "com.android.localtransport/.LocalTransport"
APP_LABEL = "Fitness Coach"
# The saved resume point that an interruption must not change (ActiveState; times of the last tap and the idle allowance may).
RESUME_KEYS = ("stage", "currentRowId", "nextLoads", "calibrationDone", "rampSkipped", "restStartedAtMs", "restSec", "conditioningDone",
               "lastHardEffortAtMs")


def api_level():
    try:
        return int(adb("shell", "getprop", "ro.build.version.sdk").strip())
    except ValueError:
        return 0


class Device(Smoke):
    def __init__(self, texts=None, title="E5"):
        super().__init__(PKG, ACTIVITY, texts, title)
        self.passed = []
        self.notes = []

    def ok(self, what):
        self.passed.append(what)
        print(f"{self.title}: {self.step}: {what}")

    def fail(self, why, nodes=None):
        if self.passed:
            print(f"::notice title={self.title}: passed before the failure ({len(self.passed)})::" + " / ".join(self.passed))
        super().fail(why, nodes)

    # ------------------------------------------------------------------------------------------------ health: crashes, "not responding"
    def launch(self):
        # Unlike the smoke test, the log is never cleared: a crash anywhere in the run must stay visible to health().
        adb("shell", "am", "start", "-W", "-n", self.activity, timeout=90, check=True)

    def health(self, where):
        crash = adb("logcat", "-d", "-b", "crash", timeout=30)
        if self.pkg in crash:
            self.fail(f"{where}: the app crashed: {' / '.join(ln for ln in crash.splitlines() if ln.strip())[-1200:]}")
        anr = [ln for ln in adb("logcat", "-d", "-b", "events", timeout=30).splitlines() if "am_anr" in ln and self.pkg in ln]
        if anr:
            self.fail(f"{where}: the app stopped responding: {anr[-1][:300]}")

    def dismiss_system(self, nodes):
        # The app's own "isn't responding" dialog is a failure, never waited out.
        if any("isn't responding" in (n.text or "") and APP_LABEL in (n.text or "") for n in nodes):
            self.fail("the app stopped responding")
        return super().dismiss_system(nodes)

    def pid(self):
        return adb("shell", "pidof", self.pkg).strip()

    # ------------------------------------------------------------------------------------------------ the database, read from outside
    def _read(self):
        with tempfile.TemporaryDirectory() as d:
            for suffix in ("-wal", "-shm", ""):
                r = subprocess.run(["adb", "exec-out", "run-as", self.pkg, "cat", f"databases/pfc.db{suffix}"], capture_output=True, timeout=60)
                if r.returncode == 0 and r.stdout and not r.stdout.startswith((b"run-as:", b"cat:")):
                    (Path(d) / f"pfc.db{suffix}").write_bytes(r.stdout)
            db = Path(d) / "pfc.db"
            if not db.exists() or not db.read_bytes().startswith(b"SQLite format 3"):
                return None
            con = sqlite3.connect(str(db))
            try:
                rows = lambda sql: [tuple(r) for r in con.execute(sql).fetchall()]  # noqa: E731
                active = rows("SELECT json FROM active_session")
                resume = None
                if active:
                    try:
                        j = json.loads(active[0][0])
                        resume = {k: j.get(k) for k in RESUME_KEYS}
                    except ValueError:
                        resume = {"unreadable": active[0][0][:80]}
                return {
                    "sets": rows("SELECT id, workout_exercise_id, set_index, kind, load_kg, reps, seconds, metres, rir, rpe, form_check, deviation "
                                 "FROM logged_set ORDER BY id"),
                    "exercises": rows("SELECT id, workout_id, position, exercise_id, status FROM workout_exercise ORDER BY id"),
                    "workouts": rows("SELECT id, day, status, template FROM workout ORDER BY id"),
                    "tables": {t: rows(f"SELECT * FROM {t} ORDER BY 1") for t in ("logged_set", "workout", "workout_exercise")},
                    "open": len(active),
                    "resume": resume,
                }
            finally:
                con.close()

    def snap(self):
        """What is saved now. Two equal reads in a row (the app may be writing)."""
        last = None
        for _ in range(8):
            c = self._read()
            if c is not None and c == last:
                return c
            last = c
            time.sleep(1)
        return last or {"sets": [], "exercises": [], "workouts": [], "tables": {}, "open": 0, "resume": None}

    @staticmethod
    def done(s):
        return sum(1 for w in s["workouts"] if w[2] == "DONE")

    def expect_same(self, what, before):
        """Every set saved, unchanged; the session still open at the same place (stage, exercise, next weights, rest)."""
        after = self.snap()
        if after["sets"] != before["sets"]:
            lost = [r for r in before["sets"] if r not in after["sets"]]
            new = [r for r in after["sets"] if r not in before["sets"]]
            self.fail(f"{what}: the sets changed: {len(before['sets'])} before, {len(after['sets'])} after; missing or changed {lost[:3]}, new {new[:3]}")
        if after["open"] != 1:
            self.fail(f"{what}: {after['open']} open sessions after it")
        if after["resume"] != before["resume"]:
            self.fail(f"{what}: the resume point moved: {before['resume']} → {after['resume']}")
        if after["exercises"] != before["exercises"]:
            self.fail(f"{what}: the exercises changed")
        self.health(what)
        r = after["resume"] or {}
        self.ok(f"{what}: all {len(after['sets'])} sets saved unchanged, resume point the same (stage {r.get('stage')}, exercise row "
                f"{r.get('currentRowId')}, rest {'on' if r.get('restStartedAtMs') else 'off'})")
        return after

    # ------------------------------------------------------------------------------------------------ the workout screen
    def markers(self):
        S = self.S
        return [S["wk_end"], S["wk_rest_skip"], S["wk_stage_next"], S["wk_done_as_planned"], S["wk_effort_5plus"], S["wk_finish"]]

    def on_workout(self, nodes):
        return any(self.find(t, nodes) for t in self.markers())

    def start_workout(self):
        S = self.S
        self.to_preview()
        self.step = "start the session"
        if self.find(S["preview_rest_day"], screen()):
            self.fail("today is a rest day: no session to interrupt")
        self.tap_text(S["preview_start"])
        self.back_in("the session started", resume=False)

    def set_on_screen(self, nodes):
        S = self.S
        return self.find(S["wk_effort_5plus"], nodes) or self.find(S["wk_done_as_planned"], nodes) or self.find(S["wk_effort_q"], nodes) or \
            (S.get("wk_effort_5_9") and self.find(S["wk_effort_5_9"], nodes))

    def to_next_set(self):
        """Past warm-up stages and rests to the next set of a lift; returns the screen."""
        S = self.S
        tapped = None
        for _ in range(60):
            nodes = screen()
            if self.set_on_screen(nodes):
                return nodes
            confirm = self.find(S["addset_confirm_go"], nodes, enabled=True)
            if confirm:
                self.tap(confirm)
                continue
            # Rests first; then a stage before the lifts; once every exercise is done, one more set ("Add a set") keeps the session
            # going for the next interruption.
            done_lift = self.find(S["wk_add_set"], nodes, enabled=True)
            order = (S["wk_rest_skip"], S["wk_rest_over"]) + ((S["wk_add_set"],) if done_lift else (S["wk_stage_next"],))
            for t in order:
                n = self.find(t, nodes, enabled=True)
                # The same button again right after tapping it: it is out of reach, so scroll to it first (a short, sideways screen).
                if n and (t, n.y) != tapped and n.y < self.size()[1] - 8:
                    self.tap(n)
                    tapped = (t, n.y)
                    break
            else:
                tapped = None
                if not self.dismiss_system(nodes):
                    self.scroll()
        self.fail("no set to log appeared")

    def find_prefix(self, text, nodes):
        """A node whose text starts with `text` (a screen text with a number in it, such as "Yes, all 10")."""
        hits = [n for n in nodes if n.pkg == self.pkg and (n.text or n.desc).startswith(text)]
        return hits[0] if hits else None

    def log_button(self, effort):
        """The button that logs the set on screen, scrolled into view: the effort answer on a weight-finding set ("5+", or the lowest
        range before a big step, D-116, so the ramp ends safely), else "Done as planned"."""
        S = self.S
        low = S.get("wk_effort_5_9")
        for _ in range(12):
            nodes = self.to_next_set()
            for t in ((effort,) if self.find(S["wk_effort_5plus"], nodes) else ()) + ((low,) if low else ()) + (S["wk_done_as_planned"],):
                n = self.find(t, nodes, enabled=True)
                if n and n.h > 0 and n.y < self.size()[1] - 8:
                    return n
            self.scroll()
        self.fail("no button to log the set")

    def answer_all_reps(self):
        yes = self.S.get("wk_cal_all_yes")
        if yes:
            n = self.find_prefix(yes.split("%")[0], screen())
            if n:
                self.tap(n)

    def log_set(self, effort="5+"):
        """Logs one set: exactly one new row, complete, in the database at once (NFR-06), and every earlier row unchanged."""
        before = self.snap()
        self.tap(self.log_button(effort))
        self.answer_all_reps()
        end = time.time() + 15
        while time.time() < end:
            now = self.snap()
            n0, n1 = len(before["sets"]), len(now["sets"])
            if n1 > n0 + 1:
                self.fail(f"one tap saved {n1 - n0} sets")
            if n1 == n0 + 1:
                if now["sets"][:n0] != before["sets"]:
                    self.fail("logging a set changed the sets before it")
                new = now["sets"][-1]
                if new[5] is None and new[6] is None or (new[3] in ("CALIBRATION", "WORKING") and new[8] is None and new[9] is None and new[4] is not None):
                    self.fail(f"the set saved is incomplete: {new}")
                return now
            time.sleep(1)
        self.fail(f"the set logged was not saved within 15 s ({len(before['sets'])} sets before and after)")

    def back_in(self, why, resume):
        """The app open again on the workout. `resume`: the app started afresh, so "Continue where you stopped?" must be offered;
        otherwise it must not be (the screen was kept)."""
        S = self.S
        end = time.time() + 90
        prompted = False
        while time.time() < end:
            nodes = screen()
            if self.find(S["resume_title"], nodes):
                prompted = True
                self.tap_text(S["resume_continue"], scrolls=5)
                continue
            if self.on_workout(nodes):
                if resume and not prompted:
                    self.fail(f"{why}: the app started afresh but did not offer to continue where it stopped")
                if prompted and not resume:
                    self.fail(f"{why}: the app asked to continue although it was still open")
                if prompted:
                    self.ok(f"{why}: offered to continue where it stopped")
                return nodes
            if not self.dismiss_system(nodes):
                if not self.in_front():
                    self.relaunch()
                time.sleep(1)
        self.fail(f"{why}: the workout did not come back")

    def rest_alarm(self, why):
        """During a rest, the end-of-rest alert is set again after the app is reopened (it is cancelled with the app)."""
        nodes = screen()
        if self.find(self.S["wk_rest_skip"], nodes) and not self.find(self.S["wk_rest_over"], nodes):
            alarms = adb("shell", "dumpsys", "alarm", timeout=60)
            if self.pkg not in alarms:
                self.fail(f"{why}: a rest is running but its alert is not set")
            self.ok(f"{why}: the rest's alert is set again")

    def finish(self):
        S = self.S
        self.step = "finish"
        # "End" sits at the top of the workout screen: back to the top first (a small screen scrolls it away).
        self.scroll_top()
        self.tap_text(S["wk_end"], scrolls=3)
        self.tap_text(S["sheet_end_finish"], scrolls=5)
        self.wait(S["done_title"], 60)
        self.tap_text(S["done_back"], scrolls=15)
        self.wait(S["tab_history"], 60)

    # ------------------------------------------------------------------------------------------------ device actions
    def home(self):
        adb("shell", "input", "keyevent", "3")
        time.sleep(2)

    def relaunch(self):
        """As the launcher icon does: an open task comes to the front as it was, a stopped app starts afresh."""
        adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", self.activity, timeout=60)
        time.sleep(2)

    def focus(self):
        return " ".join(ln.strip() for ln in adb("shell", "dumpsys", "window").splitlines() if "mCurrentFocus" in ln or "mFocusedApp" in ln)

    def in_front(self):
        return self.pkg in self.focus()

    def unlock(self):
        adb("shell", "input", "keyevent", "224")
        time.sleep(1)
        adb("shell", "wm", "dismiss-keyguard")
        adb("shell", "input", "keyevent", "82")
        time.sleep(2)

    def wait_boot(self):
        subprocess.run(["adb", "wait-for-device"], timeout=600)
        end = time.time() + 600
        while time.time() < end:
            if adb("shell", "getprop", "sys.boot_completed", timeout=30).strip() == "1":
                return
            time.sleep(3)
        self.fail("the emulator did not finish booting within 10 minutes")

    def reboot(self):
        adb("reboot", timeout=60)
        time.sleep(10)
        self.wait_boot()
        time.sleep(15)
        self.unlock()

    def kept(self, what, pid, before):
        """A warm interruption: the same process, and everything as it was."""
        if self.pid() != pid:
            self.fail(f"{what}: the app's process was restarted ({pid} → {self.pid() or 'none'})")
        return self.expect_same(what, before)

    # ------------------------------------------------------------------------------------------------ the scenarios
    def interruptions(self, apk):
        """A session logged set by set through every interruption Phase 2 lists (call, background, lock, process death, reboot)."""
        S = self.S
        self.step = "install"
        adb("uninstall", self.pkg, timeout=120)
        self.install(apk, replace=False)
        self.launch()
        self.onboard()
        self.start_workout()

        self.step = "logging"
        self.log_set()
        s = self.log_set()
        self.ok(f"each set is saved the moment it is logged, complete, the earlier ones unchanged ({len(s['sets'])} so far)")

        self.step = "home and back"
        pid = self.pid()
        self.home()
        self.relaunch()
        self.back_in("home and back", resume=False)
        s = self.kept("home and back", pid, s)

        self.step = "screen off and on"
        pid = self.pid()
        adb("shell", "input", "keyevent", "26")
        time.sleep(5)
        self.unlock()
        self.relaunch()
        self.back_in("screen off and on", resume=False)
        s = self.kept("screen off and on", pid, s)
        s = self.log_set()

        self.step = "rotation"
        pid = self.pid()
        adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        adb("shell", "settings", "put", "system", "user_rotation", "1")
        time.sleep(4)
        self.back_in("turned sideways", resume=False)
        s = self.kept("turned sideways", pid, s)
        s = self.log_set()
        adb("shell", "settings", "put", "system", "user_rotation", "0")
        time.sleep(4)
        self.back_in("turned back", resume=False)
        s = self.kept("turned back", pid, s)

        self.step = "phone call"
        pid = self.pid()
        number = "5551234567"
        rang = adb("emu", "gsm", "call", number, timeout=30)
        if "OK" not in rang:
            self.fail(f"the emulator could not ring: {rang.strip()[:200]}")
        time.sleep(5)
        # Answered as a person would: "Answer" on the call alert (Android 10+ shows a banner over an open app), else from the console.
        alert = next((n for n in screen() if (n.text or n.desc).strip().lower() == "answer"), None)
        if alert:
            self.tap(alert)
            how = "answered from the call alert"
        else:
            if "OK" not in adb("emu", "gsm", "accept", number, timeout=30):
                self.fail("the call could not be answered")
            how = "answered"
        time.sleep(4)
        if self.in_front():
            # The call screen opened, as when the person goes to it: the phone app comes in front of this one.
            adb("shell", "am", "start", "-a", "android.intent.action.DIAL", timeout=30)
            how += ", the phone app in front"
            time.sleep(3)
        on_call = self.focus()
        if self.pkg in on_call:
            self.fail(f"the call did not take over the screen (focus: {on_call[:200]})")
        self.notes.append(f"call: {how}")
        time.sleep(5)
        hung = adb("emu", "gsm", "cancel", number, timeout=30)
        if "OK" not in hung:
            self.fail(f"the call could not be ended: {hung.strip()[:200]}")
        time.sleep(3)
        self.relaunch()
        self.back_in("an answered call", resume=False)
        s = self.kept("an answered call", pid, s)
        s = self.log_set()

        self.step = "low memory"
        pid = self.pid()
        adb("shell", "am", "send-trim-memory", self.pkg, "RUNNING_CRITICAL")
        time.sleep(2)
        self.home()
        adb("shell", "am", "send-trim-memory", self.pkg, "COMPLETE")
        time.sleep(2)
        self.relaunch()
        self.back_in("asked to free memory", resume=False)
        s = self.kept("asked to free memory", pid, s)

        self.step = "process death in the background"
        self.home()
        adb("shell", "am", "kill", self.pkg)
        time.sleep(3)
        how = "closed by Android"
        if self.pid():
            adb("shell", "am", "force-stop", self.pkg)
            how = "stopped (Android did not close it)"
            time.sleep(2)
        self.relaunch()
        self.back_in(f"in the background, {how}", resume=True)
        s = self.expect_same(f"in the background, {how}", s)
        self.rest_alarm("in the background")
        s = self.log_set()

        self.step = "the app stopped on screen"
        adb("shell", "am", "force-stop", self.pkg)
        time.sleep(3)
        self.relaunch()
        self.back_in("the app stopped on screen", resume=True)
        s = self.expect_same("the app stopped on screen", s)
        self.rest_alarm("stopped on screen")

        self.step = "the app stopped while a set is saved"
        outcomes = []
        for delay in ("0", "0.05", "0.15", "0.3"):
            # The button first (it may skip a rest, which changes the session), then what is saved, then the tap and the stop.
            n = self.log_button("5+")
            before = self.snap()
            # One shell command, so the stop follows the tap by the delay only.
            adb("shell", f"input tap {n.x} {n.y}; sleep {delay}; am force-stop {self.pkg}", timeout=60)
            time.sleep(3)
            self.relaunch()
            self.back_in(f"stopped {delay} s after a tap", resume=True)
            after = self.snap()
            k0, k1 = len(before["sets"]), len(after["sets"])
            if k1 == k0:
                # Not saved: nothing changed, the same set comes back.
                if after["sets"] != before["sets"] or after["resume"] != before["resume"]:
                    self.fail(f"stopped {delay} s after a tap: no set saved but the session changed")
                outcomes.append("not saved")
            elif k1 == k0 + 1 and after["sets"][:k0] == before["sets"]:
                # Saved: the set and the session's next step together (one transaction).
                if after["resume"] == before["resume"]:
                    self.fail(f"stopped {delay} s after a tap: the set was saved but the session did not move on with it")
                outcomes.append("saved")
            else:
                self.fail(f"stopped {delay} s after a tap: {k0} sets before, {k1} after")
            self.health(f"stopped {delay} s after a tap")
            self.answer_all_reps()
        self.ok(f"stopped right after a tap, 4 times: each set saved whole with the session's next step, or not at all ({', '.join(outcomes)})")
        s = self.log_set()

        self.step = "reboot"
        self.reboot()
        self.relaunch()
        self.back_in("a restart of the phone", resume=True)
        s = self.expect_same("a restart of the phone", s)
        self.rest_alarm("after a restart")
        s = self.log_set()

        self.finish()
        after = self.snap()
        if after["sets"] != s["sets"] or after["open"] != 0 or self.done(after) != 1:
            self.fail(f"after finishing: {len(after['sets'])} sets (expected {len(s['sets'])}), {after['open']} open sessions, "
                      f"{self.done(after)} finished workouts")
        self.health("finish")
        self.ok(f"finished: all {len(after['sets'])} sets kept unchanged, the session closed")
        self.tap_text(S["tab_history"], scrolls=2)
        # Finished from "End" before the cool-down: its row starts "Ended early · …" (or "Done · …" had everything been done).
        end = time.time() + 30
        while time.time() < end:
            nodes = screen()
            row = self.find_prefix(S["hist_outcome_partial"], nodes) or self.find_prefix(S["hist_outcome_done"] + " ", nodes)
            if row:
                self.ok(f"History shows the session: {row.text or row.desc}")
                break
            time.sleep(1)
        else:
            self.fail("the session is not in History")

    # ------------------------------------------------------------------------------------------------ Android backup
    def stored(self):
        """The names in the app's backup as the local test transport keeps it (read as root)."""
        names = []
        # Android 10 keeps it in /data/cache/backup; from Android 12 the test transport keeps it in its own files.
        files = adb("shell", f"find /data/cache/backup /data/data/com.android.localtransport -path '*{self.pkg}*' -type f 2>/dev/null",
                    timeout=60).split()
        for f in files:
            data = subprocess.run(["adb", "exec-out", "cat", f], capture_output=True, timeout=120).stdout
            try:
                with tarfile.open(fileobj=io.BytesIO(data)) as t:
                    names += t.getnames()
            except tarfile.TarError:
                names.append(f)  # a key-value record: its file name
        return names

    def backup_now(self, params):
        adb("shell", "settings", "put", "secure", "backup_local_transport_parameters", params)
        time.sleep(2)
        adb("shell", "bmgr", "wipe", TRANSPORT, self.pkg)
        # Not force-stopped: Android backs up no app in the stopped state ("Backup is not allowed").
        self.home()
        out = " ".join(adb("shell", "bmgr", "backupnow", self.pkg, timeout=240).split())
        result = out.split(f"Package {self.pkg} with result:")[-1].split("Package")[0].strip() if self.pkg in out else out[:120]
        return result, self.stored()

    def backup(self, apk):
        """Android backup (Phase 2 section 16): the database and settings only in an end-to-end encrypted backup (and a
        device-to-device transfer), and back after a reinstall. Read as root from the local test transport."""
        S = self.S
        self.step = "Android backup"
        before = self.snap()
        if not before["sets"]:
            self.fail("no data to back up")
        api = api_level()
        rooted = " ".join(adb("root", timeout=60).split())
        self.wait_boot()
        time.sleep(3)
        self.where = f"adb root: {rooted[:80]}; shell: {' '.join(adb('shell', 'id').split())[:60]}"
        adb("shell", "bmgr", "enable", "true")
        adb("shell", "bmgr", "transport", TRANSPORT)
        has_db = lambda names: any(n.endswith("db/pfc.db") for n in names)  # noqa: E731

        self.step = "no backup without encryption"
        result, names = self.backup_now("fake_encryption_flag=false,is_encrypted=false,is_device_transfer=false")
        if has_db(names):
            self.fail(f"the database went into a backup that is not end-to-end encrypted ({result}; {names[:5]})")
        self.ok(f"not end-to-end encrypted: the database stays out of the backup (Android says \"{result}\"; {len(names)} files kept)")

        if api < 31:
            # Before Android 12 the test transport has no real encryption flag (only a test flag the app's rules don't accept), so the
            # encrypted backup can't be made here; the rules are checked by tools/check_backup_rules.py and on the phone in UAT.
            self.notes.append(f"Android {api}: an encrypted backup can't be simulated (no real encryption flag in the test transport)")
            self.ok("the encrypted backup is checked on Android 12 and later, and on the phone in UAT")
            return

        self.step = "device-to-device transfer"
        result, names = self.backup_now("fake_encryption_flag=false,is_encrypted=false,is_device_transfer=true")
        if not has_db(names):
            listing = " ".join(adb("shell", "ls -lR /data/cache/backup /data/data/com.android.localtransport 2>&1 | head -60", timeout=60).split())[:900]
            self.fail(f"a device-to-device transfer left the database behind ({result}; {names[:5]}; {self.where}; {listing})")
        self.ok(f"a device-to-device transfer takes the database ({result})")

        self.step = "Android backup"
        result, names = self.backup_now("fake_encryption_flag=false,is_encrypted=true,is_device_transfer=false")
        if result != "Success" or not has_db(names):
            self.fail(f"the end-to-end encrypted backup did not take the database ({result}; {names[:5]})")
        sets = adb("shell", "bmgr", "list", "sets")
        adb("uninstall", self.pkg, timeout=120)
        self.install(apk, replace=False)
        path = "at install"
        if self._read() is None:
            token = next((ln.split()[0] for ln in sets.splitlines() if ln.strip() and ln.split()[0].isalnum()), None)
            if token:
                adb("shell", "bmgr", "restore", token, self.pkg, timeout=240)
                path = "with bmgr restore"
                time.sleep(5)
        self.launch()
        end = time.time() + 60
        while time.time() < end and not self.find(S["tab_history"], screen()):
            time.sleep(2)
        if not self.find(S["tab_history"], screen()):
            self.fail(f"the data did not come back from the encrypted backup ({path})")
        after = self.snap()
        if after["tables"] != before["tables"]:
            diff = [t for t in before["tables"] if before["tables"][t] != after["tables"].get(t)]
            self.fail(f"the restored data differs from what was backed up (tables {diff})")
        self.health("restore")
        self.ok(f"an end-to-end encrypted backup brought back every set, exercise and session, identical, after a reinstall (restored {path})")

    def upgrade(self, old_apk, old_root, apk):
        """An open session and its sets, saved by the Phase 3 build the Product Owner could have installed, carried into this version
        (the same database schema, version 1; the records inside it are read by the new code)."""
        old = Device(strings(old_root), title="E5 upgrade")
        old.step = "install the older version"
        adb("uninstall", PKG, timeout=120)
        old.install(old_apk, replace=False)
        old.launch()
        old.onboard()
        old.start_workout()
        old.step = "logging on the older version"
        old.log_set()
        old.log_set()
        s = old.log_set()
        adb("shell", "am", "force-stop", PKG)
        time.sleep(2)

        self.step = "upgrade"
        self.install(apk, replace=True)
        self.relaunch()
        self.back_in("after the upgrade", resume=True)
        s = self.expect_same("after the upgrade, the older version's open session", s)
        s = self.log_set()
        self.finish()
        after = self.snap()
        if after["sets"] != s["sets"] or self.done(after) != 1 or after["open"] != 0:
            self.fail(f"after the upgrade and finishing: {len(after['sets'])} sets, {self.done(after)} finished, {after['open']} open")
        self.ok(f"the session begun on the older version finished on this one with all {len(after['sets'])} sets")


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    adb("logcat", "-c", timeout=30)
    d = Device(strings())
    what = argv[1]
    if what == "all":
        _, _, apk, old_apk, old_root = argv
        d.upgrade(old_apk, old_root, apk)
        d.interruptions(apk)
        d.backup(apk)
    elif what == "upgrade":
        d.upgrade(argv[2], argv[3], argv[4])
    elif what == "interruptions":
        d.interruptions(argv[2])
    elif what == "backup":
        d.backup(argv[2])
    elif what == "quick-backup":
        # A short session, then the backup checks (for working on the backup step alone).
        d.step = "install"
        adb("uninstall", PKG, timeout=120)
        d.install(argv[2], replace=False)
        d.launch()
        d.onboard()
        d.start_workout()
        d.log_set()
        d.log_set()
        d.finish()
        d.backup(argv[2])
    else:
        print(__doc__)
        return 2
    d.health("the end of the run")
    print(f"::notice title=E5::PASS on Android {api_level()}: {len(d.passed)} checks: " + " / ".join(d.passed) +
          ("" if not d.notes else " — notes: " + " / ".join(d.notes)))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv))
    except SystemExit:
        raise
    except Exception as e:  # an adb call that timed out or failed: reported like a failed step
        import traceback
        print(f"::error title=E5: unexpected error::{type(e).__name__}: {str(e)[:400]} / {' / '.join(traceback.format_exc().splitlines()[-6:])[:800]}")
        sys.exit(1)
