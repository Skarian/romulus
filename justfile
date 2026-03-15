set shell := ["zsh", "-cu"]

device_download_dir := "/storage/emulated/0/Download"
sample_file := "sample_files/test.json"
app_id := "com.romulus.mobile"

install-device:
    ./gradlew installDebug

push-sample file=sample_file:
    adb push "{{file}}" "{{device_download_dir}}/"

clear-app-data:
    adb shell pm clear "{{app_id}}"

generate-icon source="artwork/ROMULUS_TRANSPARENT.png" background="" padding="72" round_padding="144" splash_padding="220":
    ./scripts/generate_icon.sh \
        --source "{{source}}" \
        --background-color "{{background}}" \
        --padding "{{padding}}" \
        --round-padding "{{round_padding}}" \
        --splash-padding "{{splash_padding}}" \
        --preview-dir artwork/generated

capture-ui-screenshots session="":
    SESSION_NAME="{{session}}" ./scripts/capture_device_screenshots.sh
