#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CAPTURE_ROOT="${CAPTURE_ROOT:-$ROOT_DIR/screenshots}"
SESSION_NAME="${SESSION_NAME:-}"

if [[ -z "$SESSION_NAME" ]]; then
  SESSION_NAME="$(date +%Y-%m-%d_%H-%M)"
fi

OUTPUT_DIR="$CAPTURE_ROOT/$SESSION_NAME"

resolve_device_serial() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    printf '%s\n' "$ANDROID_SERIAL"
    return
  fi

  mapfile -t devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')

  if [[ "${#devices[@]}" -eq 1 ]]; then
    printf '%s\n' "${devices[0]}"
    return
  fi

  if [[ "${#devices[@]}" -eq 0 ]]; then
    printf 'No connected adb devices found. Pair or connect a device first.\n' >&2
    exit 1
  fi

  printf 'Multiple adb devices found. Set ANDROID_SERIAL to choose one:\n' >&2
  printf '  %s\n' "${devices[@]}" >&2
  exit 1
}

run_capture() {
  local device_serial="$1"
  local output_path="$2"
  adb -s "$device_serial" exec-out screencap -p > "$output_path"
  halve_image_dimensions "$output_path"
}

halve_image_dimensions() {
  local image_path="$1"
  local width
  local target_width

  width="$(
    sips -g pixelWidth "$image_path" 2>/dev/null |
      awk '/pixelWidth:/ { print $2 }'
  )"

  if [[ -z "$width" || ! "$width" =~ ^[0-9]+$ ]]; then
    printf 'Failed to read screenshot width for %s\n' "$image_path" >&2
    exit 1
  fi

  target_width=$((width / 2))
  if [[ "$target_width" -lt 1 ]]; then
    target_width=1
  fi

  sips --resampleWidth "$target_width" "$image_path" >/dev/null
}

clear_app_data() {
  local device_serial="$1"
  printf 'Resetting app state via just clear-app-data...\n'
  ANDROID_SERIAL="$device_serial" just --justfile "$ROOT_DIR/justfile" clear-app-data
}

confirm_clear_app_data() {
  while true; do
    printf 'Setup capture needs a reset. Press Enter to run just clear-app-data, s to skip reset, or q to quit: '
    IFS= read -r action
    case "$action" in
      "") return 0 ;;
      s|S) return 1 ;;
      q|Q) exit 0 ;;
      *) printf 'Press Enter to reset, s to skip reset, or q to quit.\n' ;;
    esac
  done
}

wait_for_action() {
  local prompt="$1"

  while true; do
    printf '%s' "$prompt" >&2
    IFS= read -r action
    case "$action" in
      "") printf 'capture\n'; return ;;
      s|S) printf 'skip\n'; return ;;
      q|Q) printf 'quit\n'; return ;;
      *) printf 'Press Enter to capture, s to skip, or q to quit.\n' >&2 ;;
    esac
  done
}

confirm_capture() {
  local output_path="$1"
  local final_step="${2:-false}"

  while true; do
    printf 'Saved %s\n' "$output_path"
    if [[ "$final_step" == "true" ]]; then
      printf 'Press Enter to finish, r to retake, or q to quit: '
    else
      printf 'Press Enter for the next step, r to retake, or q to quit: '
    fi
    IFS= read -r action
    case "$action" in
      "") return 0 ;;
      r|R) return 1 ;;
      q|Q) exit 0 ;;
      *) printf 'Press Enter to continue, r to retake, or q to quit.\n' ;;
    esac
  done
}

mkdir -p "$OUTPUT_DIR"
DEVICE_SERIAL="$(resolve_device_serial)"

STEPS=(
  "light/setup/page|Set the phone to light mode. Open the Setup page."
  "light/home/page|Stay in light mode. Go to the Home tab."
  "light/home/search|On the Home tab in light mode, open the search modal."
  "light/home/details|On the Home tab in light mode, open the source details modal."
  "light/files/page|In light mode, open a source and stop on the Files page."
  "light/files/search|On the Files page in light mode, open the search modal."
  "light/files/preferences|On the Files page in light mode, open the file preferences modal."
  "light/files/details|On the Files page in light mode, open the file details modal."
  "light/downloads/page|In light mode, go to the Downloads tab."
  "light/downloads/menu|On the Downloads tab in light mode, open a row actions menu."
  "light/downloads/details|On the Downloads tab in light mode, open the download details modal."
  "light/downloads/clear-history|On the Downloads tab in light mode, open the clear history modal."
  "light/settings/page|In light mode, go to the Settings tab."
  "light/settings/clear-diagnostics|On the Settings tab in light mode, open the clear diagnostics modal."
  "dark/setup/page|Switch the phone to dark mode. Open the Setup page."
  "dark/home/page|Stay in dark mode. Go to the Home tab."
  "dark/home/search|On the Home tab in dark mode, open the search modal."
  "dark/home/details|On the Home tab in dark mode, open the source details modal."
  "dark/files/page|In dark mode, open a source and stop on the Files page."
  "dark/files/search|On the Files page in dark mode, open the search modal."
  "dark/files/preferences|On the Files page in dark mode, open the file preferences modal."
  "dark/files/details|On the Files page in dark mode, open the file details modal."
  "dark/downloads/page|In dark mode, go to the Downloads tab."
  "dark/downloads/menu|On the Downloads tab in dark mode, open a row actions menu."
  "dark/downloads/details|On the Downloads tab in dark mode, open the download details modal."
  "dark/downloads/clear-history|On the Downloads tab in dark mode, open the clear history modal."
  "dark/settings/page|In dark mode, go to the Settings tab."
  "dark/settings/clear-diagnostics|On the Settings tab in dark mode, open the clear diagnostics modal."
)

printf 'Using device: %s\n' "$DEVICE_SERIAL"
printf 'Saving captures to: %s\n\n' "$OUTPUT_DIR"

for index in "${!STEPS[@]}"; do
  step="${STEPS[$index]}"
  slug="${step%%|*}"
  instruction="${step#*|}"
  output_path="$OUTPUT_DIR/$slug.png"
  mkdir -p "$(dirname "$output_path")"

  printf 'Step %d/%d\n' "$((index + 1))" "${#STEPS[@]}"
  printf 'Target: %s\n' "$slug"
  printf 'Instruction: %s\n' "$instruction"

  case "$slug" in
    light/setup/page|dark/setup/page)
      if confirm_clear_app_data; then
        clear_app_data "$DEVICE_SERIAL"
      else
        printf 'Skipped app reset for %s\n' "$slug"
      fi
      ;;
  esac

  action="$(wait_for_action 'Press Enter to capture, s to skip, or q to quit: ')"
  case "$action" in
    skip)
      printf 'Skipped %s\n\n' "$slug"
      continue
      ;;
    quit)
      exit 0
      ;;
  esac

  while true; do
    run_capture "$DEVICE_SERIAL" "$output_path"
    is_final_step="false"
    if [[ "$index" -eq "$((${#STEPS[@]} - 1))" ]]; then
      is_final_step="true"
    fi
    if confirm_capture "$output_path" "$is_final_step"; then
      printf '\n'
      break
    fi
  done
done

printf 'Capture session complete: %s\n' "$OUTPUT_DIR"
