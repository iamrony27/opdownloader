#!/usr/bin/env python3
"""
Adversarial Challenger Test Harness for Milestone 1
Device: AsteroidsIND (1080x2392 @ 420dpi)

Checks:
1. Aggressive downward drag test:
   - adb shell input swipe 800 1800 800 2390 300
   - Verify FAB bottom coordinate strictly <= 2062
   - Verify clearance from FloatingTabBar (2119px) >= 57px
2. Diagonal and boundary drag tests (bottom-left, bottom-right, top, right)
3. Micro-movement vs Drag disambiguation:
   - Micro-swipe (< 15px) opens SnifferSheet
   - Macro-drag (> 100px) does NOT open SnifferSheet
   - Tap at new dragged location cleanly opens SnifferSheet
4. Display sizing and layout constraints
"""

import sys
import time
import subprocess
import xml.etree.ElementTree as ET
import re

def adb(cmd: str) -> str:
    full = f"adb {cmd}"
    res = subprocess.run(full, shell=True, capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"ADB command failed: {full}\nError: {res.stderr.strip()}")
    return res.stdout.strip()

def dump_ui(retries: int = 5) -> ET.Element:
    for attempt in range(retries):
        try:
            # Clean old dump
            adb("shell rm -f /data/local/tmp/challenger_dump.xml")
            adb("shell uiautomator dump /data/local/tmp/challenger_dump.xml")
            time.sleep(1.0)
            xml_str = adb("shell cat /data/local/tmp/challenger_dump.xml")
            if xml_str.strip().startswith("<?xml"):
                return ET.fromstring(xml_str)
        except Exception as e:
            print(f"dump_ui attempt {attempt + 1} failed: {e}. Retrying...")
            time.sleep(1.5)
    raise RuntimeError("Failed to obtain valid UI hierarchy dump after retries")

def parse_bounds(bounds_str: str):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
    if not m:
        raise ValueError(f"Invalid bounds string: {bounds_str}")
    return tuple(map(int, m.groups()))

def find_fab(root: ET.Element):
    for node in root.iter("node"):
        if node.attrib.get("content-desc") == "SnifferFab":
            return node
    return None

def is_sheet_open(root: ET.Element) -> bool:
    for node in root.iter("node"):
        txt = node.attrib.get("text", "")
        desc = node.attrib.get("content-desc", "")
        if any(k in txt for k in ["Videos on this page", "Scan page", "Streams detected", "No matching video"]):
            return True
        if any(k in desc for k in ["Videos on this page", "Scan page", "Streams detected", "No matching video"]):
            return True
    return False

def dismiss_sheet_if_open():
    root = dump_ui()
    if is_sheet_open(root):
        print("Dismissing open SnifferSheet with KEYEVENT_BACK...")
        adb("shell input keyevent 4")
        time.sleep(1.2)

