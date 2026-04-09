#!/usr/bin/env python
"""
Gate script for page-by-page proofreading of "שבט מטמונים".
Ensures Claude works on one page at a time and completes each before moving on.

Commands:
    python next_page.py init     - Scan PDF and initialize progress.json
    python next_page.py status   - Show current proofreading status
    python next_page.py start    - Set current page to first content page
    python next_page.py extract  - Extract text of current page to pages/
    python next_page.py done     - Mark current page complete (requires report)
    python next_page.py next     - Advance to next page (requires current done)
    python next_page.py jump N   - Jump to specific page N (for resuming)
"""

import sys
import json
import os
from datetime import datetime

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PDF_PATH = r"C:\Users\Moti Levi\Downloads\שבט מטמונים ספר להדפסה  עם שערים כסליו תשפו - יז כסלו תשפו.pdf"
PROGRESS_FILE = os.path.join(BASE_DIR, "proofread_reports", "progress.json")
REPORTS_DIR = os.path.join(BASE_DIR, "proofread_reports")
PAGES_DIR = os.path.join(BASE_DIR, "pages")


def load_progress():
    if os.path.exists(PROGRESS_FILE):
        with open(PROGRESS_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return None


def save_progress(data):
    data["last_updated"] = datetime.now().isoformat()
    os.makedirs(os.path.dirname(PROGRESS_FILE), exist_ok=True)
    with open(PROGRESS_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def report_path(page_num):
    return os.path.join(REPORTS_DIR, f"report_page_{page_num:03d}.md")


def page_text_path(page_num):
    return os.path.join(PAGES_DIR, f"page_{page_num:03d}.txt")


def cmd_init():
    """Scan PDF and create progress.json with list of content pages."""
    import fitz
    doc = fitz.open(PDF_PATH)
    total = len(doc)
    content_pages = []
    for i in range(total):
        text = doc[i].get_text().strip()
        if len(text) > 50:
            content_pages.append(i + 1)  # 1-indexed
    doc.close()

    data = {
        "total_pages": total,
        "content_pages": content_pages,
        "current_page": None,
        "completed_pages": [],
        "skipped_pages": [],
    }
    save_progress(data)
    print(f"Initialized. {total} total pages, {len(content_pages)} with content.")
    print(f"First content page: {content_pages[0]}, Last: {content_pages[-1]}")


def cmd_status():
    """Show current proofreading status."""
    data = load_progress()
    if not data:
        print("ERROR: Not initialized. Run 'python next_page.py init' first.")
        sys.exit(1)

    current = data["current_page"]
    completed = data["completed_pages"]
    content = data["content_pages"]

    print("=" * 50)
    print("  PROOFREADING STATUS - שבט מטמונים")
    print("=" * 50)
    print(f"  Total pages:     {data['total_pages']}")
    print(f"  Content pages:   {len(content)}")
    print(f"  Completed:       {len(completed)}")
    print(f"  Remaining:       {len(content) - len(completed)}")
    print(f"  Current page:    {current or 'None (use start/next)'}")
    print(f"  Progress:        {len(completed)}/{len(content)} ({100*len(completed)//max(len(content),1)}%)")
    print(f"  Last updated:    {data.get('last_updated', 'N/A')}")
    print("=" * 50)

    if current:
        has_report = os.path.exists(report_path(current))
        print(f"\n  Page {current} report: {'EXISTS' if has_report else 'NOT YET WRITTEN'}")


def cmd_start():
    """Set current page to first content page."""
    data = load_progress()
    if not data:
        print("ERROR: Not initialized. Run 'python next_page.py init' first.")
        sys.exit(1)

    if data["current_page"] is not None:
        print(f"WARNING: Already on page {data['current_page']}.")
        print("Use 'next' to advance or 'jump N' to go to a specific page.")
        return

    first = data["content_pages"][0]
    data["current_page"] = first
    save_progress(data)
    print(f"Started. Current page set to {first}.")
    print(f"Now run: python next_page.py extract")


def cmd_extract():
    """Extract text of current page to a file."""
    data = load_progress()
    if not data or data["current_page"] is None:
        print("ERROR: No current page. Run 'start' first.")
        sys.exit(1)

    page_num = data["current_page"]

    import fitz
    doc = fitz.open(PDF_PATH)
    # page_num is 1-indexed, fitz uses 0-indexed
    page = doc[page_num - 1]
    text = page.get_text()
    doc.close()

    os.makedirs(PAGES_DIR, exist_ok=True)
    out_path = page_text_path(page_num)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(f"=== PAGE {page_num} ===\n\n")
        f.write(text)

    print(f"Extracted page {page_num} -> {out_path}")
    print(f"Text length: {len(text)} characters")
    print(f"\nNext step: Read the file, proofread it, write report to:")
    print(f"  {report_path(page_num)}")


def cmd_done():
    """Mark current page as completed. Requires report to exist."""
    data = load_progress()
    if not data or data["current_page"] is None:
        print("ERROR: No current page set.")
        sys.exit(1)

    page_num = data["current_page"]
    rpath = report_path(page_num)

    if not os.path.exists(rpath):
        print(f"BLOCKED: Report not found at {rpath}")
        print(f"You MUST write the proofreading report before marking done.")
        sys.exit(1)

    # Check report has actual content (not empty)
    with open(rpath, "r", encoding="utf-8") as f:
        content = f.read().strip()
    if len(content) < 50:
        print(f"BLOCKED: Report at {rpath} seems too short ({len(content)} chars).")
        print(f"Write a complete proofreading report first.")
        sys.exit(1)

    if page_num not in data["completed_pages"]:
        data["completed_pages"].append(page_num)
        data["completed_pages"].sort()
    save_progress(data)
    print(f"Page {page_num} marked as COMPLETED.")
    print(f"Now run: python next_page.py next")


def cmd_next():
    """Advance to the next content page. Requires current to be completed."""
    data = load_progress()
    if not data:
        print("ERROR: Not initialized.")
        sys.exit(1)

    current = data["current_page"]
    if current is not None and current not in data["completed_pages"]:
        print(f"BLOCKED: Page {current} is not yet completed.")
        print(f"Write the report and run 'done' first.")
        sys.exit(1)

    content_pages = data["content_pages"]
    if current is None:
        next_page = content_pages[0]
    else:
        try:
            idx = content_pages.index(current)
            if idx + 1 >= len(content_pages):
                print("ALL PAGES COMPLETED! No more pages to proofread.")
                return
            next_page = content_pages[idx + 1]
        except ValueError:
            # Current page not in content_pages, find next one
            next_page = None
            for p in content_pages:
                if p > current and p not in data["completed_pages"]:
                    next_page = p
                    break
            if next_page is None:
                print("ALL PAGES COMPLETED!")
                return

    data["current_page"] = next_page
    save_progress(data)
    print(f"Advanced to page {next_page}.")
    print(f"Now run: python next_page.py extract")


def cmd_jump(page_num):
    """Jump to a specific page number."""
    data = load_progress()
    if not data:
        print("ERROR: Not initialized.")
        sys.exit(1)

    current = data["current_page"]
    if current is not None and current not in data["completed_pages"]:
        print(f"BLOCKED: Page {current} is not yet completed.")
        print(f"Complete current page first, or use 'skip' to skip it.")
        sys.exit(1)

    if page_num not in data["content_pages"]:
        print(f"WARNING: Page {page_num} has no extractable text content.")
        print(f"Setting anyway - you may want to check if it's an image-only page.")

    data["current_page"] = page_num
    save_progress(data)
    print(f"Jumped to page {page_num}.")
    print(f"Now run: python next_page.py extract")


def cmd_skip():
    """Skip current page without completing it."""
    data = load_progress()
    if not data or data["current_page"] is None:
        print("ERROR: No current page.")
        sys.exit(1)

    page_num = data["current_page"]
    if "skipped_pages" not in data:
        data["skipped_pages"] = []
    data["skipped_pages"].append(page_num)
    data["current_page"] = None
    save_progress(data)
    print(f"Skipped page {page_num}.")
    print(f"Run 'next' to go to the following page.")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(0)

    cmd = sys.argv[1].lower()

    if cmd == "init":
        cmd_init()
    elif cmd == "status":
        cmd_status()
    elif cmd == "start":
        cmd_start()
    elif cmd == "extract":
        cmd_extract()
    elif cmd == "done":
        cmd_done()
    elif cmd == "next":
        cmd_next()
    elif cmd == "jump":
        if len(sys.argv) < 3:
            print("Usage: python next_page.py jump <page_number>")
            sys.exit(1)
        cmd_jump(int(sys.argv[2]))
    elif cmd == "skip":
        cmd_skip()
    else:
        print(f"Unknown command: {cmd}")
        print(__doc__)
        sys.exit(1)
