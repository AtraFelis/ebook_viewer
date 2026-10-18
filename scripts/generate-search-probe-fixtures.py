"""Small deterministic synthetic PDFs; standard library only, no user input."""

import hashlib
import json
import zlib
from pathlib import Path


OUTPUT = Path(__file__).resolve().parents[1] / "local-artifacts/search-probe-fixtures"


def generate(kind: str, count: int) -> dict:
    objects = [b"", b"", b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    pages = []
    for index in range(count):
        page_number = len(objects) + 1
        content_number = page_number + 1
        pages.append(page_number)
        resources = b"/Font << /F0 3 0 R >>"
        if kind == "images":
            image_number = page_number + 2
            resources += f" /XObject << /Im0 {image_number} 0 R >>".encode()
            content = b"q 300 0 0 400 20 20 cm /Im0 Do Q"
        else:
            line = "needle alpha needle" if kind == "text" else "needle " * 20
            rows = 1 if kind == "text" else 10
            content = f"BT /F0 {4 if kind == 'dense' else 8} Tf 20 500 Td 12 TL\n".encode()
            content += b"\n".join(f"({line}) Tj T*".encode() for _ in range(rows))
            content += b"\nET"
        objects.append(
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 360 540] /Resources << "
            + resources + f" >> /Contents {content_number} 0 R >>".encode()
        )
        compressed = zlib.compress(content)
        objects.append(
            f"<< /Filter /FlateDecode /Length {len(compressed)} >>\nstream\n".encode()
            + compressed + b"\nendstream"
        )
        if kind == "images":
            # Only 3 KiB decoded per unique image, even across 1,000 pages.
            pixels = bytes((index + value) % 256 for value in range(32 * 32 * 3))
            image = zlib.compress(pixels)
            objects.append(
                b"<< /Type /XObject /Subtype /Image /Width 32 /Height 32 "
                b"/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode "
                + f"/Length {len(image)} >>\nstream\n".encode() + image + b"\nendstream"
            )
    objects[0] = b"<< /Type /Catalog /Pages 2 0 R >>"
    objects[1] = (
        f"<< /Type /Pages /Count {count} /Kids [".encode()
        + b" ".join(f"{number} 0 R".encode() for number in pages) + b"] >>"
    )
    data = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = [0]
    for number, value in enumerate(objects, 1):
        offsets.append(len(data))
        data.extend(f"{number} 0 obj\n".encode() + value + b"\nendobj\n")
    xref = len(data)
    data.extend(f"xref\n0 {len(offsets)}\n0000000000 65535 f \n".encode())
    for offset in offsets[1:]:
        data.extend(f"{offset:010d} 00000 n \n".encode())
    data.extend(
        f"trailer\n<< /Root 1 0 R /Size {len(offsets)} >>\nstartxref\n{xref}\n%%EOF\n".encode()
    )
    assert len(data) < 8 * 1024 * 1024
    name = f"{kind}-{count}.pdf"
    (OUTPUT / name).write_bytes(data)
    return {
        "file": name, "pages": count, "bytes": len(data),
        "matches": count * (2 if kind == "text" else 200 if kind == "dense" else 0),
        "decodedImageBytes": count * 3072 if kind == "images" else 0,
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    files = [generate("text", 1000), generate("images", 430),
             generate("images", 1000), generate("dense", 100)]
    manifest = {"kind": "bounded synthetic diagnostics; no private PDF", "files": files}
    (OUTPUT / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(manifest))


if __name__ == "__main__":
    main()
