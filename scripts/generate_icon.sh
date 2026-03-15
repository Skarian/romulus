#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

source_image="${repo_root}/artwork/ROMULUS_TRANSPARENT.png"
background_color="$(sed -nE 's:.*name="romulus_brand_background">(#[0-9A-Fa-f]{6,8})<.*:\1:p' "${repo_root}/app/src/main/res/values/colors.xml" | head -n 1)"
padding=72
round_padding=144
splash_padding=220
preview_dir="${repo_root}/artwork/generated"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source)
      source_image="$2"
      shift 2
      ;;
    --background-color)
      if [[ -n "$2" ]]; then
        background_color="$2"
      fi
      shift 2
      ;;
    --padding)
      padding="$2"
      shift 2
      ;;
    --round-padding)
      round_padding="$2"
      shift 2
      ;;
    --splash-padding)
      splash_padding="$2"
      shift 2
      ;;
    --preview-dir)
      preview_dir="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "${source_image}" ]]; then
  echo "Source image not found: ${source_image}" >&2
  exit 1
fi

if [[ -z "${background_color}" ]]; then
  echo "Unable to resolve romulus_brand_background from app/src/main/res/values/colors.xml" >&2
  exit 1
fi

if ! command -v magick >/dev/null 2>&1; then
  echo "ImageMagick 'magick' is required but was not found." >&2
  exit 1
fi

icon_canvas=1024
content_size=$((icon_canvas - (padding * 2)))
round_content_size=$((icon_canvas - (round_padding * 2)))
splash_content_size=$((icon_canvas - (splash_padding * 2)))
rounded_corner_radius=224

mkdir -p \
  "${preview_dir}" \
  "${repo_root}/app/src/main/res/drawable-nodpi" \
  "${repo_root}/app/src/main/res/mipmap-mdpi" \
  "${repo_root}/app/src/main/res/mipmap-hdpi" \
  "${repo_root}/app/src/main/res/mipmap-xhdpi" \
  "${repo_root}/app/src/main/res/mipmap-xxhdpi" \
  "${repo_root}/app/src/main/res/mipmap-xxxhdpi"

preview_icon="${preview_dir}/icon-preview-1024.png"
mask_icon="${preview_dir}/icon-mask-1024.png"
round_preview_icon="${preview_dir}/icon-preview-round-1024.png"
round_mask_icon="${preview_dir}/icon-mask-round-1024.png"
splash_preview_icon="${preview_dir}/icon-preview-splash-1024.png"

magick -size "${icon_canvas}x${icon_canvas}" "xc:${background_color}" \
  \( "${source_image}" -resize "${content_size}x${content_size}" \) \
  -gravity center -composite \
  "${preview_icon}"

magick -size "${icon_canvas}x${icon_canvas}" xc:none \
  -fill white -draw "roundrectangle 24,24 1000,1000 ${rounded_corner_radius},${rounded_corner_radius}" \
  "${mask_icon}"

magick "${preview_icon}" "${mask_icon}" -alpha off -compose CopyOpacity -composite "${preview_icon}"
rm -f "${mask_icon}"

magick -size "${icon_canvas}x${icon_canvas}" "xc:${background_color}" \
  \( "${source_image}" -resize "${round_content_size}x${round_content_size}" \) \
  -gravity center -composite \
  "${round_preview_icon}"

magick -size "${icon_canvas}x${icon_canvas}" xc:none \
  -fill white -draw "circle 512,512 512,24" \
  "${round_mask_icon}"

magick "${round_preview_icon}" "${round_mask_icon}" -alpha off -compose CopyOpacity -composite "${round_preview_icon}"
rm -f "${round_mask_icon}"

declare -A android_sizes=(
  [mipmap-mdpi]=48
  [mipmap-hdpi]=72
  [mipmap-xhdpi]=96
  [mipmap-xxhdpi]=144
  [mipmap-xxxhdpi]=192
)

for density in "${!android_sizes[@]}"; do
  magick "${preview_icon}" -resize "${android_sizes[$density]}x${android_sizes[$density]}" \
    "${repo_root}/app/src/main/res/${density}/ic_launcher.png"
  magick "${round_preview_icon}" -resize "${android_sizes[$density]}x${android_sizes[$density]}" \
    "${repo_root}/app/src/main/res/${density}/ic_launcher_round.png"
done

magick -size "${icon_canvas}x${icon_canvas}" "xc:${background_color}" \
  \( "${source_image}" -resize "${splash_content_size}x${splash_content_size}" \) \
  -gravity center -composite \
  "${splash_preview_icon}"

cp "${splash_preview_icon}" "${repo_root}/app/src/main/res/drawable-nodpi/ic_splash_logo.png"

echo "Generated preview: ${preview_icon}"
echo "Generated round preview: ${round_preview_icon}"
echo "Generated splash preview: ${splash_preview_icon}"
echo "Generated splash icon: ${repo_root}/app/src/main/res/drawable-nodpi/ic_splash_logo.png"
echo "Generated Android launcher assets under app/src/main/res/mipmap-*/ic_launcher*.png"
