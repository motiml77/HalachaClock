#!/usr/bin/env python3
"""
Download visible sunrise data from ChaiTables.com for all available Israeli cities.

ChaiTables has terrain data for 8 Israeli metro areas:
jerusalem, haifa, eilat, ashdod, modiin, ariel, karnei_shomron, rechovot

For cities without ChaiTables data (flat/coastal), visible sunrise = sea-level sunrise.
The app maps each city to the nearest metro area that has terrain data.

Output: JSON file bundled with the Android app as an asset.
"""

import json
import time
import os
from datetime import date
from typing import Dict, Tuple, Optional

import requests
from bs4 import BeautifulSoup

BASE_URL = "https://chaitables.com/cgi-bin/ChaiTables.cgi/"
USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
HEBREW_YEAR = 5786  # Regular year (12 months)

# Metro areas that ChaiTables actually has data for
AVAILABLE_METROS = {
    "jerusalem": (31.778, 35.235),
    "haifa": (32.794, 34.990),
    "eilat": (29.558, 34.952),
    "ashdod": (31.804, 34.655),
    "modiin": (31.899, 35.010),
    "ariel": (32.107, 35.173),
    "karnei_shomron": (32.176, 35.096),
    "rechovot": (31.894, 34.810),
}

# Column index (1-based) -> KosherJava Hebrew month (1=Nissan, 7=Tishrei)
# Regular year (12 months): columns are Tishrei through Elul
COLUMN_TO_KJ_REGULAR = {
    1: 7, 2: 8, 3: 9, 4: 10, 5: 11, 6: 12,
    7: 1, 8: 2, 9: 3, 10: 4, 11: 5, 12: 6,
}


def hebrew_to_gregorian(hebrew_year: int, kj_month: int, day: int) -> Optional[date]:
    """Convert Hebrew date to Gregorian using pyluach."""
    try:
        from pyluach.dates import HebrewDate
        hd = HebrewDate(hebrew_year, kj_month, day)
        return hd.to_pydate()
    except Exception:
        return None


def get_days_in_hebrew_month(hebrew_year: int, kj_month: int) -> int:
    """Get the number of days in a Hebrew month."""
    try:
        from pyluach.dates import HebrewDate
        # Create date on 1st of month, get month length
        hd = HebrewDate(hebrew_year, kj_month, 1)
        return hd.month_length()
    except Exception:
        # Fallback defaults
        defaults = {7: 30, 8: 29, 9: 30, 10: 29, 11: 30, 12: 29,
                    1: 30, 2: 29, 3: 30, 4: 29, 5: 30, 6: 29}
        return defaults.get(kj_month, 30)


def build_url(metro_area: str, lat: float, lon: float) -> str:
    """Build ChaiTables CGI URL for Israel."""
    params = {
        "cgi_country": "Eretz_Yisroel",
        "cgi_USAcities2": "0",
        "cgi_eroshgt": "0.0",
        "cgi_geotz": "2.0",
        "cgi_DST": "ON",
        "cgi_exactcoord": "OFF",
        "cgi_types": "0",
        "cgi_RoundSecond": "1",
        "cgi_AddCushion": "2",
        "cgi_24hr": "",
        "cgi_typezman": "-1",
        "cgi_yrheb": str(HEBREW_YEAR),
        "cgi_optionheb": "1",
        "cgi_UserNumber": "413",
        "cgi_Language": "English",
        "cgi_AllowShaving": "OFF",
        "cgi_TableType": "BY",
        "cgi_USAcities1": "1",
        "cgi_MetroArea": metro_area,
        "cgi_searchradius": "2",
        "cgi_eroslatitude": f"{lat:.6f}",
        "cgi_eroslongitude": f"{-lon:.6f}",
    }
    return BASE_URL + "?" + "&".join(f"{k}={v}" for k, v in params.items())


