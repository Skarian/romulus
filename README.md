# Romulus Android

Android app for Real-Debrid ROM downloads

## What it does

- Download files into specified sub-directories
- Unarchive .zip, .rar, and .7z files automatically
- Apply rename policy on files and unarchived files
- Set ignore policy to only browse files you are interested in
- Remote zip downloading when you are only interested in specific files and want to avoid downloading the entire archive
- Download queue with retry semantics

## Config file

The app requires the user provides a JSON file pointing to specific magnets to select from

You are free to use my config:

```text
https://raw.githubusercontent.com/Skarian/romulus-config/main/source.json
```

Or produce your own, learn more here:
[romulus-config](https://github.com/Skarian/romulus-config)

## Requirements

- Android 13+

## Install

Install the latest signed APK from GitHub Releases.

## Docs

- Product and behavior docs: `docs/BEHAVIOR.md`
- Architecture overview: `docs/ARCHITECTURE.md`
- Documentation index: `docs/README.md`
- UI design note: `docs/design/aged-brass-ui.md`

## Repository Layout

- `app/`: Android application module
- `docs/`: behavior, architecture, design, and reference docs
- `artwork/`: launcher and branding assets
- `scripts/`: repo-local helper scripts
