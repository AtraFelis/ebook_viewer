"""Controlled synthetic scan baseline; bundled PC libraries only, no user PDF."""

import hashlib
import importlib.util
import json
from pathlib import Path

from PIL import Image, ImageDraw
from pypdf import PdfReader, PdfWriter


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "local-artifacts/sharpness-fixtures"


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    spec = importlib.util.spec_from_file_location("p2_generator", ROOT / "scripts/generate-p2-fixtures.py")
    generator = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(generator)
    pattern = Image.new("L", (1440, 2240), 255)
    draw = ImageDraw.Draw(pattern)
    for line in range(28):
        y = 80 + line * 72
        draw.rectangle((80, y, 1360, y + 2), fill=40 + line % 5 * 20)
        draw.text((90, y + 15), f"Synthetic scan line {line:02d}", fill=20)
    files = []
    for name, image in (("gray.pdf", pattern), ("rgb.pdf", pattern.convert("RGB"))):
        temporary = OUTPUT / "source.pdf"
        metadata = generator.image_pdf(temporary, image, 85)
        reader = PdfReader(temporary)
        writer = PdfWriter()
        for _ in range(48):
            writer.add_page(reader.pages[0])
        path = OUTPUT / name
        with path.open("wb") as output:
            writer.write(output)
        temporary.unlink()
        files.append({"file": name, "pages": 48, "sharedImage": True,
                      "bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), **metadata})
    manifest = {"kind": "shared JPEG synthetic baseline, not unique high-resolution scans", "files": files}
    (OUTPUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest))


if __name__ == "__main__":
    main()
