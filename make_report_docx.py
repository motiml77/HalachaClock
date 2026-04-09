from docx import Document
from docx.shared import Pt, RGBColor, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_ALIGN_VERTICAL
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
import re

# --- data ---
rows = [
    ("31",  "1",  "מסויימים",           "מסוימים",              "התלויות בתנאים מסויימים (הערה 12)"),
    ("33",  "2",  "המדוייקים",           "המדויקים",             "הזמנים המדוייקים (הערה 14, המשך)"),
    ("40",  "3",  "המדוייקים",           "המדויקים",             "הזמנים המדוייקים (הערה 10, המשך)"),
    ("52",  "4",  "המדוייקים",           "המדויקים",             'הזמנים המדוייקים (הערה בתחתית העמוד)'),
    ("158", "5",  'ינצ"י',               'יצ"ו',                 'ולפיכך ינצ"י דנפיק – הכינוי ינצ"י אינו מוכר; הצורה המקובלת: יצ"ו'),
    ("159", "6",  "יולד ך",              "יולדך",                "רווח מיותר בתוך המילה (מופיע כשתי מילים נפרדות)"),
    ("162", "7",  "בזמון",               "בזימון",               'חייבות בזמון – חסרה יו"ד'),
    ("163", "8",  "בסמיכה",              "בסמיכות",              'הפוסקים נוסף על פי שיטת הרמב"ם שהוא בסמיכה – צ"ל: בסמיכות'),
    ("166", "9",  'שי"סופר מברך',        "שמי שסופר מברך",       "הוא כגון שי\"סופר מברך ובור יוצא – נראה חסרה אות מ'"),
    ("172", "10", "ציבור / צבור",        "צורה אחידה",           "צורות מעורבות באותו עמוד – יש לאחד"),
    ("174", "11", "מרבי",                "מרבני",                "מרבי יוסף אריח – חסרה נו\"ן; צ\"ל: מרבני"),
    ("183", "12", "כוון",                "כיוון",                'כוון שנזכר – חסרה יו"ד'),
    ("188", "13", "שתגשנה",              "שתתגשנה",              "הרכבות שתגשנה מתפתחת – ייתכן חסרה תי\"ו"),
    ("190", "14", "שולמרד",              "שולמד",                "בהערת השוליים – אות ר' מיותרת"),
    ("192", "15", "הרבנים הרבנים",       "הרבנים",               'ולא די לנו מדברי הרבנים הרבנים – הכפלת מילה'),
    ("193", "16", "לאמור",               "לאמר",                 "לכן כתב בס' אמרי יושר לאמור: – הצורה הנכונה: לאמר"),
    ("199", "17", "כספברכת",             "כס' ברכת",             "כספברכת הזיון – חסר רווח ו/או אות"),
    ("205", "18", "מפנקאטש",             "מפאנקאטש",             "הגר\"ג סולוביצ'יק מפנקאטש – השם המלא: פאנקאטש"),
    ("216", "19", 'ר"יי',                'ר"י',                  'ר\'יי מעמדין – גרש עודף; צ"ל ר"י מעמדין (רבי יעקב עמדין)'),
    ("225", "20", 'ס"ס',                 "ס'",                   'בס"ס ברכות – הקיצור אינו מקובל'),
    ("229", "21", "לאמור",               "לאמר",                 "ולכן כתב לאמור: – הצורה הנכונה: לאמר"),
    ("234", "22", 'שהדרי״י',             'שהדרי״ש',              'לדעת מה שהדרי״י מעמדין – ייתכן שגיאת הקלדה (יש לבדוק)'),
    ("267", "23", "לקלולא",              "לקלקלא",               "ולא יבא לידי לקלולא"),
    ("269", "24", "מלשותי",              "מלשוני",               "ואין מלשותי יכול לענות"),
    ("293", "25", "תהמידין",             "תמידין",               "הערת שוליים: תהמידין ומוספין"),
    ("316", "26", "לקמ'",               "לקמן'",                "ודפסיקתא לקמ' – חסרה נו\"ן"),
    ("338", "27", "השניית",              "השנייה",               'בהערה השניית של... – יו"ד מיותרת בסוף'),
    ("348", "28", "ומקשראם",             "ומקשרם",               "הערת שוליים: ומקשראם אל הכהן – אות א' מיותרת"),
    ("351", "29", "כסרה",                "לבדיקה",               'מכל מקום הוא כסרה בזה – ייתכן "כצרה", יש לבדוק'),
    ("369", "30", 'ה "תמימות"',          'ה"תמימות"',            'רווח מיותר בין ה"א לגרשיים'),
    ("371", "31", "בביחד",               "ביחד",                 "שנהגו בביחד – ב' כפולה"),
    ("372", "32", "שאלחזור",             "שלא לחזור",            "מילים דבוקות, יש לבדוק"),
    ("379", "33", "הגניי",               'הגר"י',                "שגיאת הקלדה של ראשי תיבות"),
    ("401", "34", 'ב "תמימות"',          'ב"תמימות"',            "רווח מיותר לפני הגרשיים"),
    ("407", "35", 'יו"טביים',            'יו"ט ביים',            "מילים דבוקות"),
    ("416", "36", "הדרכי",               "הדרכים",               "לפיכן הדרכי להוסיף את הקדושים – אות מ' חסרה"),
    ("421", "37", 'ה"ם',                 "הם",                   'כולומר, ה"ם ספר הרמב"ם – ה\' מנותקת מ-ם בטעות'),
    ("430", "38", "הכוכביים",            "הכוכבים",              'מפני שאין להשמע מפני הכוכביים – יו"ד מיותרת'),
    ("441", "39", "הנינ'ו",              "הניחו",                "שהיינ'ו כבר הוכחנו – גרש שנכנס בתוך מילה"),
    ("441", "40", "שיינ'ו",              "שייכו",                "שיינ'ו אותו לכך – גרש שנכנס בתוך מילה"),
    ("462", "41", "האידיאו",             "האידיאל",              "מתוך האידיאו שלו – אות ל' חסרה בסוף"),
    ("470", "42", 'ו"לחם',              "ולחם",                 'לא אכל ומים לא ו"לחם – גרשיים שנכנסו בתוך המילה'),
    ("471", "43", "ספרתישלמה",           "ספר תישלמה",           'בהערת שוליים – חסר רווח בין "ספר" ל"תישלמה"'),
    ("487", "44", "רביינו",              "רבינו",                'בהערת שוליים – יו"ד מיותרת'),
    ("496", "45", "שיטקו",               "שיתקו",                'שיטקו – ייתכן שצריך "שיתקו"'),
    ("499", "46", "ספיירה",              "ספירה",                "יו\"ד מיותרת – חוזרת לאורך כל נספח ב' (עמ' 499–529). יש לברר עם המחבר אם כוונתי"),
]

