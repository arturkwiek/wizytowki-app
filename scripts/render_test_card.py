"""Renders a synthetic (fictional) business card photo for test-data/cards (#19).

Usage: python scripts/render_test_card.py OUT.jpg "line 1" "line 2" ...
A line starting with "#" is drawn large (name). Only fictional data – never real people.
"""
import sys
from PIL import Image, ImageDraw, ImageFont

FONT = "C:/Windows/Fonts/arial.ttf"
BOLD = "C:/Windows/Fonts/arialbd.ttf"

def main(out, lines):
    img = Image.new("RGB", (1700, 1000), "white")
    draw = ImageDraw.Draw(img)
    y = 110
    for line in lines:
        big = line.startswith("#")
        text = line.lstrip("#")
        font = ImageFont.truetype(BOLD if big else FONT, 88 if big else 56)
        draw.text((110, y), text, fill="black", font=font)
        y += 140 if big else 100
    img.save(out, quality=92)

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