def main():
    print("=" * 60)
    print("EMPIRICAL CHALLENGER TEST SUITE — MILESTONE 1")
    print("Device: AsteroidsIND (1080x2392 @ 420dpi)")
    print("=" * 60)

    # 1. Ensure App is active on Browser tab with page loaded
    print("\n[Step 0] Verifying app window state...")
    adb("shell am start -n com.opdownloader.app/com.opdownloader.app.MainActivity")
    time.sleep(1.5)
    dismiss_sheet_if_open()

    root = dump_ui()
    fab = find_fab(root)
    if fab is None:
        print("Navigating to Browser tab...")
        adb("shell input tap 740 2200")
        time.sleep(1.5)
        root = dump_ui()
        fab = find_fab(root)

    if fab is None:
        print("Loading https://m.youtube.com in address bar...")
        adb("shell input tap 500 430")
        time.sleep(0.5)
        adb("shell input text https://m.youtube.com")
        time.sleep(0.5)
        # Tap the Go button at (955, 431)
        adb("shell input tap 955 431")
        time.sleep(4.0)
        root = dump_ui()
        fab = find_fab(root)

    assert fab is not None, "FATAL: Could not locate DraggableSnifferFab on BrowserScreen!"
    init_bounds = parse_bounds(fab.attrib["bounds"])
    print(f"DraggableSnifferFab Initial Bounds: {init_bounds}")

    # =========================================================================
    # CHALLENGE 1: Aggressive / Violent Drag Tests
    # =========================================================================
    print("\n" + "=" * 60)
    print("CHALLENGE 1: AGGRESSIVE DOWNWARD DRAG TESTS")
    print("=" * 60)

    # 1A. Specified violent swipe: adb shell input swipe 800 1800 800 2390 300
    print("\nTest 1A: Executing violent downward swipe: input swipe 800 1800 800 2390 300...")
    adb("shell input swipe 800 1800 800 2390 300")
    time.sleep(1.0)

    root = dump_ui()
    fab1 = find_fab(root)
    assert fab1 is not None, "FAIL: FAB disappeared after downward swipe!"
    fx1, fy1, fx2, fy2 = parse_bounds(fab1.attrib["bounds"])
    print(f"FAB bounds after violent downward swipe: [{fx1}, {fy1}][{fx2}, {fy2}]")
    print(f"FAB bottom coordinate: {fy2}")

    tab_bar_top = 2119
    max_allowed_bottom = 2062
    clearance = tab_bar_top - fy2

    print(f"Clearance from FloatingTabBar (y={tab_bar_top}): {clearance}px")
    assert fy2 <= max_allowed_bottom, f"CHALLENGE FAILED: FAB bottom {fy2} exceeded max allowed {max_allowed_bottom}!"
    assert fy2 < tab_bar_top, f"CHALLENGE FAILED: FAB overlapped FloatingTabBar ({fy2} >= {tab_bar_top})!"
    assert clearance >= 57, f"CHALLENGE FAILED: Clearance {clearance}px is less than 57px requirement!"
    print(f">>> PASS: Test 1A (Violent downward swipe strictly clamped at y={fy2} <= {max_allowed_bottom}, clearance={clearance}px >= 57px)")

    # 1B. Violent diagonal swipe towards bottom-right corner (800 1800 -> 1079 2390 in 200ms)
    print("\nTest 1B: Executing violent diagonal bottom-right swipe: 800 1800 -> 1079 2390 in 200ms...")
    adb("shell input swipe 800 1800 1079 2390 200")
    time.sleep(1.0)
    root = dump_ui()
    fab1b = find_fab(root)
    assert fab1b is not None
    bx1, by1, bx2, by2 = parse_bounds(fab1b.attrib["bounds"])
    print(f"FAB bounds after diagonal bottom-right swipe: [{bx1}, {by1}][{bx2}, {by2}]")
    assert by2 <= max_allowed_bottom, f"FAIL: Diagonal swipe broke bottom clamp! by2={by2}"
    assert bx2 <= 1080, f"FAIL: Diagonal swipe breached screen right margin! bx2={bx2}"
    print(f">>> PASS: Test 1B (Diagonal bottom-right swipe clamped: bottom={by2} <= {max_allowed_bottom}, right={bx2} <= 1080)")

    # 1C. Violent diagonal swipe towards bottom-left corner (500 1800 -> 0 2390 in 200ms)
    print("\nTest 1C: Executing violent diagonal bottom-left swipe: 500 1800 -> 0 2390 in 200ms...")
    adb("shell input swipe 500 1800 0 2390 200")
    time.sleep(1.0)
    root = dump_ui()
    fab1c = find_fab(root)
    assert fab1c is not None
    lx1, ly1, lx2, ly2 = parse_bounds(fab1c.attrib["bounds"])
    print(f"FAB bounds after diagonal bottom-left swipe: [{lx1}, {ly1}][{lx2}, {ly2}]")
    assert ly2 <= max_allowed_bottom, f"FAIL: Diagonal left swipe broke bottom clamp! ly2={ly2}"
    assert lx1 >= 0, f"FAIL: Left margin breached! lx1={lx1}"
    print(f">>> PASS: Test 1C (Diagonal bottom-left swipe clamped: bottom={ly2} <= {max_allowed_bottom}, left={lx1} >= 0)")

    # =========================================================================
    # CHALLENGE 2: Micro-movement vs Drag Disambiguation
    # =========================================================================
    print("\n" + "=" * 60)
    print("CHALLENGE 2: MICRO-MOVEMENT VS DRAG DISAMBIGUATION")
    print("=" * 60)

    # First ensure sheet is closed
    dismiss_sheet_if_open()

    # Get fresh FAB location
    root = dump_ui()
    fab_curr = find_fab(root)
    cx1, cy1, cx2, cy2 = parse_bounds(fab_curr.attrib["bounds"])
    center_x = (cx1 + cx2) // 2
    center_y = (cy1 + cy2) // 2
    print(f"Current FAB center: ({center_x}, {center_y})")

    # 2A. Micro-swipe (< 15px, below touch slop)
    # Move by ~8 pixels: center_x, center_y -> center_x + 6, center_y + 5 (distance ~7.8px < 15px)
    micro_end_x = center_x + 6
    micro_end_y = center_y + 5
    print(f"Test 2A: Performing micro-swipe of ~8px (<15px touch slop) from ({center_x}, {center_y}) to ({micro_end_x}, {micro_end_y}) over 100ms...")
    adb(f"shell input swipe {center_x} {center_y} {micro_end_x} {micro_end_y} 100")
    time.sleep(1.5)

    root = dump_ui()
    sheet_open_micro = is_sheet_open(root)
    print(f"SnifferSheet open after micro-swipe: {sheet_open_micro}")
    assert sheet_open_micro, "CHALLENGE FAILED: Micro-swipe (<15px) failed to open SnifferSheet! Tap was consumed or lost."
    print(">>> PASS: Test 2A (Micro-swipe <15px correctly recognized as tap and opened SnifferSheet)")

    # Dismiss SnifferSheet
    print("Dismissing SnifferSheet...")
    adb("shell input keyevent 4")
    time.sleep(1.2)
    root = dump_ui()
    assert not is_sheet_open(root), "FAIL: SnifferSheet did not close after back press!"

    # 2B. Macro-drag (> 100px)
    # Move FAB by 300px horizontally / vertically
    root = dump_ui()
    fab_before_drag = find_fab(root)
    bx1, by1, bx2, by2 = parse_bounds(fab_before_drag.attrib["bounds"])
    drag_start_x = (bx1 + bx2) // 2
    drag_start_y = (by1 + by2) // 2
    drag_end_x = drag_start_x + 350 if drag_start_x < 500 else drag_start_x - 350
    drag_end_y = drag_start_y - 350  # move up by 350px

    print(f"Test 2B: Performing macro-drag of >100px from ({drag_start_x}, {drag_start_y}) to ({drag_end_x}, {drag_end_y}) over 500ms...")
    adb(f"shell input swipe {drag_start_x} {drag_start_y} {drag_end_x} {drag_end_y} 500")
    time.sleep(1.2)

    root = dump_ui()
    sheet_open_drag = is_sheet_open(root)
    print(f"SnifferSheet open after macro-drag (>100px): {sheet_open_drag}")
    assert not sheet_open_drag, "CHALLENGE FAILED: Macro-drag (>100px) incorrectly opened SnifferSheet! Drag disambiguation failed."

    fab_after_drag = find_fab(root)
    assert fab_after_drag is not None, "FAIL: FAB disappeared after drag!"
    afx1, afy1, afx2, afy2 = parse_bounds(fab_after_drag.attrib["bounds"])
    print(f"FAB bounds after macro-drag: [{afx1}, {afy1}][{afx2}, {afy2}]")
    drag_distance = ((afx1 - bx1)**2 + (afy1 - by1)**2)**0.5
    print(f"Actual FAB displacement: {drag_distance:.1f}px")
    assert drag_distance > 100, f"FAIL: FAB was not displaced by drag! Distance = {drag_distance}"
    print(">>> PASS: Test 2B (Macro-drag >100px successfully repositioned FAB without opening SnifferSheet)")

    # 2C. Tap FAB at new dragged location
    new_tap_x = (afx1 + afx2) // 2
    new_tap_y = (afy1 + afy2) // 2
    print(f"Test 2C: Tapping FAB at its new dragged position ({new_tap_x}, {new_tap_y})...")
    adb(f"shell input tap {new_tap_x} {new_tap_y}")
    time.sleep(1.5)

    root = dump_ui()
    sheet_open_tap = is_sheet_open(root)
    print(f"SnifferSheet open after tap at new location: {sheet_open_tap}")
    assert sheet_open_tap, f"CHALLENGE FAILED: Tapping FAB at relocated position ({new_tap_x}, {new_tap_y}) failed to open SnifferSheet!"
    print(">>> PASS: Test 2C (Tapping FAB at relocated coordinates cleanly opens SnifferSheet)")

    # Clean up: dismiss sheet
    adb("shell input keyevent 4")
    time.sleep(1.2)

    # =========================================================================
    # CHALLENGE 3: Top & Lateral Edge Boundary Stress-Testing
    # =========================================================================
    print("\n" + "=" * 60)
    print("CHALLENGE 3: TOP & LATERAL EDGE BOUNDARY STRESS-TESTING")
    print("=" * 60)

    # 3A. Violent upward swipe to top edge: (500, 1000) -> (500, 0)
    print("Test 3A: Violent upward swipe towards screen top: from current FAB center to y=0...")
    root = dump_ui()
    fab3 = find_fab(root)
    cur_x = (parse_bounds(fab3.attrib["bounds"])[0] + parse_bounds(fab3.attrib["bounds"])[2]) // 2
    cur_y = (parse_bounds(fab3.attrib["bounds"])[1] + parse_bounds(fab3.attrib["bounds"])[3]) // 2
    adb(f"shell input swipe {cur_x} {cur_y} {cur_x} 0 250")
    time.sleep(1.0)
    root = dump_ui()
    fab3_up = find_fab(root)
    tx1, ty1, tx2, ty2 = parse_bounds(fab3_up.attrib["bounds"])
    print(f"FAB bounds after violent upward swipe: [{tx1}, {ty1}][{tx2}, {ty2}]")
    assert ty1 >= 536, f"FAIL: FAB breached top container bounds into toolbar! ty1={ty1} < 536"
    print(f">>> PASS: Test 3A (Upward drag stopped at top container edge: ty1={ty1} >= 536)")

    # 3B. Violent drag towards far right edge: (tx, ty) -> (1080, ty)
    print("Test 3B: Violent rightward swipe to x=1080...")
    adb(f"shell input swipe {tx1 + 50} {ty1 + 50} 1080 {ty1 + 50} 250")
    time.sleep(1.0)
    root = dump_ui()
    fab3_right = find_fab(root)
    rx1, ry1, rx2, ry2 = parse_bounds(fab3_right.attrib["bounds"])
    print(f"FAB bounds after rightward swipe: [{rx1}, {ry1}][{rx2}, {ry2}]")
    assert rx2 <= 1080, f"FAIL: FAB breached right edge! rx2={rx2} > 1080"
    print(f">>> PASS: Test 3B (Rightward drag stopped at margin: rx2={rx2} <= 1080)")

    print("\n" + "=" * 60)
    print("ALL EMPIRICAL CHALLENGER TESTS PASSED WITH ZERO FAILURES!")
    print("VERDICT: APPROVE")
    print("=" * 60)

if __name__ == "__main__":
    main()