HEADERS = ["עמוד", "#", "שגוי", "תיקון", "הקשר"]
COL_WIDTHS = [Cm(1.5), Cm(0.9), Cm(3.2), Cm(3.2), Cm(8.5)]

# colours
HEADER_BG  = "1F3864"   # dark navy
ROW_ALT_BG = "DCE6F1"   # light blue
ROW_BG     = "FFFFFF"

def set_cell_bg(cell, hex_color):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_color)
    tcPr.append(shd)

def set_cell_border(cell):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    tcBorders = OxmlElement("w:tcBorders")
    for side in ("top", "left", "bottom", "right"):
        border = OxmlElement(f"w:{side}")
        border.set(qn("w:val"), "single")
        border.set(qn("w:sz"), "4")
        border.set(qn("w:space"), "0")
        border.set(qn("w:color"), "9DB2CE")
        tcBorders.append(border)
    tcPr.append(tcBorders)

doc = Document()

# --- RTL document direction ---
sectPr = doc.sections[0]._sectPr
bidi = OxmlElement("w:bidi")
sectPr.append(bidi)

# page margins
section = doc.sections[0]
section.page_width  = Cm(21)
section.page_height = Cm(29.7)
section.left_margin = section.right_margin = Cm(2)
section.top_margin  = section.bottom_margin = Cm(2)

