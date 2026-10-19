#!/usr/bin/env python3
"""Phase 4 Part 3 (E5): interruptions, data integrity, Android backup and an upgrade over older data, on an emulator.

The debug build is driven from outside with adb and UI Automator, like the release smoke test, so the app is killed, rebooted and
reinstalled for real. Its own database, read with run-as after every step, is the check for "zero sets lost" (NFR-06): every set
logged is in it at once, and stays there through each interruption.

    python3 -I tools/device_e5.py all <debug.apk> <older-debug.apk> <older-checkout>
    python3 -I tools/device_e5.py upgrade <older-debug.apk> <older-checkout> <debug.apk>
    python3 -I tools/device_e5.py interruptions <debug.apk>
    python3 -I tools/device_e5.py backup <debug.apk>          (uses the data `interruptions` left)

Each check prints a GitHub annotation; on failure the annotation names the step and the words on screen, and the exit code is 1.
"""
import sqlite3
import subprocess
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from release_smoke import Smoke, adb, screen, strings  # noqa: E402  (the tools folder, added above; run with -I)

PKG = "com.personalfitnesscoach.dev"
ACTIVITY = f"{PKG}/com.personalfitnesscoach.app.MainActivity"
TRANSPORT = "com.android.localtransport/.LocalTransport"


