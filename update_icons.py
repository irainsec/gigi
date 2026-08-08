
import requests
from PIL import Image, ImageOps, ImageDraw
import os

IMAGE_URL = "https://www.iamanraj.com/images/extras/me.png"
APP_DIR = r"app/src/main/res"

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

# Adaptive Icon Sizes (Foreground)
# Base MDPI: 108x108 total, 72x72 safe zone
ADAPTIVE_SIZES = {
    "mipmap-mdpi": (108, 72),
    "mipmap-hdpi": (162, 108),
    "mipmap-xhdpi": (216, 144),
    "mipmap-xxhdpi": (324, 216),
    "mipmap-xxxhdpi": (432, 288)
}

def download_image(url, save_path):
    response = requests.get(url, stream=True)
    if response.status_code == 200:
        with open(save_path, 'wb') as f:
            for chunk in response.iter_content(1024):
                f.write(chunk)
        return True
    return False

def make_circle(img):
    mask = Image.new('L', img.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0) + img.size, fill=255)
    output = ImageOps.fit(img, mask.size, centering=(0.5, 0.5))
    output.putalpha(mask)
    return output

def update_adaptive_xml():
    xml_dir = os.path.join(APP_DIR, "mipmap-anydpi-v26")
    files = ["ic_launcher.xml", "ic_launcher_round.xml"]
    
    new_content = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>"""

    for f in files:
        path = os.path.join(xml_dir, f)
        target_dir = os.path.dirname(path)
        os.makedirs(target_dir, exist_ok=True)
        with open(path, "w") as xml_file:
            xml_file.write(new_content)
        print(f"Updated {path}")

def process_icons():
    temp_img = "temp_icon.png"
    print(f"Downloading {IMAGE_URL}...")
    if not download_image(IMAGE_URL, temp_img):
        print("Failed to download image.")
        return

    original = Image.open(temp_img).convert("RGBA")
    
    # Create Circular Version first
    circular = make_circle(original.copy())

    for folder, size in SIZES.items():
        target_dir = os.path.join(APP_DIR, folder)
        os.makedirs(target_dir, exist_ok=True)
        
        # 1. Legacy Icons (Full Square/Circle)
        resized = circular.resize((size, size), Image.Resampling.LANCZOS)
        
        ic_launcher_path = os.path.join(target_dir, "ic_launcher.png")
        resized.save(ic_launcher_path, "PNG")
        print(f"Saved {ic_launcher_path}")
        
        ic_launcher_round_path = os.path.join(target_dir, "ic_launcher_round.png")
        resized.save(ic_launcher_round_path, "PNG")
        print(f"Saved {ic_launcher_round_path}")

        # 2. Adaptive Foregrounds
        if folder in ADAPTIVE_SIZES:
            total_size, safe_size = ADAPTIVE_SIZES[folder]
            
            # Create a transparent background for foreground layer
            foreground = Image.new("RGBA", (total_size, total_size), (0, 0, 0, 0))
            
            # Resize content to the SAFE size
            content = circular.resize((safe_size, safe_size), Image.Resampling.LANCZOS)
            
            # Paste in center
            offset = (total_size - safe_size) // 2
            foreground.paste(content, (offset, offset), content)
            
            fg_path = os.path.join(target_dir, "ic_launcher_foreground.png")
            foreground.save(fg_path, "PNG")
            print(f"Saved {fg_path}")

    # Remove conflicting XML drawable
    xml_foreground = os.path.join(APP_DIR, "drawable", "ic_launcher_foreground.xml")
    if os.path.exists(xml_foreground):
        os.remove(xml_foreground)
        print("Removed conflicting ic_launcher_foreground.xml")

    # Update Adaptive XMLs
    update_adaptive_xml()

    # Clean up
    if os.path.exists(temp_img):
        os.remove(temp_img)
    print("Icon update complete!")

if __name__ == "__main__":
    process_icons()