# --- Title ---
title = doc.add_paragraph()
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
title_run = title.add_run('דוח הגהה – ספר "שבט מטמונים"')
title_run.bold = True
title_run.font.size = Pt(16)
title_run.font.color.rgb = RGBColor(0x1F, 0x38, 0x64)
title_run.font.name = "David"
title._p.pPr.append(OxmlElement("w:bidi"))

subtitle = doc.add_paragraph()
subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
sub_run = subtitle.add_run("בדיקת שגיאות כתיב ואותיות/רווחים מיותרים בתוך מילים | קריאה ויזואלית")
sub_run.font.size = Pt(10)
sub_run.font.color.rgb = RGBColor(0x44, 0x72, 0xC4)
sub_run.font.name = "David"
subtitle._p.pPr.append(OxmlElement("w:bidi"))

doc.add_paragraph()

# --- Table ---
table = doc.add_table(rows=1 + len(rows), cols=5)
table.style = "Table Grid"

# column widths
for i, w in enumerate(COL_WIDTHS):
    for row in table.rows:
        row.cells[i].width = w

# header row
hdr = table.rows[0]
for i, (cell, text) in enumerate(zip(hdr.cells, HEADERS)):
    set_cell_bg(cell, HEADER_BG)
    set_cell_border(cell)
    cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    p = cell.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p._p.append(OxmlElement("w:bidi"))
    run = p.add_run(text)
    run.bold = True
    run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
    run.font.size = Pt(10)
    run.font.name = "David"

# data rows
for idx, (page, num, wrong, fix, ctx) in enumerate(rows):
    row = table.rows[idx + 1]
    bg = ROW_ALT_BG if idx % 2 == 1 else ROW_BG
    data = [page, num, wrong, fix, ctx]
    for col_i, (cell, text) in enumerate(zip(row.cells, data)):
        set_cell_bg(cell, bg)
        set_cell_border(cell)
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        p = cell.paragraphs[0]
        p._p.append(OxmlElement("w:bidi"))
        if col_i in (0, 1):
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        else:
            p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        # highlight wrong/fix columns
        run = p.add_run(text)
        run.font.size = Pt(9)
        run.font.name = "David"
        if col_i == 2:   # שגוי – red
            run.font.color.rgb = RGBColor(0xC0, 0x00, 0x00)
            run.bold = True
        elif col_i == 3: # תיקון – dark green
            run.font.color.rgb = RGBColor(0x37, 0x5A, 0x23)
            run.bold = True

# --- Footer note ---
doc.add_paragraph()
note = doc.add_paragraph()
pPr = note._p.get_or_add_pPr()
pPr.append(OxmlElement("w:bidi"))
note.alignment = WD_ALIGN_PARAGRAPH.RIGHT
r1 = note.add_run("סה\"כ: 46 ממצאים בכל הספר (עמ' 25–529).  ")
r1.bold = True
r1.font.size = Pt(10)
r1.font.name = "David"

note2 = doc.add_paragraph()
pPr2 = note2._p.get_or_add_pPr()
pPr2.append(OxmlElement("w:bidi"))
note2.alignment = WD_ALIGN_PARAGRAPH.RIGHT
r2 = note2.add_run(
    "הערה חשובה: המילה \"ספיירה\" (במקום \"ספירה\") מופיעה עשרות פעמים לאורך נספח ב' (עמ' 499–529). "
    "ייתכן שזו בחירת כתיב מכוונת של המחבר – יש לברר לפני תיקון."
)
r2.font.size = Pt(9)
r2.font.color.rgb = RGBColor(0x7F, 0x3F, 0x00)
r2.font.name = "David"

out = r"C:\Users\Moti Levi\Desktop\AI\clock_halacha\proofread_reports\full_report.docx"
doc.save(out)
print(f"Saved: {out}")
