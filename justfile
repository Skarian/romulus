set shell := ["zsh", "-cu"]

device_download_dir := "/storage/emulated/0/Download"
sample_file := "sample_files/test.json"

install-device:
    ./gradlew installDebug

push-sample file=sample_file:
    adb push "{{file}}" "{{device_download_dir}}/"
