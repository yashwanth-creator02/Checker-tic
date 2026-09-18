"""
Master generator for the Flip Copper Icon:
- Generates 512x512 and 1024x1024 master assets in docs/brand/
- Generates all mipmap WebP files for Android (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- Generates adaptive foreground (ic_launcher_foreground.png) and background (ic_launcher_background.xml)
- Generates in-app vector logo and adaptive vector XML
"""

import os
import math
from PIL import Image, ImageDraw, ImageFilter
import numpy as np

BASE_DIR = r"c:\Users\yashw\Documents\GitHub\android_studio\Checker-Tic"
RES_DIR = os.path.join(BASE_DIR, "app", "src", "main", "res")
BRAND_DIR = os.path.join(BASE_DIR, "docs", "brand")

os.makedirs(BRAND_DIR, exist_ok=True)

CANVAS_SIZE = 2048

def draw_capsule(draw, p1, p2, radius, fill):
    x1, y1 = p1
    x2, y2 = p2
    dx, dy = x2 - x1, y2 - y1
    dist = math.hypot(dx, dy)
    if dist == 0:
        draw.ellipse([x1 - radius, y1 - radius, x1 + radius, y1 + radius], fill=fill)
        return
    ux, uy = -dy / dist, dx / dist
    poly = [
        (x1 + ux * radius, y1 + uy * radius),
        (x2 + ux * radius, y2 + uy * radius),
        (x2 - ux * radius, y2 - uy * radius),
        (x1 - ux * radius, y1 - uy * radius)
    ]
    draw.polygon(poly, fill=fill)
    draw.ellipse([x1 - radius, y1 - radius, x1 + radius, y1 + radius], fill=fill)
    draw.ellipse([x2 - radius, y2 - radius, x2 + radius, y2 + radius], fill=fill)

def render_cards_layer():
    """Renders the two cards (notes and tasks) on a transparent 2048x2048 canvas."""
    cards_img = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))

    card_w, card_h = 960, 1180
    card_r = 130
    
    # Back Card: Notes (-10 deg)
    back_card = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
    bc_draw = ImageDraw.Draw(back_card)
    
    # Soft warm porcelain back card
    bc_draw.rounded_rectangle([0, 0, card_w - 1, card_h - 1], radius=card_r, 
                               fill=(244, 234, 227, 255), 
                               outline=(228, 214, 204, 255), width=10)
    
    # Satin copper note lines: #7C4831
    copper_color = (124, 72, 49, 255)
    line_x = 120
    line_h = 62
    line_r = 31
    bc_draw.rounded_rectangle([line_x, 370, line_x + 500, 370 + line_h], radius=line_r, fill=copper_color)
    bc_draw.rounded_rectangle([line_x, 520, line_x + 680, 520 + line_h], radius=line_r, fill=copper_color)
    bc_draw.rounded_rectangle([line_x, 670, line_x + 390, 670 + line_h], radius=line_r, fill=copper_color)

    back_rot = back_card.rotate(-10, resample=Image.BICUBIC, expand=True)
    
    # Back card shadow
    bc_shadow = Image.new("RGBA", back_rot.size, (0, 0, 0, 0))
    bc_shadow.paste((0, 0, 0, 95), (0, 0), back_rot.split()[3])
    bc_shadow = bc_shadow.filter(ImageFilter.GaussianBlur(60))
    
    bc_x, bc_y = 420, 500
    cards_img.alpha_composite(bc_shadow, (bc_x - 25, bc_y + 40))
    cards_img.alpha_composite(back_rot, (bc_x, bc_y))

    # Front Card: Tasks (+8 deg)
    front_card = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
    fc_draw = ImageDraw.Draw(front_card)
    fc_draw.rounded_rectangle([0, 0, card_w - 1, card_h - 1], radius=card_r, 
                               fill=(255, 255, 255, 255), 
                               outline=(248, 244, 240, 255), width=10)
    
    # Smooth checkmark in satin copper
    p1 = (195, 630)
    p2 = (400, 840)
    p3 = (760, 390)
    tick_rad = 56
    draw_capsule(fc_draw, p1, p2, tick_rad, copper_color)
    draw_capsule(fc_draw, p2, p3, tick_rad, copper_color)

    front_rot = front_card.rotate(8, resample=Image.BICUBIC, expand=True)

    # Deep shadow between front and back card
    fc_shadow = Image.new("RGBA", front_rot.size, (0, 0, 0, 0))
    fc_shadow.paste((0, 0, 0, 150), (0, 0), front_rot.split()[3])
    fc_shadow = fc_shadow.filter(ImageFilter.GaussianBlur(70))

    fc_x, fc_y = 740, 460
    cards_img.alpha_composite(fc_shadow, (fc_x - 35, fc_y + 50))
    cards_img.alpha_composite(front_rot, (fc_x, fc_y))

    # Center cards mathematically onto (CANVAS_SIZE/2, CANVAS_SIZE/2)
    arr = np.array(cards_img)
    alpha = arr[:, :, 3]
    ys, xs = np.where(alpha > 20)
    if len(xs) > 0 and len(ys) > 0:
        cx = (xs.min() + xs.max()) / 2.0
        cy = (ys.min() + ys.max()) / 2.0
        dx = int(round((CANVAS_SIZE / 2.0) - cx))
        dy = int(round((CANVAS_SIZE / 2.0) - cy))
        
        centered_cards = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
        centered_cards.paste(cards_img, (dx, dy))
        return centered_cards

    return cards_img

