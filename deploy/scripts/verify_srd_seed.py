"""Verify the bundled SRD text against the pinned Creative Commons SRD PDF.

Requires pypdf. Pass a local copy of the official PDF; no network is used.
"""

import argparse
import hashlib
import json
import re
import unicodedata
from pathlib import Path

from pypdf import PdfReader


ROOT = Path(__file__).resolve().parents[2]
CONTENT = ROOT / "app/src/main/resources/content"


def normalize(text):
    text = "".join(char for char in text if unicodedata.category(char) != "Cf")
    text = re.sub(r"[\u2010\u2011\u2212]+", "-", text)
    return " ".join(text.split()).translate(str.maketrans("\u2018\u2019\u201c\u201d\u2212", "''\"\"-"))


def extract(pdf, page, heading, next_heading):
    text = pdf.pages[page - 1].extract_text()
    match = re.search(
        rf"(?ms)^{re.escape(heading)}[ \t]*\n(.*?)^{re.escape(next_heading)}[ \t]*$", text
    )
    if not match:
        raise ValueError("The expected source headings are missing")
    return [" ".join(part.split()) for part in match.group(1).split("\u2022") if part.strip()]


def verify(pdf_path):
    index = json.loads((CONTENT / "srd-5.1-source-index.json").read_text(encoding="utf-8"))
    if hashlib.sha256(pdf_path.read_bytes()).hexdigest() != index["pdfSha256"]:
        raise ValueError("The PDF does not match the pinned Creative Commons source")
    reader = PdfReader(pdf_path)
    for span in index["spans"]:
        if "flatStart" in span or "segments" in span:
            parts = []
            for segment in span.get("segments", [span]):
                page = " ".join(reader.pages[segment["pdfPage"] - 1].extract_text().split())
                parts.append(page[segment["flatStart"]:segment["flatEnd"]])
        else:
            parts = extract(reader, span["pdfPage"], span["heading"], span["nextHeading"])
        source = normalize(" ".join(parts))
        if hashlib.sha256(source.encode()).hexdigest() != span["sha256"]:
            raise ValueError(f"Source span changed for {span['key']}")
        path = CONTENT / "srd-5.1" / span["textFile"]
        authored = normalize(re.sub(r"(?m)^- ", "", path.read_text(encoding="utf-8")))
        if authored != source:
            for expected, actual in zip(source.split(), authored.split()):
                if actual != expected:
                    raise ValueError(f"{span['key']} differs: expected {expected!r}, got {actual!r}")
            raise ValueError(f"{span['key']} has a different number of words")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pdf", type=Path)
    verify(parser.parse_args().pdf)
    print("The bundled SRD seed matches its indexed Creative Commons source span.")