class Device(Smoke):
    def __init__(self, texts=None, title="E5"):
        super().__init__(PKG, ACTIVITY, texts, title)
        self.passed = []

    def ok(self, what):
        self.passed.append(what)
        print(f"::notice title={self.title}: {self.step}::{what}")

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
                q = lambda sql: con.execute(sql).fetchone()[0]  # noqa: E731
                return {
                    "sets": q("SELECT COUNT(*) FROM logged_set"),
                    "open": q("SELECT COUNT(*) FROM active_session"),
                    "done": q("SELECT COUNT(*) FROM workout WHERE status = 'DONE'"),
                    "workouts": q("SELECT COUNT(*) FROM workout"),
                }
            finally:
                con.close()

    def counts(self):
        """The rows saved now: logged sets, open sessions, finished and all workouts. Two equal reads in a row (the app may be writing)."""
        last = None
        for _ in range(6):
            c = self._read()
            if c is not None and c == last:
                return c
            last = c
            time.sleep(1)
        return last or {"sets": 0, "open": 0, "done": 0, "workouts": 0}

    def expect(self, what, sets, open_=1):
        c = self.counts()
        if c["sets"] != sets or c["open"] != open_:
            self.fail(f"{what}: expected {sets} sets saved and {open_} open session, found {c['sets']} and {c['open']}")
        self.ok(f"{what}: all {sets} sets saved, the session still open")

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

    def to_next_set(self):
        """Past warm-up stages and rests to the next set of a lift; returns the screen."""
        S = self.S
        for _ in range(60):
            nodes = screen()
            if self.find(S["wk_effort_5plus"], nodes) or self.find(S["wk_done_as_planned"], nodes):
                return nodes
            for t in (S["wk_rest_skip"], S["wk_rest_over"], S["wk_stage_next"]):
                n = self.find(t, nodes, enabled=True)
                if n:
                    self.tap(n)
                    break
            else:
                if not self.dismiss_system(nodes):
                    self.scroll()
        self.fail("no set to log appeared")

    def tap_set(self, effort):
        """One tap that logs the set on screen: the effort answer on a weight-finding set, else "Done as planned"."""
        S = self.S
        nodes = self.to_next_set()
        if self.find(S["wk_effort_5plus"], nodes):
            self.tap_text(effort, scrolls=10)
        else:
            self.tap_text(S["wk_done_as_planned"], scrolls=10)

    def log_set(self, effort="5+"):
        """Logs one set and checks it is in the database at once (NFR-06). Returns the number of sets saved."""
        before = self.counts()["sets"]
        self.tap_set(effort)
        end = time.time() + 15
        while time.time() < end:
            c = self.counts()["sets"]
            if c == before + 1:
                return c
            if c > before + 1:
                self.fail(f"one tap saved {c - before} sets")
            time.sleep(1)
        self.fail(f"the set logged was not saved within 15 s ({before} sets before and after)")

    def back_in(self, why, resume=True):
        """The app open again on the workout: through "Continue where you stopped?" when it started afresh."""
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
                if prompted:
                    self.ok(f"{why}: offered to continue where it stopped")
                return nodes
            if not self.dismiss_system(nodes):
                if not self.in_front():
                    self.relaunch()
                time.sleep(1)
        self.fail(f"{why}: the workout did not come back")

    def finish(self):
        S = self.S
        self.step = "finish"
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

    def in_front(self):
        return any(self.pkg in ln for ln in adb("shell", "dumpsys", "window").splitlines() if "mCurrentFocus" in ln or "mFocusedApp" in ln)

    def unlock(self):
        adb("shell", "input", "keyevent", "224")
        time.sleep(1)
        adb("shell", "wm", "dismiss-keyguard")
        adb("shell", "input", "keyevent", "82")
        time.sleep(2)

    def reboot(self):
        adb("reboot", timeout=60)
        time.sleep(10)
        subprocess.run(["adb", "wait-for-device"], timeout=600)
        end = time.time() + 600
        while time.time() < end:
            if adb("shell", "getprop", "sys.boot_completed", timeout=30).strip() == "1":
                break
            time.sleep(3)
        else:
            self.fail("the emulator did not finish booting within 10 minutes")
        time.sleep(15)
        self.unlock()

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
        n = self.log_set()
        n = self.log_set()
        self.ok(f"each set is saved the moment it is logged ({n} so far)")

        self.step = "home and back"
        self.home()
        self.relaunch()
        self.back_in("home and back", resume=False)
        self.expect("home and back", n)

        self.step = "screen off and on"
        adb("shell", "input", "keyevent", "26")
        time.sleep(5)
        self.unlock()
        self.relaunch()
        self.back_in("screen off and on", resume=False)
        self.expect("screen off and on", n)
        n = self.log_set()

        self.step = "rotation"
        adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        adb("shell", "settings", "put", "system", "user_rotation", "1")
        time.sleep(4)
        self.back_in("turned sideways", resume=False)
        self.expect("turned sideways", n)
        n = self.log_set()
        adb("shell", "settings", "put", "system", "user_rotation", "0")
        time.sleep(4)
        self.back_in("turned back", resume=False)
        self.expect("turned back", n)

        self.step = "phone call"
        call = adb("emu", "gsm", "call", "5551234567", timeout=30)
        time.sleep(8)
        adb("emu", "gsm", "cancel", "5551234567", timeout=30)
        time.sleep(3)
        self.relaunch()
        self.back_in("an incoming call", resume=False)
        self.expect("an incoming call" + ("" if "OK" in call else " (the emulator console may not have rung)"), n)
        n = self.log_set()

        self.step = "low memory"
        adb("shell", "am", "send-trim-memory", self.pkg, "RUNNING_CRITICAL")
        time.sleep(2)
        self.home()
        adb("shell", "am", "send-trim-memory", self.pkg, "COMPLETE")
        time.sleep(2)
        self.relaunch()
        self.back_in("low memory", resume=False)
        self.expect("low memory", n)

        self.step = "process death in the background"
        self.home()
        adb("shell", "am", "kill", self.pkg)
        time.sleep(3)
        if adb("shell", "pidof", self.pkg).strip():
            adb("shell", "am", "force-stop", self.pkg)
            time.sleep(2)
        self.relaunch()
        self.back_in("the app killed in the background")
        self.expect("the app killed in the background", n)
        n = self.log_set()

        self.step = "the app stopped on screen"
        adb("shell", "am", "force-stop", self.pkg)
        time.sleep(3)
        self.relaunch()
        self.back_in("the app stopped on screen")
        self.expect("the app stopped on screen", n)

        self.step = "the app stopped while a set is saved"
        self.tap_set("5+")
        adb("shell", "am", "force-stop", self.pkg)
        time.sleep(3)
        self.relaunch()
        self.back_in("the app stopped while a set was saved")
        c = self.counts()
        if c["sets"] not in (n, n + 1) or c["open"] != 1:
            self.fail(f"stopped while saving: {c['sets']} sets saved (expected {n} or {n + 1}), {c['open']} open sessions")
        n = c["sets"]
        n2 = self.log_set()
        self.ok(f"stopped while a set was saved: the set is saved whole or not at all ({n} sets), and the next one adds one ({n2})")
        n = n2

        self.step = "reboot"
        self.reboot()
        self.relaunch()
        self.back_in("a restart of the phone")
        self.expect("a restart of the phone", n)
        n = self.log_set()

        self.finish()
        c = self.counts()
        if c["sets"] != n or c["open"] != 0 or c["done"] != 1:
            self.fail(f"after finishing: {c['sets']} sets (expected {n}), {c['open']} open sessions, {c['done']} finished workouts")
        self.ok(f"finished: all {n} sets kept, the session closed and in the history")
        self.tap_text(S["tab_history"], scrolls=2)
        self.wait(S["hist_outcome_done"], 30)
        self.ok("History shows the session")

    def backup(self, apk):
        """Android backup (Phase 2 section 16): the database and settings go only into an end-to-end encrypted backup, and come back."""
        S = self.S
        self.step = "Android backup"
        before = self.counts()
        if before["sets"] == 0:
            self.fail("no data to back up")
        adb("shell", "bmgr", "enable", "true")
        adb("shell", "bmgr", "transport", TRANSPORT)
        adb("shell", "settings", "put", "secure", "backup_local_transport_parameters", "fake_encryption_flag=true")
        adb("shell", "bmgr", "wipe", TRANSPORT, self.pkg)
        out = adb("shell", "bmgr", "backupnow", self.pkg, timeout=180)
        if "Success" not in out:
            self.fail(f"the encrypted backup did not run: {out.strip()[:300]}")
        adb("uninstall", self.pkg, timeout=120)
        self.install(apk, replace=False)
        if self._read() is None:
            token = next((ln.split()[0] for ln in adb("shell", "bmgr", "list", "sets").splitlines() if ln.strip() and ln.split()[0].isalnum()), None)
            if token:
                adb("shell", "bmgr", "restore", token, self.pkg, timeout=180)
                time.sleep(5)
        self.launch()
        self.wait(S["tab_history"], 60)
        after = self.counts()
        if (after["sets"], after["done"]) != (before["sets"], before["done"]):
            self.fail(f"restored {after['sets']} sets and {after['done']} sessions, expected {before['sets']} and {before['done']}")
        self.ok(f"an end-to-end encrypted backup brought back all {after['sets']} sets and {after['done']} sessions after a reinstall")

        self.step = "no backup without encryption"
        adb("shell", "settings", "put", "secure", "backup_local_transport_parameters", "fake_encryption_flag=false")
        adb("shell", "bmgr", "wipe", TRANSPORT, self.pkg)
        adb("shell", "bmgr", "backupnow", self.pkg, timeout=180)
        adb("uninstall", self.pkg, timeout=120)
        self.install(apk, replace=False)
        token = next((ln.split()[0] for ln in adb("shell", "bmgr", "list", "sets").splitlines() if ln.strip() and ln.split()[0].isalnum()), None)
        if token:
            adb("shell", "bmgr", "restore", token, self.pkg, timeout=180)
            time.sleep(5)
        self.launch()
        self.wait(S["onb_welcome_title"], 60)
        c = self._read()
        if c is not None and c["sets"] > 0:
            self.fail(f"{c['sets']} sets came back from a backup that was not encrypted")
        self.ok("without end-to-end encryption nothing was backed up: the app started fresh")

    def upgrade(self, old_apk, old_root, apk):
        """An open session and its sets, saved by the Phase 3 build the Product Owner could have installed, carried into this version."""
        old = Device(strings(old_root), title="E5 upgrade")
        old.step = "install the older version"
        adb("uninstall", PKG, timeout=120)
        old.install(old_apk, replace=False)
        old.launch()
        old.onboard()
        old.start_workout()
        old.step = "logging on the older version"
        n = old.log_set()
        n = old.log_set()
        n = old.log_set()
        adb("shell", "am", "force-stop", PKG)
        time.sleep(2)

        self.step = "upgrade"
        self.install(apk, replace=True)
        self.relaunch()
        self.back_in("after the upgrade")
        self.expect("after the upgrade, the older version's open session", n)
        n = self.log_set()
        self.finish()
        c = self.counts()
        if c["sets"] != n or c["done"] != 1 or c["open"] != 0:
            self.fail(f"after the upgrade and finishing: {c}")
        self.ok(f"the session begun on the older version finished on this one with all {n} sets")


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
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
    else:
        print(__doc__)
        return 2
    crash = adb("logcat", "-d", "-b", "crash", timeout=30).strip()
    if PKG in crash:
        d.fail("a crash was logged")
    print(f"::notice title=E5::PASS: {len(d.passed)} checks")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
