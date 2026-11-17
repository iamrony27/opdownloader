#!/usr/bin/env python3
"""
scripts/run_e2e.py
Command-line test runner for the PremiumDownloader E2E automated testing track.
Supports filtering by tier, feature ID, test ID, device target, and verbose logging.
"""

import argparse
import sys
import os
import time
from typing import List

# Ensure project root is in sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from scripts.e2e_device import ADBDevice
from scripts.e2e_test_suite import ALL_TESTS, TestCase, TestResult


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="PremiumDownloader Automated E2E Opaque-Box Test Runner",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 scripts/run_e2e.py --list
  python3 scripts/run_e2e.py --tier 1
  python3 scripts/run_e2e.py --tier 4
  python3 scripts/run_e2e.py --feature 6
  python3 scripts/run_e2e.py --test TC-T1-F06-01
  python3 scripts/run_e2e.py --tier all --verbose
""",
    )
    parser.add_argument(
        "--tier",
        choices=["1", "2", "3", "4", "all"],
        default="all",
        help="Execute tests belonging to a specific tier (default: all)",
    )
    parser.add_argument(
        "--feature",
        choices=[str(i) for i in range(1, 11)],
        help="Execute tests associated with a specific feature number (1..10)",
    )
    parser.add_argument(
        "--test",
        metavar="TEST_ID",
        help="Execute a single test by its exact test ID (e.g. TC-T1-F01-01)",
    )
    parser.add_argument(
        "--device",
        metavar="SERIAL",
        help="Target ADB device serial (default: auto-detect Nothing A059 / AsteroidsIND)",
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Enable detailed ADB command and diagnostic logging",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="List all registered test cases without executing them",
    )
    return parser.parse_args()


def filter_tests(args: argparse.Namespace) -> List[TestCase]:
    tests = ALL_TESTS

    if args.test:
        tests = [t for t in tests if t.test_id.lower() == args.test.lower()]
        if not tests:
            print(f"Error: No test found with ID '{args.test}'. Use --list to see all IDs.")
            sys.exit(2)
        return tests

    if args.tier != "all":
        tier_int = int(args.tier)
        tests = [t for t in tests if t.tier == tier_int]

    if args.feature:
        tests = [t for t in tests if t.feature == args.feature]

    return tests


def print_test_list():
    print(f"\nRegistered E2E Tests for PremiumDownloader (Total: {len(ALL_TESTS)}):")
    print(f"{'Test ID':<15} {'Tier':<6} {'Feature':<8} {'Test Name'}")
    print("-" * 80)
    for t in ALL_TESTS:
        print(f"{t.test_id:<15} {t.tier:<6} {t.feature:<8} {t.name}")
    print("-" * 80)
    print(f"Summary: Tier 1: 50 | Tier 2: 50 | Tier 3: 12 | Tier 4: 6 | Total: 118\n")


def print_summary_table(results: List[TestResult], total_duration: float):
    passed = sum(1 for r in results if r.status == "PASS")
    failed = sum(1 for r in results if r.status == "FAIL")
    errors = sum(1 for r in results if r.status == "ERROR")
    total = len(results)

    print("\n" + "=" * 90)
    print(f"{'E2E TEST EXECUTION RESULTS':^90}")
    print("=" * 90)
    print(f"{'Test ID':<15} {'Tier':<6} {'Feat':<6} {'Status':<8} {'Time (s)':<10} {'Details'}")
    print("-" * 90)

    for r in results:
        status_color = r.status
        # Truncate details for clean table formatting
        detail_msg = r.details.replace("\n", " ")
        if len(detail_msg) > 42:
            detail_msg = detail_msg[:39] + "..."
        print(f"{r.test_id:<15} {r.tier:<6} {r.feature:<6} {status_color:<8} {r.duration:<10.2f} {detail_msg}")

    print("-" * 90)
    print(
        f"TOTAL: {total} | "
        f"PASS: {passed} ({(passed/total)*100 if total else 0:.1f}%) | "
        f"FAIL: {failed} | "
        f"ERROR: {errors} | "
        f"Duration: {total_duration:.2f}s"
    )
    print("=" * 90 + "\n")


def main():
    args = parse_args()

    if args.list:
        print_test_list()
        sys.exit(0)

    selected_tests = filter_tests(args)
    if not selected_tests:
        print("No tests match the specified criteria.")
        sys.exit(0)

    print(f"\nConnecting to ADB device (target: {args.device or 'Auto-detect Nothing A059'})...")
    try:
        dev = ADBDevice(serial=args.device, verbose=args.verbose)
        print(f"Connected to device: {dev.serial} (Resolution: {dev.SCREEN_WIDTH}x{dev.SCREEN_HEIGHT})")
    except Exception as e:
        print(f"Fatal: Failed to connect to device: {e}")
        sys.exit(2)

    # Ensure app is running and responsive in foreground
    print(f"Ensuring application {dev.PACKAGE_NAME} is active in foreground...")
    dev.start_app(clean=False)

    print(f"Starting test execution ({len(selected_tests)} test cases selected)...\n")

    results: List[TestResult] = []
    suite_start = time.time()

    for idx, test in enumerate(selected_tests, 1):
        if args.verbose:
            print(f"[{idx}/{len(selected_tests)}] Running {test.test_id}: {test.name}...")
        res = test.run(dev)
        results.append(res)
        if not args.verbose:
            # Inline progress dot / indicator
            mark = "✓" if res.status == "PASS" else ("✗" if res.status == "FAIL" else "!")
            print(f"[{idx:>3}/{len(selected_tests):<3}] {test.test_id:<15} [{mark}] {res.status:<5} ({res.duration:.2f}s) - {res.name}")

    total_duration = time.time() - suite_start
    print_summary_table(results, total_duration)

    # Return exit code: 0 if all selected tests passed, 1 if any failed/error
    all_passed = all(r.status == "PASS" for r in results)
    sys.exit(0 if all_passed else 1)


if __name__ == "__main__":
    main()