def build_full_icon(cards_layer, is_round=False):
    canvas = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
    margin = 120
    box = [margin, margin, CANVAS_SIZE - margin, CANVAS_SIZE - margin]
    radius = 440

    # Background fill: satin copper with subtle top warmth gradient
    bg = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
    bg_draw = ImageDraw.Draw(bg)
    
    # Subtle vertical gradient from #834E35 (131, 78, 53) to #74432D (116, 67, 45)
    for y in range(margin, CANVAS_SIZE - margin):
        ratio = (y - margin) / float(CANVAS_SIZE - 2 * margin)
        r = int(131 - ratio * 15)
        g = int(78 - ratio * 11)
        b = int(53 - ratio * 8)
        bg_draw.line([(margin, y), (CANVAS_SIZE - margin, y)], fill=(r, g, b, 255))
        
    bg_mask = Image.new("L", (CANVAS_SIZE, CANVAS_SIZE), 0)
    if is_round:
        ImageDraw.Draw(bg_mask).ellipse([margin, margin, CANVAS_SIZE - margin, CANVAS_SIZE - margin], fill=255)
    else:
        ImageDraw.Draw(bg_mask).rounded_rectangle(box, radius=radius, fill=255)
        
    canvas.paste(bg, (0, 0), bg_mask)
    
    # Composite cards
    canvas.alpha_composite(cards_layer)
    
    if is_round:
        round_mask = Image.new("L", (CANVAS_SIZE, CANVAS_SIZE), 0)
        ImageDraw.Draw(round_mask).ellipse([margin, margin, CANVAS_SIZE - margin, CANVAS_SIZE - margin], fill=255)
        out = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
        out.paste(canvas, (0, 0), round_mask)
        # Outer copper accent ring
        b_draw = ImageDraw.Draw(out)
        b_draw.ellipse([margin, margin, CANVAS_SIZE - margin, CANVAS_SIZE - margin],
                       outline=(170, 110, 85, 120), width=10)
        return out

    return canvas

def build_adaptive_foreground(cards_layer):
    """
    Android Adaptive icon foreground:
    Canvas size: 432x432 (xxxhdpi, 108dp).
    Safe zone: central 288x288 circle (72dp).
    The cards are scaled to fit comfortably inside the safe zone (diameter ~260px).
    """
    ADAPTIVE_PX = 432
    fg = Image.new("RGBA", (ADAPTIVE_PX, ADAPTIVE_PX), (0, 0, 0, 0))
    
    # Cards layer in 2048x2048 has content from x: 400..1700, y: 450..1700 (width ~ 1300)
    # Scale 2048 down to ~390 and center so the cards sit inside the 288px safe circle!
    scaled_cards = cards_layer.resize((380, 380), Image.Resampling.LANCZOS)
    offset = (ADAPTIVE_PX - 380) // 2
    fg.alpha_composite(scaled_cards, (offset, offset))
    return fg

if __name__ == "__main__":
    print("Generating cards layer...")
    cards = render_cards_layer()
    
    # 1. Master previews
    squircle_master = build_full_icon(cards, is_round=False)
    round_master = build_full_icon(cards, is_round=True)
    
    master_512 = squircle_master.resize((512, 512), Image.Resampling.LANCZOS)
    master_512.save(os.path.join(BRAND_DIR, "flip_icon_512.png"))
    
    round_512 = round_master.resize((512, 512), Image.Resampling.LANCZOS)
    round_512.save(os.path.join(BRAND_DIR, "flip_icon_round_512.png"))
    
    master_1024 = squircle_master.resize((1024, 1024), Image.Resampling.LANCZOS)
    master_1024.save(os.path.join(BRAND_DIR, "flip_icon_master_1024.png"))
    print("Saved master previews in docs/brand/")

    # 2. Adaptive Foreground
    adaptive_fg = build_adaptive_foreground(cards)
    adaptive_fg_path = os.path.join(RES_DIR, "drawable", "ic_launcher_foreground.png")
    adaptive_fg.save(adaptive_fg_path, "PNG")
    print("Saved adaptive foreground:", adaptive_fg_path)

    # 3. Adaptive Background XML (#7C4831 satin copper)
    bg_xml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#7C4831"
        android:pathData="M0,0h108v108h-108z" />
</vector>
"""
    bg_xml_path = os.path.join(RES_DIR, "drawable", "ic_launcher_background.xml")
    with open(bg_xml_path, "w", encoding="utf-8") as f:
        f.write(bg_xml)
    print("Saved adaptive background XML:", bg_xml_path)

    # 4. Raster Mipmaps across all densities
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    for folder, size in densities.items():
        out_dir = os.path.join(RES_DIR, folder)
        os.makedirs(out_dir, exist_ok=True)
        
        sq_icon = squircle_master.resize((size, size), Image.Resampling.LANCZOS)
        sq_icon.save(os.path.join(out_dir, "ic_launcher.webp"), "WEBP", quality=100)
        
        r_icon = round_master.resize((size, size), Image.Resampling.LANCZOS)
        r_icon.save(os.path.join(out_dir, "ic_launcher_round.webp"), "WEBP", quality=100)
        print(f"Generated {folder}: {size}x{size}")

    # 5. In-App Brand Vector / Monogram: ic_flip_logo.xml
    # We can also save a high-res PNG for compose painter:
    logo_png = master_512.resize((128, 128), Image.Resampling.LANCZOS)
    logo_png_path = os.path.join(RES_DIR, "drawable", "ic_flip_logo.png")
    logo_png.save(logo_png_path, "PNG")
    print("Saved in-app logo PNG:", logo_png_path)

    print("All Flip copper icon assets generated successfully!")
