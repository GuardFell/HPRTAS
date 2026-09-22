"""Render `tests/test-plan.md` as a Word document at `tests/exports/test-plan.docx`.

The acceptance test plan is kept as Markdown, which is the source of record. The assessment asks
for it in `.docx`, `.xlsx` or PDF, so this renders a readable Word copy rather than keeping a second
document that can drift from the first: re-run the script after any change to the plan.

    cd tests/exports && python render-test-plan.py

Needs `python-docx`. There is no PDF toolchain on the machines this was written on, which is why the
export is `.docx`.
"""

import re
import sys
from pathlib import Path

from docx import Document
from docx.enum.section import WD_ORIENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Cm, Pt

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "tests" / "test-plan.md"
OUT = ROOT / "tests" / "exports" / "test-plan.docx"

INLINE = re.compile(r"(\*\*.+?\*\*|`[^`]+`)")


def add_runs(par, text):
    """Add text to a paragraph, honouring **bold** and `code` spans."""
    for part in INLINE.split(text):
        if not part:
            continue
        if part.startswith("**") and part.endswith("**"):
            par.add_run(part[2:-2]).bold = True
        elif part.startswith("`") and part.endswith("`"):
            run = par.add_run(part[1:-1])
            run.font.name = "Consolas"
            run.font.size = Pt(8.5)
        else:
            par.add_run(part)


def split_row(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def is_block_start(text):
    return (not text) or text[0] in "#|>" or bool(re.match(r"^([-*]|\d+\.) ", text))


def add_table(doc, rows):
    header, body = rows[0], rows[1:]
    table = doc.add_table(rows=1, cols=len(header))
    table.style = "Table Grid"
    table.autofit = True
    for i, text in enumerate(header):
        cell = table.rows[0].cells[i]
        cell.text = ""
        par = cell.paragraphs[0]
        par.paragraph_format.space_after = Pt(0)
        run = par.add_run(text)
        run.bold = True
        run.font.size = Pt(8)
    for row in body:
        cells = table.add_row().cells
        for i, text in enumerate(row[: len(header)]):
            cells[i].text = ""
            par = cells[i].paragraphs[0]
            par.paragraph_format.space_after = Pt(0)
            par.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
            add_runs(par, text)
            for run in par.runs:
                if run.font.size is None:
                    run.font.size = Pt(8)
    doc.add_paragraph()


def render(lines, doc):
    i = 0
    while i < len(lines):
        stripped = lines[i].strip()

        if stripped.startswith("```"):
            i += 1
            block = []
            while i < len(lines) and not lines[i].strip().startswith("```"):
                block.append(lines[i])
                i += 1
            run = doc.add_paragraph().add_run("\n".join(block))
            run.font.name = "Consolas"
            run.font.size = Pt(8)
            i += 1
            continue

        if stripped.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                cells = split_row(lines[i])
                if not all(re.fullmatch(r":?-{2,}:?", c) for c in cells if c):
                    rows.append(cells)
                i += 1
            if rows:
                add_table(doc, rows)
            continue

        if stripped.startswith("#"):
            level = len(stripped) - len(stripped.lstrip("#"))
            par = doc.add_heading(level=0) if level == 1 else doc.add_heading(level=min(level - 1, 4))
            add_runs(par, stripped[level:].strip())
            i += 1
            continue

        if stripped.startswith(">"):
            block = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                block.append(lines[i].strip().lstrip(">").strip())
                i += 1
            par = doc.add_paragraph()
            par.paragraph_format.left_indent = Cm(0.5)
            add_runs(par, " ".join(b for b in block if b))
            for run in par.runs:
                run.italic = True
                run.font.size = Pt(9)
            continue

        if re.match(r"^[-*] ", stripped):
            add_runs(doc.add_paragraph(style="List Bullet"), stripped[2:])
            i += 1
            continue

        if re.match(r"^\d+\. ", stripped):
            add_runs(doc.add_paragraph(style="List Number"), re.sub(r"^\d+\. ", "", stripped))
            i += 1
            continue

        if not stripped:
            i += 1
            continue

        # A paragraph runs until a blank line or another block starts, so consecutive plain lines
        # are joined rather than each becoming its own paragraph.
        block = []
        while i < len(lines) and not is_block_start(lines[i].strip()):
            block.append(lines[i].strip())
            i += 1
        add_runs(doc.add_paragraph(), " ".join(block))


def main():
    doc = Document()

    section = doc.sections[0]
    section.orientation = WD_ORIENT.LANDSCAPE
    section.page_width, section.page_height = section.page_height, section.page_width
    for attr in ("left_margin", "right_margin", "top_margin", "bottom_margin"):
        setattr(section, attr, Cm(1.5))
    doc.styles["Normal"].font.size = Pt(9.5)

    head = doc.add_paragraph()
    add_runs(
        head,
        "Acceptance test plan for the Hospital Patient Referral, Treatment and Administration "
        "System. Rendered from `tests/test-plan.md` by `tests/exports/render-test-plan.py`; the "
        "Markdown file is the source of record and this document is a readable copy of it, "
        "committed with it.",
    )
    for run in head.runs:
        run.italic = True
        run.font.size = Pt(9)
    doc.add_paragraph()

    render(SRC.read_text(encoding="utf-8").splitlines(), doc)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    doc.save(OUT)
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    sys.exit(main())
