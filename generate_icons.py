import os
from PIL import Image

def generate_icons(source_path, base_res_path):
    print(f"🎨 Starting icon generation from: {source_path}")
    
    # 1. Load and prepare source image (Crop symbol from concept)
    img = Image.open(source_path)
    width, height = img.size
    
    # The symbol is in the top part of the image. 
    # Let's crop a square area centered horizontally, starting from top.
    # For gigi_logo_v1, the text is at the bottom.
    # We'll take a square of size (width) starting from Y=0.
    left = 0
    top = 0
    right = width
    bottom = width # Square crop
    
    symbol_img = img.crop((left, top, right, bottom))
    
    # Save the base symbol for reference
    drawable_path = os.path.join(base_res_path, 'drawable', 'gigi_symbol_only.png')
    os.makedirs(os.path.dirname(drawable_path), exist_ok=True)
    symbol_img.save(drawable_path)
    print(f"✅ Saved base symbol to: {drawable_path}")

    # 2. Define target sizes (Android Launcher Icons)
    icon_configs = [
        ('mipmap-mdpi', 48),
        ('mipmap-hdpi', 72),
        ('mipmap-xhdpi', 96),
        ('mipmap-xxhdpi', 144),
        ('mipmap-xxxhdpi', 192)
    ]

    for folder, size in icon_configs:
        target_dir = os.path.join(base_res_path, folder)
        os.makedirs(target_dir, exist_ok=True)
        
        # 1. Standard Launcher Icon
        symbol_img.resize((size, size), Image.Resampling.LANCZOS).save(os.path.join(target_dir, 'ic_launcher.png'))
        
        # 2. Foreground (for Adaptive Icons)
        symbol_img.resize((size, size), Image.Resampling.LANCZOS).save(os.path.join(target_dir, 'ic_launcher_foreground.png'))
        
        # 3. Round Icon
        symbol_img.resize((size, size), Image.Resampling.LANCZOS).save(os.path.join(target_dir, 'ic_launcher_round.png'))
        
        print(f"🚀 Generated all icon variations for {folder} at {size}x{size}")

if __name__ == "__main__":
    SOURCE = r"C:\Users\ATPL-ADMIN\.gemini\antigravity\brain\9892a223-ab6c-4499-83aa-e7e348b235eb\gigi_logo_v1_1770802984285.png"
    RES_DIR = r"c:\Users\ATPL-ADMIN\OneDrive - AltiSec Technologies Pvt. Ltd\Desktop\amanraj\amanraj\app\src\main\res"
    
    try:
        generate_icons(SOURCE, RES_DIR)
        print("\n✨ All icons generated successfully!")
    except Exception as e:
        print(f"❌ Error: {e}")
