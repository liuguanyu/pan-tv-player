#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Android App Icon Generator
Generate different resolution mipmap icons and adaptive icons from source icon file
"""

from PIL import Image, ImageDraw
import os

# Define Android app icon sizes
ICON_SIZES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

# Adaptive icon sizes (108dp base)
ADAPTIVE_ICON_SIZES = {
    'mdpi': 108,
    'hdpi': 162,
    'xhdpi': 216,
    'xxhdpi': 324,
    'xxxhdpi': 432
}

# Android TV Banner size
BANNER_SIZE = (320, 180)

def crop_to_content(img, threshold=220):
    """
    Crop image to content, removing light-colored borders
    
    Args:
        img: PIL Image object
        threshold: Pixel brightness threshold to consider as "light" (0-255)
    
    Returns:
        Cropped PIL Image object
    """
    # Convert to RGB for analysis
    if img.mode == 'RGBA':
        # Create a white background
        bg = Image.new('RGB', img.size, (255, 255, 255))
        bg.paste(img, mask=img.split()[3])
        img_rgb = bg
    else:
        img_rgb = img.convert('RGB')
    
    # Get image data
    pixels = img_rgb.load()
    width, height = img_rgb.size
    
    # Find bounds of non-light content
    left = width
    top = height
    right = 0
    bottom = 0
    
    for y in range(height):
        for x in range(width):
            r, g, b = pixels[x, y]
            # Calculate brightness (simple average)
            brightness = (r + g + b) / 3
            # If pixel is not light (below threshold)
            if brightness < threshold:
                left = min(left, x)
                top = min(top, y)
                right = max(right, x)
                bottom = max(bottom, y)
    
    # Add a tiny bit of padding to avoid cutting off anti-aliased edges
    padding = 2
    left = max(0, left - padding)
    top = max(0, top - padding)
    right = min(width, right + padding)
    bottom = min(height, bottom + padding)
    
    # Crop the image
    if left < right and top < bottom:
        return img.crop((left, top, right, bottom))
    else:
        return img

def generate_adaptive_icons(source_image_path, output_base_dir):
    """
    Generate adaptive icons (foreground and background layers)
    
    Args:
        source_image_path: Source icon file path
        output_base_dir: Output base directory
    """
    try:
        # Open source icon
        print(f"Opening source icon for adaptive icons: {source_image_path}")
        img = Image.open(source_image_path)
        
        # Convert to RGBA mode if not already
        if img.mode != 'RGBA':
            img = img.convert('RGBA')
        
        print(f"Source icon size: {img.size}")
        
        # Crop to content (remove white borders)
        img_content = crop_to_content(img)
        print(f"Cropped to content: {img_content.size}")
        
        # Resize content to square if needed (to ensure it fits)
        width, height = img_content.size
        max_dim = max(width, height)
        
        # Create a square canvas with the dominant background color (black/dark grey from the icon)
        # We sample the corner of the content to get the background color
        bg_color = img_content.getpixel((width//2, height//10)) # Sample top center inside the shape
        # Actually, let's just use the dark color we saw earlier: (30, 30, 30) roughly
        # Or better, just transparent for foreground
        
        # Generate adaptive icons for each density
        for density, size in ADAPTIVE_ICON_SIZES.items():
            output_dir = os.path.join(output_base_dir, f'mipmap-{density}')
            
            # Create output directory if not exists
            os.makedirs(output_dir, exist_ok=True)
            
            # 1. Background Layer: Dark color matching the icon background
            # The icon seems to be dark grey/black. Let's use a solid color.
            # Based on previous sampling: (36, 39, 50) is a common dark color in center
            # But let's use a standard dark background color for adaptive icon background
            bg_color = (30, 30, 30, 255)
            bg_img = Image.new('RGBA', (size, size), bg_color)
            bg_path = os.path.join(output_dir, 'ic_launcher_background.png')
            bg_img.save(bg_path, 'PNG', optimize=True)
            print(f"Generated {density} background: {bg_path} ({size}x{size})")
            
            # 2. Foreground Layer: The logo content
            # For adaptive icons, the foreground should be smaller than the full size (about 66%)
            # to allow for safe zone masking.
            fg_size = size
            fg_img = Image.new('RGBA', (fg_size, fg_size), (0, 0, 0, 0))
            
            # Calculate safe content size (about 70% of full size)
            content_size = int(size * 0.75)
            
            # Resize content to fit in safe area
            img_resized = img_content.resize((content_size, content_size), Image.Resampling.LANCZOS)
            
            # Paste in center
            paste_x = (fg_size - content_size) // 2
            paste_y = (fg_size - content_size) // 2
            fg_img.paste(img_resized, (paste_x, paste_y))
            
            fg_path = os.path.join(output_dir, 'ic_launcher_foreground.png')
            fg_img.save(fg_path, 'PNG', optimize=True)
            print(f"Generated {density} foreground: {fg_path} ({size}x{size})")
        
        print("\n[SUCCESS] All adaptive icons generated successfully!")
        return True
        
    except Exception as e:
        print(f"[ERROR] Failed to generate adaptive icons: {str(e)}")
        return False

def generate_icons(source_image_path, output_base_dir):
    """
    Generate app icons in various sizes from source icon
    
    Args:
        source_image_path: Source icon file path
        output_base_dir: Output base directory
    """
    try:
        # Open source icon
        print(f"Opening source icon: {source_image_path}")
        img = Image.open(source_image_path)
        
        # Convert to RGBA mode if not already
        if img.mode != 'RGBA':
            img = img.convert('RGBA')
        
        print(f"Source icon size: {img.size}")
        
        # Crop to content (remove white borders)
        img_content = crop_to_content(img)
        print(f"Cropped to content: {img_content.size}")
        
        # Resize to square (if not already)
        width, height = img_content.size
        if width != height:
            # If not square, paste onto a square transparent background?
            # Or just crop to center? The logo seems to be square content.
            # Let's crop to square from center to be safe, assuming the content is centered.
            min_dim = min(width, height)
            left = (width - min_dim) // 2
            top = (height - min_dim) // 2
            right = left + min_dim
            bottom = top + min_dim
            img_content = img_content.crop((left, top, right, bottom))
        
        # Generate icons for each density
        for density, size in ICON_SIZES.items():
            output_dir = os.path.join(output_base_dir, f'mipmap-{density}')
            
            # Create output directory if not exists
            os.makedirs(output_dir, exist_ok=True)
            
            # Resize icon
            resized_img = img_content.resize((size, size), Image.Resampling.LANCZOS)
            
            # Save icon
            output_path = os.path.join(output_dir, 'ic_launcher.png')
            resized_img.save(output_path, 'PNG', optimize=True)
            
            print(f"Generated {density} icon: {output_path} ({size}x{size})")
        
        print("\n[SUCCESS] All icons generated successfully!")
        return True
        
    except Exception as e:
        print(f"[ERROR] Failed to generate icons: {str(e)}")
        return False

def generate_banner(source_image_path, output_base_dir):
    """
    Generate Android TV banner (320x180)
    
    Args:
        source_image_path: Source icon file path
        output_base_dir: Output base directory
    """
    try:
        # Open source icon
        print(f"Opening source icon for TV banner: {source_image_path}")
        img = Image.open(source_image_path)
        
        # Convert to RGBA mode if not already
        if img.mode != 'RGBA':
            img = img.convert('RGBA')
        
        print(f"Source icon size: {img.size}")
        
        # Crop to content (remove white borders)
        img_content = crop_to_content(img)
        print(f"Cropped to content: {img_content.size}")
        
        # Create banner with dark background
        banner_width, banner_height = BANNER_SIZE
        banner_img = Image.new('RGBA', BANNER_SIZE, (30, 30, 30, 255))
        
        # Calculate size to fit the logo (maintain aspect ratio)
        # Use 80% of banner height to leave some padding
        target_height = int(banner_height * 0.8)
        content_width, content_height = img_content.size
        aspect_ratio = content_width / content_height
        target_width = int(target_height * aspect_ratio)
        
        # If too wide, scale by width instead
        if target_width > banner_width * 0.9:
            target_width = int(banner_width * 0.9)
            target_height = int(target_width / aspect_ratio)
        
        # Resize logo
        img_resized = img_content.resize((target_width, target_height), Image.Resampling.LANCZOS)
        
        # Paste in center
        paste_x = (banner_width - target_width) // 2
        paste_y = (banner_height - target_height) // 2
        banner_img.paste(img_resized, (paste_x, paste_y), img_resized)
        
        # Save banner to drawable-xhdpi (standard TV density)
        output_dir = os.path.join(output_base_dir, 'drawable-xhdpi')
        os.makedirs(output_dir, exist_ok=True)
        
        banner_path = os.path.join(output_dir, 'banner.png')
        banner_img.save(banner_path, 'PNG', optimize=True)
        
        print(f"Generated TV banner: {banner_path} ({banner_width}x{banner_height})")
        print("\n[SUCCESS] TV banner generated successfully!")
        return True
        
    except Exception as e:
        print(f"[ERROR] Failed to generate TV banner: {str(e)}")
        return False

def main():
    # Set paths
    source_icon = 'icon.png'
    output_dir = 'app/src/main/res'
    
    # Check if source file exists
    if not os.path.exists(source_icon):
        print(f"[ERROR] Source icon file not found: {source_icon}")
        return False
    
    # Generate icons
    print("=" * 60)
    print("Starting Android App Icon Generation")
    print("=" * 60)
    
    # Generate regular icons
    print("\nGenerating regular icons...")
    success1 = generate_icons(source_icon, output_dir)
    
    # Generate adaptive icons
    print("\nGenerating adaptive icons...")
    success2 = generate_adaptive_icons(source_icon, output_dir)
    
    # Generate TV banner
    print("\nGenerating Android TV banner...")
    success3 = generate_banner(source_icon, output_dir)
    
    if success1 and success2 and success3:
        print("\n" + "=" * 60)
        print("Icon Generation Complete!")
        print("=" * 60)
        print("\nGenerated icon locations:")
        print("\nRegular icons:")
        for density in ICON_SIZES.keys():
            path = f"{output_dir}/mipmap-{density}/ic_launcher.png"
            print(f"  - {path}")
        print("\nAdaptive icons:")
        for density in ADAPTIVE_ICON_SIZES.keys():
            bg_path = f"{output_dir}/mipmap-{density}/ic_launcher_background.png"
            fg_path = f"{output_dir}/mipmap-{density}/ic_launcher_foreground.png"
            print(f"  - {bg_path}")
            print(f"  - {fg_path}")
        print("\nAndroid TV banner:")
        print(f"  - {output_dir}/drawable-xhdpi/banner.png")
    
    return success1 and success2 and success3

if __name__ == '__main__':
    main()