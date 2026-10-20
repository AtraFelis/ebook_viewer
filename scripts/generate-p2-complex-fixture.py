"""Public synthetic 48-page PDF with twelve distinct 3.2MP JPEG images; no private data."""

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
    spec = importlib.util.spec_from_file_location("public_pdf_generator", ROOT / "scripts/generate-p2-fixtures.py")
    generator = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(generator)
    sources = []
    writer = PdfWriter()
    try:
        readers = []
        for index in range(12):
            image = Image.new("RGB", (1440, 2240), (255, 255 - index, 250))
            draw = ImageDraw.Draw(image)
            for row in range(28):
                y = 80 + row * 72
                draw.rectangle((80, y, 1360, y + 2), fill=(30 + index, 40 + row, 60))
                draw.text((90, y + 15), f"Public synthetic image {index}, line {row}", fill=(10, 20, 30))
            source = OUTPUT / f"public-complex-source-{index}.pdf"
            sources.append(source)
            generator.image_pdf(source, image, 85)
            readers.append(PdfReader(source))
        for index in range(48):
            writer.add_page(readers[index % 12].pages[0])
        target = OUTPUT / "complex.pdf"
        with target.open("wb") as output:
            writer.write(output)
        assert 0 < target.stat().st_size <= 4 * 1024 * 1024
        report = {"kind": "public synthetic, not private scans", "pages": 48,
                  "distinctImages": 12, "width": 1440, "height": 2240,
                  "bytes": target.stat().st_size,
                  "sha256": hashlib.sha256(target.read_bytes()).hexdigest()}
        (OUTPUT / "complex-manifest.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report))
    finally:
        writer.close()
        for source in sources:
            source.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