def parse_table(html: str, hebrew_year: int) -> Dict[int, Tuple[int, int, int]]:
    """Parse ChaiTables HTML -> {greg_day_of_year: (hour, minute, second)}"""
    soup = BeautifulSoup(html, 'html.parser')

    # Find the data table (13-16 cells in header)
    zman_table = None
    for table in soup.find_all('table'):
        first_row = table.find('tr')
        if first_row:
            cells = first_row.find_all('td')
            if 13 <= len(cells) <= 16:
                zman_table = table
                break

    if not zman_table:
        return {}

    rows = zman_table.find_all('tr')
    if len(rows) < 2:
        return {}

    header_cells = rows[0].find_all('td')
    total_columns = len(header_cells)
    # Determine month count (subtract day columns on left and right)
    month_count = total_columns - 2
    if month_count < 12:
        month_count = total_columns - 1  # Maybe only left day column

    entries = {}
    conversion_cache = {}

    for row in rows[1:]:
        cells = row.find_all('td')
        if not cells:
            continue

        day_text = cells[0].get_text(strip=True)
        try:
            hebrew_day = int(day_text)
        except ValueError:
            continue
        if not 1 <= hebrew_day <= 30:
            continue

        for col in range(1, min(month_count + 1, len(cells))):
            time_text = cells[col].get_text(strip=True)
            if not time_text or time_text == "--:--:--":
                continue

            kj_month = COLUMN_TO_KJ_REGULAR.get(col)
            if kj_month is None:
                continue

            # Validate day exists in this month
            max_days = get_days_in_hebrew_month(hebrew_year, kj_month)
            if hebrew_day > max_days:
                continue

            # Parse time
            parts = time_text.split(':')
            if len(parts) < 2:
                continue
            try:
                hour = int(parts[0].strip())
                minute = int(parts[1].strip())
                second = int(parts[2].strip()) if len(parts) >= 3 else 0
            except ValueError:
                continue

            # Convert to Gregorian day-of-year
            cache_key = (kj_month, hebrew_day)
            if cache_key not in conversion_cache:
                conversion_cache[cache_key] = hebrew_to_gregorian(
                    hebrew_year, kj_month, hebrew_day
                )
            greg_date = conversion_cache[cache_key]
            if greg_date is None:
                continue

            day_of_year = greg_date.timetuple().tm_yday
            entries[day_of_year] = (hour, minute, second)

    return entries


def download_metro(metro_area: str, lat: float, lon: float) -> Dict[int, Tuple[int, int, int]]:
    """Download and parse data for one metro area."""
    url = build_url(metro_area, lat, lon)
    headers = {"User-Agent": USER_AGENT, "Referer": "https://www.google.com"}

    resp = requests.get(url, headers=headers, timeout=60)
    resp.raise_for_status()

    return parse_table(resp.text, HEBREW_YEAR)


def validate_entries(entries: Dict[int, Tuple[int, int, int]], city: str) -> bool:
    """Validate parsed entries are sensible."""
    if len(entries) < 300:
        print(f"  WARNING: Only {len(entries)} entries for {city} (expected 350+)")
        return False

    # Check time ranges (sunrise in Israel: ~4:30 to ~6:45)
    for day, (h, m, s) in entries.items():
        if h < 4 or h > 7:
            print(f"  WARNING: Unusual hour {h}:{m:02d} on day {day} for {city}")
            return False

    # Check continuity - no gaps > 2 days
    days = sorted(entries.keys())
    for i in range(1, len(days)):
        gap = days[i] - days[i - 1]
        if gap > 3:
            print(f"  WARNING: Gap of {gap} days between day {days[i-1]} and {days[i]} for {city}")

    return True


