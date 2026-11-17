#!/usr/bin/env python3
"""
scripts/e2e_device.py
Opaque-box ADB controller and UIAutomator inspection helper for PremiumDownloader.
Designed specifically for physical device Nothing A059 (1080x2392 @ 420dpi).
"""

import os
import re
import shlex
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
from typing import Dict, List, Optional, Tuple, Union


class UINode:
    """Represents a node in the Android accessibility UI hierarchy."""

    def __init__(self, attrib: Dict[str, str]):
        self.attrib = attrib
        self.text: str = attrib.get("text", "")
        self.content_desc: str = attrib.get("content-desc", "")
        self.class_name: str = attrib.get("class", "")
        self.resource_id: str = attrib.get("resource-id", "")
        self.clickable: bool = attrib.get("clickable", "false") == "true"
        self.bounds_raw: str = attrib.get("bounds", "[0,0][0,0]")
        self.bounds: Tuple[int, int, int, int] = self._parse_bounds(self.bounds_raw)
        self.x1, self.y1, self.x2, self.y2 = self.bounds
        self.width: int = max(0, self.x2 - self.x1)
        self.height: int = max(0, self.y2 - self.y1)
        self.center_x: int = self.x1 + self.width // 2
        self.center_y: int = self.y1 + self.height // 2

    @staticmethod
    def _parse_bounds(bounds_str: str) -> Tuple[int, int, int, int]:
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
        if m:
            return int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4))
        return (0, 0, 0, 0)

    def contains_point(self, x: int, y: int) -> bool:
        return self.x1 <= x <= self.x2 and self.y1 <= y <= self.y2

    def __repr__(self) -> str:
        ident = self.text or self.content_desc or self.class_name
        return f"<UINode '{ident}' bounds={self.bounds_raw}>"


