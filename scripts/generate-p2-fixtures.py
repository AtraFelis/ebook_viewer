"""Generate public synthetic P2 fixtures with the bundled desktop Python tools.

No user PDF is read. These tools are not Android application dependencies.
"""

import hashlib
import io
import json
from pathlib import Path

import PIL
import pypdf
import reportlab
from PIL import Image, ImageDraw
from pypdf import PdfReader, PdfWriter
from reportlab.pdfgen import canvas


OUTPUT = Path(__file__).resolve().parents[1] / "local-artifacts" / "p2-fixtures"
# This public fixture value is test data, never a user's document credential.
FIXTURE_PASSWORD = "p2-public-synthetic-fixture"


def text_fixture() -> None:
    buffer = io.BytesIO()
    document = canvas.Canvas(buffer, pagesize=(360, 540), invariant=1)
    document.setTitle("Synthetic platform feature fixture")
    document.setFont("Helvetica", 16)
    document.drawString(20, 490, "needle alpha beta needle")
    document.drawString(20, 460, "Synthetic platform capability check")
    document.showPage()
    document.save()
    reader = PdfReader(io.BytesIO(buffer.getvalue()))
    writer = PdfWriter()
    writer.append_pages_from_reader(reader)
    parent = writer.add_outline_item("Synthetic chapter", 0)
    writer.add_outline_item("Synthetic section", 0, parent=parent)
    with (OUTPUT / "text-outline.pdf").open("wb") as destination:
        writer.write(destination)
    for algorithm, name in (("AES-128", "aes128.pdf"), ("AES-256", "aes256.pdf")):
        encrypted = PdfWriter(clone_from=reader)
        encrypted.encrypt(FIXTURE_PASSWORD, algorithm=algorithm)
        with (OUTPUT / name).open("wb") as destination:
            encrypted.write(destination)


def image_pdf(path: Path, image: Image.Image, quality: int) -> dict:
    encoded = io.BytesIO()
    image.save(encoded, "JPEG", quality=quality, subsampling=0, optimize=False)
    jpeg = encoded.getvalue()
    color_space = b"/DeviceGray" if image.mode == "L" else b"/DeviceRGB"
    content = b"q 347 0 0 537 0 0 cm /Im0 Do Q\n"
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 347 537] "
        b"/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /XObject /Subtype /Image /Width "
        + str(image.width).encode()
        + b" /Height "
        + str(image.height).encode()
        + b" /ColorSpace "
        + color_space
        + b" /BitsPerComponent 8 /Filter /DCTDecode /Length "
        + str(len(jpeg)).encode()
        + b" >>\nstream\n"
        + jpeg
        + b"\nendstream",
        b"<< /Length " + str(len(content)).encode() + b" >>\nstream\n" + content + b"endstream",
    ]
    result = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = [0]
    for number, value in enumerate(objects, 1):
        offsets.append(len(result))
        result.extend(f"{number} 0 obj\n".encode() + value + b"\nendobj\n")
    xref = len(result)
    result.extend(f"xref\n0 {len(offsets)}\n0000000000 65535 f \n".encode())
    for offset in offsets[1:]:
        result.extend(f"{offset:010d} 00000 n \n".encode())
    result.extend(
        f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    )
    path.write_bytes(result)
    return {"width": image.width, "height": image.height, "mode": image.mode, "jpegBytes": len(jpeg)}


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    text_fixture()
    gray = Image.new("L", (1440, 2240), 255)
    drawing = ImageDraw.Draw(gray)
    for line in range(28):
        y = 80 + line * 72
        drawing.rectangle((80, y, 1360, y + 2), fill=40 + line % 5 * 20)
        drawing.text((90, y + 15), f"Synthetic scan line {line:02d}", fill=20)
    descriptions = {}
    for name, image, quality in (
        ("gray-q85.pdf", gray, 85),
        ("rgb-q85.pdf", gray.convert("RGB"), 85),
        ("rgb-q50.pdf", gray.convert("RGB"), 50),
        ("gray-half-q85.pdf", gray.resize((720, 1120)), 85),
    ):
        descriptions[name] = image_pdf(OUTPUT / name, image, quality)
    files = []
    for name in ("text-outline.pdf", "aes128.pdf", "aes256.pdf", *descriptions):
        path = OUTPUT / name
        entry = {
            "file": name,
            "bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        }
        entry.update(descriptions.get(name, {}))
        files.append(entry)
    manifest = {
        "kind": "synthetic fixtures only; not user document measurements",
        "generators": {"Pillow": PIL.__version__, "pypdf": pypdf.__version__, "reportlab": reportlab.Version},
        "expected": {"textSearchMatches": 2, "outlineLevels": 2, "encryptedAlgorithms": ["AES-128", "AES-256"]},
        "files": files,
    }
    (OUTPUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"fixtureCount": len(files), "output": str(OUTPUT), "generators": manifest["generators"]}))


if __name__ == "__main__":
    main()