def main():
    print("=" * 60)
    print("ChaiTables Visible Sunrise Data Downloader")
    print("=" * 60)
    print(f"Hebrew year: {HEBREW_YEAR}")
    print(f"Available metro areas: {len(AVAILABLE_METROS)}")
    print()

    all_data = {}

    for i, (metro, (lat, lon)) in enumerate(AVAILABLE_METROS.items()):
        print(f"[{i+1}/{len(AVAILABLE_METROS)}] Downloading {metro}...", end=" ", flush=True)

        try:
            entries = download_metro(metro, lat, lon)
            if entries:
                valid = validate_entries(entries, metro)
                all_data[metro] = entries
                print(f"OK ({len(entries)} entries{'' if valid else ' - VALIDATION ISSUES'})")
            else:
                print("FAILED (no entries parsed)")
        except Exception as e:
            print(f"ERROR: {e}")

        if i < len(AVAILABLE_METROS) - 1:
            time.sleep(2)

    # Build output JSON
    # Format: compact array of [dayOfYear, hour, minute, second]
    output = {
        "version": 1,
        "generatedAt": date.today().isoformat(),
        "sourceHebrewYear": HEBREW_YEAR,
        "description": (
            "Visible sunrise times by Gregorian day-of-year (1-366). "
            "Data is solar-position-based and valid for any year. "
            "Only cities with significant terrain are included."
        ),
        "metros": {}
    }

    for metro, entries in all_data.items():
        entry_list = []
        for day_of_year in sorted(entries.keys()):
            h, m, s = entries[day_of_year]
            entry_list.append([day_of_year, h, m, s])
        output["metros"][metro] = entry_list

    # City-to-metro mapping for the app
    # Cities without ChaiTables data use the nearest metro area
    output["cityToMetro"] = {
        # Direct matches
        "jerusalem": "jerusalem",
        "haifa": "haifa",
        "eilat": "eilat",
        "ashdod": "ashdod",
        "modiin": "modiin",
        "ariel": "ariel",
        "karnei_shomron": "karnei_shomron",
        "rehovot": "rechovot",
        # Mapped to nearest metro
        "tel_aviv": "rechovot",       # Flat, but close to Rechovot
        "beer_sheva": "ashdod",       # Southern flat area
        "tzfat": "haifa",             # Northern mountain
        "tiberias": "haifa",          # Near Haifa
        "netanya": "rechovot",        # Central coast
        "petach_tikva": "rechovot",   # Central flat
        "bnei_brak": "rechovot",      # Central flat
        "ramat_gan": "rechovot",      # Central flat
        "herzliya": "rechovot",       # Central coast
        "kfar_saba": "karnei_shomron",  # Near Karnei Shomron
        "raanana": "karnei_shomron",    # Near Karnei Shomron
        "ashkelon": "ashdod",         # Nearby coastal
        "afula": "haifa",             # Northern
        "kiryat_shmona": "haifa",     # Far north
        "nahariya": "haifa",          # Northern coast
        "akko": "haifa",              # Near Haifa
        "maale_adumim": "jerusalem",  # Near Jerusalem
        "gush_etzion": "jerusalem",   # Near Jerusalem
        "beit_shemesh": "jerusalem",  # Near Jerusalem (hilly)
        "rishon_lezion": "rechovot",  # Near Rechovot
        "holon": "rechovot",          # Near Rechovot
        "bat_yam": "rechovot",        # Near Rechovot
    }

    # Save to assets
    output_dir = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "app", "src", "main", "assets"
    )
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "chai_tables_preloaded.json")

    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(output, f, ensure_ascii=False, separators=(',', ':'))

    # Stats
    total_entries = sum(len(e) for e in all_data.values())
    file_size = os.path.getsize(output_path)

    print(f"\n{'=' * 60}")
    print(f"DONE!")
    print(f"Metro areas downloaded: {len(all_data)}/{len(AVAILABLE_METROS)}")
    print(f"Total entries: {total_entries}")
    print(f"File size: {file_size / 1024:.1f} KB")
    print(f"Output: {output_path}")

    # Verify coverage
    sample = all_data.get("jerusalem", {})
    if sample:
        days = sorted(sample.keys())
        print(f"\nJerusalem sample: day {days[0]} to {days[-1]}")
        for d in [1, 80, 172, 266, 355]:
            if d in sample:
                h, m, s = sample[d]
                print(f"  Day {d:3d}: {h}:{m:02d}:{s:02d}")

    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