class ADBDevice:
    """High-reliability ADB client with safe dump caching and gesture primitives."""

    PACKAGE_NAME = "com.opdownloader.app"
    MAIN_ACTIVITY = "com.opdownloader.app/.MainActivity"

    # Known device physical metrics for Nothing A059 (AsteroidsIND)
    SCREEN_WIDTH = 1080
    SCREEN_HEIGHT = 2392
    STATUS_BAR_HEIGHT = 126
    NAV_BAR_TOP = 2329
    NAV_BAR_HEIGHT = 63
    FLOATING_TABS_TOP = 2119
    MAX_ALLOWED_FAB_BOTTOM = 2119

    def __init__(self, serial: Optional[str] = None, verbose: bool = False):
        self.verbose = verbose
        self.serial = serial or self._auto_detect_device()
        self._last_dump_time = 0.0
        self._last_dump_xml = ""
        self._last_dump_nodes: List[UINode] = []

    def _log(self, msg: str):
        if self.verbose:
            print(f"[ADBDevice] {msg}")

    def _auto_detect_device(self) -> str:
        """Finds connected wireless or USB device."""
        try:
            output = subprocess.check_output(["adb", "devices", "-l"], text=True)
            lines = [line.strip() for line in output.splitlines() if line.strip() and not line.startswith("List of")]
            devices = [line.split()[0] for line in lines if "device" in line.split()]
            if not devices:
                raise RuntimeError("No connected ADB devices found.")
            for dev in devices:
                if "Asteroids" in output or "A059" in output:
                    self._log(f"Auto-selected device: {dev}")
                    return dev
            self._log(f"Selected first available device: {devices[0]}")
            return devices[0]
        except Exception as e:
            raise RuntimeError(f"Failed to detect ADB device: {e}")

    def run_cmd(self, args: List[str], timeout: int = 15) -> Tuple[int, str, str]:
        """Runs an ADB command with timeout."""
        cmd = ["adb", "-s", self.serial] + args
        self._log(f"EXEC: {' '.join(cmd)}")
        try:
            p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=timeout)
            return p.returncode, p.stdout, p.stderr
        except subprocess.TimeoutExpired:
            self._log(f"Command timed out after {timeout}s: {' '.join(cmd)}")
            return 124, "", f"Command timed out after {timeout}s"
        except Exception as e:
            self._log(f"Command error: {e}")
            return 1, "", str(e)

    def shell(self, cmd_str: str, timeout: int = 15) -> str:
        """Runs a command via adb shell."""
        cmd = ["adb", "-s", self.serial, "shell", cmd_str]
        self._log(f"SHELL: {cmd_str}")
        try:
            p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=timeout)
            return p.stdout
        except subprocess.TimeoutExpired:
            self._log(f"Shell command timed out after {timeout}s: {cmd_str}")
            return ""
        except Exception as e:
            self._log(f"Shell error: {e}")
            return ""

    # --- Process & Navigation Control ---

    def start_app(self, clean: bool = False) -> bool:
        """Launches the target app."""
        if clean:
            self.stop_app()
            time.sleep(0.3)
        code, out, _ = self.run_cmd(["shell", "am", "start", "-W", "-n", self.MAIN_ACTIVITY])
        time.sleep(0.8)
        return code == 0

    def stop_app(self) -> bool:
        """Force stops the app process."""
        code, _, _ = self.run_cmd(["shell", "am", "force-stop", self.PACKAGE_NAME])
        return code == 0

    def is_app_running(self) -> bool:
        out = self.shell(f"pidof {self.PACKAGE_NAME}")
        return bool(out.strip())

    def ensure_browser_tab(self, load_default_page: bool = False) -> bool:
        """Navigates to the Browser tab regardless of current screen state."""
        self.dismiss_keyboard()
        # Ensure app is in foreground
        self.start_app(clean=False)
        nodes = self.dump_hierarchy(force_refresh=True)

        # Check if already in browser screen
        in_browser = any(n.text == "Web Sniffer" for n in nodes)
        if not in_browser:
            # Click Browser tab in FloatingTabBar (label 'Browser' or icon)
            for n in nodes:
                if n.text == "Browser" or n.content_desc == "Browser":
                    self.tap(n.center_x, n.center_y)
                    time.sleep(0.8)
                    break
            else:
                # Fallback: known coordinates for Browser tab center on Nothing A059: (740, 2205)
                self.tap(740, 2205)
                time.sleep(0.8)
            nodes = self.dump_hierarchy(force_refresh=True)

        if load_default_page:
            has_webview = any("WebView" in n.class_name for n in nodes)
            if not has_webview:
                yt = next((n for n in nodes if n.text == "YouTube"), None)
                if yt:
                    self.tap_node(yt)
                    time.sleep(1.8)
        return True

    def ensure_home_tab(self) -> bool:
        self.dismiss_keyboard()
        self.start_app(clean=False)
        nodes = self.dump_hierarchy(force_refresh=True)
        for n in nodes:
            if n.text == "Home" or n.content_desc == "Home":
                self.tap(n.center_x, n.center_y)
                time.sleep(0.8)
                return True
        self.tap(141, 2205)
        time.sleep(0.8)
        return True

    def reverse_port(self, remote_port: int = 8765, local_port: int = 8765) -> bool:
        """Ensures ADB port reverse is active for local fixture server."""
        code, out, _ = self.run_cmd(["reverse", f"tcp:{remote_port}", f"tcp:{local_port}"])
        return code == 0

    # --- UIAutomator Hierarchy Dump & Parsing ---

    def dump_hierarchy(self, force_refresh: bool = False, max_retries: int = 2) -> List[UINode]:
        """
        Dumps and parses the accessibility node hierarchy.
        Enforces a minimum 0.6s pause between calls to avoid SIGKILL (code 137).
        """
        now = time.time()
        elapsed = now - self._last_dump_time
        if not force_refresh and self._last_dump_nodes and elapsed < 1.0:
            return self._last_dump_nodes

        if elapsed < 0.6:
            time.sleep(0.6 - elapsed)

        remote_path = "/sdcard/e2e_dump.xml"
        success = False

        for attempt in range(max_retries):
            self.shell(f"uiautomator dump {remote_path}", timeout=8)
            self._last_dump_time = time.time()
            xml_content = self.shell(f"cat {remote_path}", timeout=5)
            if xml_content and "<hierarchy" in xml_content:
                self._last_dump_xml = xml_content
                success = True
                break
            time.sleep(0.7 * (attempt + 1))

        if not success:
            self._log("UIAutomator dump failed, using cached hierarchy if available.")
            return self._last_dump_nodes

        nodes: List[UINode] = []
        try:
            root = ET.fromstring(self._last_dump_xml)
            for elem in root.iter("node"):
                nodes.append(UINode(elem.attrib))
        except Exception as e:
            self._log(f"XML parse error: {e}")

        self._last_dump_nodes = nodes
        return nodes

    def find_node(
        self,
        text: Optional[str] = None,
        text_contains: Optional[str] = None,
        content_desc: Optional[str] = None,
        class_name: Optional[str] = None,
        refresh: bool = True,
    ) -> Optional[UINode]:
        nodes = self.dump_hierarchy(force_refresh=refresh)
        for n in nodes:
            if text is not None and n.text != text:
                continue
            if text_contains is not None and text_contains.lower() not in n.text.lower():
                continue
            if content_desc is not None and content_desc != n.content_desc:
                continue
            if class_name is not None and class_name != n.class_name:
                continue
            return n
        return None

    def find_all_nodes(
        self,
        text: Optional[str] = None,
        text_contains: Optional[str] = None,
        content_desc: Optional[str] = None,
        class_name: Optional[str] = None,
        refresh: bool = True,
    ) -> List[UINode]:
        nodes = self.dump_hierarchy(force_refresh=refresh)
        matched: List[UINode] = []
        for n in nodes:
            if text is not None and n.text != text:
                continue
            if text_contains is not None and text_contains.lower() not in n.text.lower():
                continue
            if content_desc is not None and content_desc != n.content_desc:
                continue
            if class_name is not None and class_name != n.class_name:
                continue
            matched.append(n)
        return matched

    def find_sniffer_fab(self, refresh: bool = True) -> Optional[UINode]:
        """Locates the floating sniffer widget."""
        nodes = self.dump_hierarchy(force_refresh=refresh)
        # Check for text patterns: "Sniff videos", "$count video(s) found", "Scanning page…"
        for n in nodes:
            if "video" in n.text.lower() or "sniff" in n.text.lower():
                # Sniffer FAB is located above the bottom tabs and has height < 250px
                if n.y1 > 1500 and n.height < 250:
                    return n
        # Check for clickable container holding "Tap to pick a stream" or "Checking sizes…"
        for n in nodes:
            if "stream" in n.text.lower() or "checking sizes" in n.text.lower():
                return n
        return None

    # --- Gestures & Inputs ---

    def tap(self, x: int, y: int):
        """Dispatches an input tap at (x, y)."""
        self.shell(f"input tap {x} {y}")

    def tap_node(self, node: UINode):
        """Taps the center of a UINode."""
        self.tap(node.center_x, node.center_y)

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 400):
        """Dispatches a swipe or drag gesture with duration >= 300ms."""
        self.shell(f"input swipe {x1} {y1} {x2} {y2} {duration_ms}")

    def drag(self, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 500):
        """Dispatches a drag gesture with steady velocity."""
        self.swipe(x1, y1, x2, y2, duration_ms)

    def input_text(self, text: str):
        """Inputs text safely via adb."""
        escaped = text.replace(" ", "%s")
        # Quote characters like & ; ( )
        escaped = re.sub(r"([&;()<>|\"'$`])", r"\\\1", escaped)
        self.shell(f"input text {escaped}")

    def keyevent(self, keycode: Union[int, str]):
        """Dispatches an Android keyevent (e.g. 4 for BACK, 66 for ENTER)."""
        self.shell(f"input keyevent {keycode}")

    def dismiss_keyboard(self):
        """Dismisses the soft keyboard if open."""
        self.keyevent(111)  # KEYCODE_ESCAPE
        time.sleep(0.2)
        self.keyevent(4)    # KEYCODE_BACK
        time.sleep(0.3)

    # --- Screen Capture & Logcat ---

    def screencap(self, save_path: Optional[str] = None) -> bytes:
        """Streams screenshot directly into memory or saves to file."""
        cmd = ["adb", "-s", self.serial, "exec-out", "screencap", "-p"]
        p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        png_data = p.stdout
        if save_path:
            os.makedirs(os.path.dirname(os.path.abspath(save_path)), exist_ok=True)
            with open(save_path, "wb") as f:
                f.write(png_data)
        return png_data

    def get_screen_resolution_from_png(self) -> Tuple[int, int]:
        png = self.screencap()
        if len(png) >= 24 and png[:8] == b"\x89PNG\r\n\x1a\n":
            w, h = struct.unpack(">II", png[16:24])
            return w, h
        return (self.SCREEN_WIDTH, self.SCREEN_HEIGHT)

    def clear_logcat(self):
        self.run_cmd(["logcat", "-c"])

    def logcat(self, tags: Optional[str] = None, filter_str: Optional[str] = None) -> str:
        """Reads recent logcat buffer."""
        cmd = ["shell", "logcat", "-d"]
        if tags:
            cmd.extend(["-s", tags])
        _, out, _ = self.run_cmd(cmd)
        if filter_str:
            lines = [l for l in out.splitlines() if filter_str in l]
            return "\n".join(lines)
        return out

    def navigate_browser_to(self, url: str) -> bool:
        """Enters a URL into the browser URL box and taps Go."""
        self.ensure_browser_tab()
        # Find EditText
        edit = self.find_node(class_name="android.widget.EditText")
        if not edit:
            # Tap search placeholder
            ph = self.find_node(text="Search or enter a website")
            if ph:
                self.tap_node(ph)
                time.sleep(0.5)
                edit = self.find_node(class_name="android.widget.EditText")

        if edit:
            self.tap_node(edit)
            time.sleep(0.3)
            # Clear text efficiently in a single on-device shell loop
            self.shell("for i in $(seq 1 40); do input keyevent 67; done")
            self.input_text(url)
            time.sleep(0.3)
            # Tap Go
            go_node = self.find_node(text="Go")
            if go_node:
                self.tap_node(go_node)
            else:
                self.keyevent(66)  # ENTER
            time.sleep(1.2)
            self.dismiss_keyboard()
            return True
        return False
