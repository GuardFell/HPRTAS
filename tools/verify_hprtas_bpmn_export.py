# -*- coding: utf-8 -*-
"""Read the exported diagrams back with Windows OCR and check they are legible.

The PDFs and PNGs are raster, so there is no text layer to inspect. This tiles
each rendered diagram, OCRs the tiles, and measures how much of the text the
model contains comes back. A diagram whose labels were drawn too small, wrapped
wrong or drawn on top of each other does not come back.

The 0.6 coverage threshold is the one `verify_cook_meal_bpmn.py` uses.

Usage:
    python tools\\verify_hprtas_bpmn_export.py
"""
import glob
import os
import re
import subprocess
import sys
import tempfile

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import export_hprtas_bpmn_pdf as X  # noqa: E402

OCR_SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                          "ocr_diagram_text.ps1")
MIN_COVERAGE = 0.6
# Windows OCR degrades on a large bitmap because it scales it down first, so the
# diagram is read in tiles this size: at this ceiling a 28 px label comes back.
TILE = 2000
OVERLAP = 150
MIN_WORD = 4


def words(text):
    return [w for w in re.findall(r"[A-Za-z]+", text) if len(w) >= MIN_WORD]


def ocr(path):
    out = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                          "-File", OCR_SCRIPT, "-Path", path],
                         capture_output=True, text=True, errors="replace")
    return out.stdout


def ocr_tiles(png_path):
    img = Image.open(png_path).convert("RGB")
    width, height = img.size
    seen = []
    with tempfile.TemporaryDirectory() as tmp:
        for top in range(0, height, TILE - OVERLAP):
            for left in range(0, width, TILE - OVERLAP):
                box = (left, top, min(left + TILE, width), min(top + TILE, height))
                if box[2] - box[0] < 50 or box[3] - box[1] < 50:
                    continue
                tile = os.path.join(tmp, "tile.png")
                img.crop(box).save(tile)
                seen.append(ocr(tile))
        # a tile pass only ever reads a window of the diagram
    return " \n".join(seen)


def main():
    failures = 0
    models = sorted(glob.glob(os.path.join(X.REPO, "models", "operational", "*.bpmn"))) + \
        sorted(glob.glob(os.path.join(X.REPO, "models", "strategic", "*.bpmn")))
    for path in models:
        name = os.path.basename(path)[:-5]
        diagram = X.Diagram(path)
        expected = set()
        for s in diagram.shapes.values():
            expected.update(words(s["name"]))
        for _, nm, _ in diagram.pools + diagram.lanes:
            expected.update(words(nm))
        if not expected:
            continue

        text = ocr_tiles(os.path.join(X.OUT_DIR, name + ".png"))
        found = set(words(text))
        missing = sorted(expected - found)
        coverage = 1.0 - len(missing) / len(expected)
        ok = coverage >= MIN_COVERAGE
        if not ok:
            failures += 1
        print("%-52s 词覆盖 %.0f%% (%d/%d)%s"
              % (name, 100 * coverage, len(expected) - len(missing), len(expected),
                 "" if ok else "  <-- 未达 %.0f%%" % (100 * MIN_COVERAGE)))
        if missing:
            print("      未读回: %s" % ", ".join(missing[:14]))
    print()
    print("VERIFICATION %s" % ("PASSED" if not failures else
                               "FAILED on %d diagram(s)" % failures))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
