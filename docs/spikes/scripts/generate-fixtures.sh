#!/usr/bin/env bash
set -euo pipefail

spikes_dir="$(cd "$(dirname "$0")/.." && pwd)"
repo_root="$(cd "$spikes_dir/../.." && pwd)"
env_file="${1:-$spikes_dir/.env.local}"
env_present="false"

unset RD_API_TOKEN \
  SPIKE1_MAGNET \
  SPIKE1_ROOT_SELECTED_PATHS \
  SPIKE1_DIRECTORY_SCOPE \
  SPIKE1_DIRECTORY_SELECTED_PATHS \
  SPIKE1_EXACT_ZIP_PATH \
  SPIKE1_UNCACHED_MAGNET \
  SPIKE1_UNCACHED_SELECTED_PATH \
  SPIKE4_MAGNET \
  SPIKE4_TORRENT_PATH \
  SPIKE4_EXACT_ZIP_PATH \
  SPIKE4_SELECTED_INTERNAL_PATHS \
  SPIKE4_IGNORE_GLOBS \
  SPIKE4_UNARCHIVE \
  SPIKE4_RECURSIVE_UNARCHIVE \
  SPIKE4_DESTINATION_SUBFOLDER \
  SPIKE4_RENAME_PATTERN \
  SPIKE4_RENAME_REPLACEMENT || true

if [[ -f "$env_file" ]]; then
  env_present="true"
  set -a
  source "$env_file"
  set +a
fi

generated_dir="$spikes_dir/fixtures/generated"
spike1_dir="$generated_dir/spike-1"
spike2_dir="$generated_dir/spike-2"
spike3_dir="$generated_dir/spike-3"
spike4_dir="$generated_dir/spike-4"

mkdir -p \
  "$spike1_dir" \
  "$spike2_dir/input/7z" \
  "$spike2_dir/input/rar" \
  "$spike2_dir/input/zip" \
  "$spike2_dir/input/failure" \
  "$spike2_dir/input/nested" \
  "$spike3_dir/http-root" \
  "$spike4_dir"

copy_fixture() {
  local source_path="$1"
  local target_path="$2"
  mkdir -p "$(dirname "$target_path")"
  cp -f "$source_path" "$target_path"
}

normalize_scope() {
  local value="${1:-}"
  if [[ -z "$value" ]]; then
    printf '/'
  else
    printf '%s' "$value"
  fi
}

presence() {
  local value="${1:-}"
  if [[ -n "$value" ]]; then
    printf 'present'
  else
    printf 'missing'
  fi
}

bool_or_default() {
  local value="${1:-}"
  local fallback="$2"
  if [[ -n "$value" ]]; then
    printf '%s' "$value"
  else
    printf '%s' "$fallback"
  fi
}

display_value() {
  local name="$1"
  if [[ ! -v "$name" ]]; then
    printf 'UNSET'
    return
  fi

  local value="${!name}"
  if [[ -z "$value" ]]; then
    printf 'EMPTY'
  else
    printf '%s' "$value"
  fi
}

copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/simple.zip" \
  "$spike2_dir/input/zip/simple.zip"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/encoding/unicode_file_names.zip" \
  "$spike2_dir/input/zip/unicode_file_names.zip"
copy_fixture \
  "$repo_root/references/commons-compress/src/test/resources/bla.7z" \
  "$spike2_dir/input/7z/simple.7z"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/multiple-files/rar/archive1.zip.0.rar" \
  "$spike2_dir/input/rar/simple.rar"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-7z.7z.001" \
  "$spike2_dir/input/7z/multipart-7z.7z.001"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-7z.7z.002" \
  "$spike2_dir/input/7z/multipart-7z.7z.002"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-7z.7z.003" \
  "$spike2_dir/input/7z/multipart-7z.7z.003"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-7z.7z.004" \
  "$spike2_dir/input/7z/multipart-7z.7z.004"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-rar.part1.rar" \
  "$spike2_dir/input/rar/multipart-rar.part1.rar"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-rar.part2.rar" \
  "$spike2_dir/input/rar/multipart-rar.part2.rar"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-rar.part3.rar" \
  "$spike2_dir/input/rar/multipart-rar.part3.rar"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-rar.part4.rar" \
  "$spike2_dir/input/rar/multipart-rar.part4.rar"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-rar.part5.rar" \
  "$spike2_dir/input/rar/multipart-rar.part5.rar"
copy_fixture \
  "$repo_root/references/commons-compress/src/test/resources/OSX_ArchiveWithNestedArchive.zip" \
  "$spike2_dir/input/nested/nested-archive.zip"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/bug/Ticket19-pass.rar" \
  "$spike2_dir/input/failure/encrypted.rar"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/misc/password-protection/two-passwords.7z" \
  "$spike2_dir/input/failure/encrypted.7z"
copy_fixture \
  "$repo_root/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/bug/wrong_crc_getter_in_simple_interface.7z" \
  "$spike2_dir/input/failure/corrupt.7z"

copy_fixture \
  "$repo_root/references/cloudzip/pkg/zipfile/testdata/regular.zip" \
  "$spike3_dir/http-root/regular.zip"
copy_fixture \
  "$repo_root/references/cloudzip/pkg/zipfile/testdata/uncompressed.zip" \
  "$spike3_dir/http-root/uncompressed.zip"
copy_fixture \
  "$repo_root/references/cloudzip/pkg/zipfile/testdata/big_directory.zip" \
  "$spike3_dir/http-root/big_directory.zip"
copy_fixture \
  "$repo_root/references/cloudzip/pkg/zipfile/testdata/zip64.zip" \
  "$spike3_dir/http-root/zip64.zip"
copy_fixture \
  "$repo_root/references/zip4j/src/test/resources/test-archives/zip_with_duplicate_entries.zip" \
  "$spike3_dir/http-root/duplicate_entries.zip"
copy_fixture \
  "$repo_root/references/commons-compress/src/test/resources/OSX_ArchiveWithNestedArchive.zip" \
  "$spike3_dir/http-root/nested_archive.zip"
copy_fixture \
  "$repo_root/references/cloudzip/pkg/zipfile/testdata/malformed.zip" \
  "$spike3_dir/http-root/malformed.zip"
copy_fixture \
  "$repo_root/references/zip4j/src/test/resources/test-archives/strong_encrypted.zip" \
  "$spike3_dir/http-root/strong_encrypted.zip"

cat > "$generated_dir/README.md" <<EOF
# Generated Spike Fixtures

This workspace is generated by \`docs/spikes/justfile\`.

## Contents

1. \`spike-1/user-input.md\` - sanitized Spike 1 readiness summary from \`$(basename "$env_file")\` when present.
2. \`spike-2/input/\` - local archive fixtures copied from checked-in reference modules.
3. \`spike-2/fixtures.md\` - Spike 2 fixture manifest.
4. \`spike-3/http-root/\` - remote-zip fixtures for the local HTTP test server.
5. \`spike-3/fixtures.md\` - Spike 3 fixture manifest.
6. \`spike-4/user-input.md\` - sanitized Spike 4 readiness summary from \`$(basename "$env_file")\` when present.
EOF

if [[ "$env_present" == "true" ]]; then
cat > "$spike1_dir/user-input.md" <<EOF
# Spike 1 User Input

## Credential Status

1. \`RD_API_TOKEN\`: $(presence "${RD_API_TOKEN:-}")

## Run A - Add Again With Root Scope

1. Scope: \`/\`
2. \`SPIKE1_MAGNET\`: $(presence "${SPIKE1_MAGNET:-}")
3. \`SPIKE1_ROOT_SELECTED_PATHS\`: $(display_value SPIKE1_ROOT_SELECTED_PATHS)
4. Ready: $( [[ -n "${RD_API_TOKEN:-}" && -n "${SPIKE1_MAGNET:-}" && -n "${SPIKE1_ROOT_SELECTED_PATHS:-}" ]] && printf 'yes' || printf 'no' )

## Run B - Add With Directory Scope

1. \`SPIKE1_DIRECTORY_SCOPE\`: $(normalize_scope "${SPIKE1_DIRECTORY_SCOPE:-}")
2. \`SPIKE1_DIRECTORY_SELECTED_PATHS\`: $(display_value SPIKE1_DIRECTORY_SELECTED_PATHS)
3. Ready: $( [[ -n "${RD_API_TOKEN:-}" && -n "${SPIKE1_MAGNET:-}" && -n "${SPIKE1_DIRECTORY_SCOPE:-}" && -n "${SPIKE1_DIRECTORY_SELECTED_PATHS:-}" ]] && printf 'yes' || printf 'no' )

## Run C - Add Again With Exact Zip Scope

1. \`SPIKE1_EXACT_ZIP_PATH\`: $(display_value SPIKE1_EXACT_ZIP_PATH)
2. Ready: $( [[ -n "${RD_API_TOKEN:-}" && -n "${SPIKE1_MAGNET:-}" && -n "${SPIKE1_EXACT_ZIP_PATH:-}" ]] && printf 'yes' || printf 'no' )

## Known-Uncached Profile

1. \`SPIKE1_UNCACHED_MAGNET\`: $(presence "${SPIKE1_UNCACHED_MAGNET:-}")
2. \`SPIKE1_UNCACHED_SELECTED_PATH\`: $(display_value SPIKE1_UNCACHED_SELECTED_PATH)
3. Ready: $( [[ -n "${RD_API_TOKEN:-}" && -n "${SPIKE1_UNCACHED_MAGNET:-}" && -n "${SPIKE1_UNCACHED_SELECTED_PATH:-}" ]] && printf 'yes' || printf 'no' )
EOF
else
cat > "$spike1_dir/user-input.md" <<EOF
# Spike 1 User Input

1. Local env file: missing
2. Create \`$spikes_dir/.env.local\` from \`$spikes_dir/.env.example\` when you want Spike 1 readiness output.
EOF
fi

if [[ "$env_present" == "true" ]]; then
cat > "$spike4_dir/user-input.md" <<EOF
# Spike 4 User Input

## Credential Status

1. \`RD_API_TOKEN\`: $(presence "${RD_API_TOKEN:-}")
2. \`SPIKE4_MAGNET\`: $(presence "${SPIKE4_MAGNET:-}")

## Integrated Archive-Selection Inputs

1. \`SPIKE4_TORRENT_PATH\`: $(normalize_scope "${SPIKE4_TORRENT_PATH:-}")
2. \`SPIKE4_EXACT_ZIP_PATH\`: $(display_value SPIKE4_EXACT_ZIP_PATH)
3. \`SPIKE4_SELECTED_INTERNAL_PATHS\`: $(display_value SPIKE4_SELECTED_INTERNAL_PATHS)
4. \`SPIKE4_IGNORE_GLOBS\`: $(display_value SPIKE4_IGNORE_GLOBS)

## Output-Shaping Inputs

1. \`SPIKE4_UNARCHIVE\`: $(bool_or_default "${SPIKE4_UNARCHIVE:-}" "true")
2. \`SPIKE4_RECURSIVE_UNARCHIVE\`: $(bool_or_default "${SPIKE4_RECURSIVE_UNARCHIVE:-}" "true")
3. \`SPIKE4_DESTINATION_SUBFOLDER\`: $(display_value SPIKE4_DESTINATION_SUBFOLDER)
4. \`SPIKE4_RENAME_PATTERN\`: $(display_value SPIKE4_RENAME_PATTERN)
5. \`SPIKE4_RENAME_REPLACEMENT\`: $(display_value SPIKE4_RENAME_REPLACEMENT)

## Readiness

1. Base integration flow ready: $( [[ -n "${RD_API_TOKEN:-}" && -n "${SPIKE4_MAGNET:-}" ]] && printf 'yes' || printf 'no' )
2. Exact zip integration inputs ready: $( [[ -n "${RD_API_TOKEN:-}" && -n "${SPIKE4_MAGNET:-}" && -n "${SPIKE4_EXACT_ZIP_PATH:-}" && -n "${SPIKE4_SELECTED_INTERNAL_PATHS:-}" ]] && printf 'yes' || printf 'no' )
EOF
else
cat > "$spike4_dir/user-input.md" <<EOF
# Spike 4 User Input

1. Local env file: missing
2. Create \`$spikes_dir/.env.local\` from \`$spikes_dir/.env.example\` when you want Spike 4 readiness output.
EOF
fi

cat > "$spike2_dir/fixtures.md" <<EOF
# Spike 2 Fixtures

## Input Set

1. \`input/zip/simple.zip\` - normal zip fixture from 7-Zip Android testdata.
2. \`input/zip/unicode_file_names.zip\` - zip with unicode names.
3. \`input/7z/simple.7z\` - normal 7z fixture.
4. \`input/rar/simple.rar\` - normal rar fixture.
5. \`input/7z/multipart-7z.7z.001\` through \`input/7z/multipart-7z.7z.004\` - multipart 7z fixture.
6. \`input/rar/multipart-rar.part1.rar\` through \`input/rar/multipart-rar.part5.rar\` - multipart rar fixture.
7. \`input/nested/nested-archive.zip\` - nested archive fixture for recursion tests.
8. \`input/failure/encrypted.rar\` - encrypted rar failure fixture.
9. \`input/failure/encrypted.7z\` - encrypted 7z failure fixture.
10. \`input/failure/corrupt.7z\` - corrupted 7z failure fixture.

## Source Map

1. \`simple.zip\` <- \`references/7-Zip-JBinding-4Android/.../snippets/simple.zip\`
2. \`unicode_file_names.zip\` <- \`references/7-Zip-JBinding-4Android/.../encoding/unicode_file_names.zip\`
3. \`simple.7z\` <- \`references/commons-compress/src/test/resources/bla.7z\`
4. \`simple.rar\` <- \`references/7-Zip-JBinding-4Android/.../multiple-files/rar/archive1.zip.0.rar\`
5. \`multipart-7z.*\` <- \`references/7-Zip-JBinding-4Android/.../snippets/multipart-7z.7z.00*\`
6. \`multipart-rar.*\` <- \`references/7-Zip-JBinding-4Android/.../snippets/multipart-rar.part*.rar\`
7. \`nested-archive.zip\` <- \`references/commons-compress/src/test/resources/OSX_ArchiveWithNestedArchive.zip\`
8. \`encrypted.rar\` <- \`references/7-Zip-JBinding-4Android/.../bug/Ticket19-pass.rar\`
9. \`encrypted.7z\` <- \`references/7-Zip-JBinding-4Android/.../misc/password-protection/two-passwords.7z\`
10. \`corrupt.7z\` <- \`references/7-Zip-JBinding-4Android/.../bug/wrong_crc_getter_in_simple_interface.7z\`
EOF

cat > "$spike3_dir/fixtures.md" <<EOF
# Spike 3 Fixtures

## HTTP Root

1. \`http-root/regular.zip\` - normal range-friendly zip fixture.
2. \`http-root/uncompressed.zip\` - uncompressed zip fixture.
3. \`http-root/big_directory.zip\` - many-entry central-directory fixture.
4. \`http-root/zip64.zip\` - zip64 fixture.
5. \`http-root/duplicate_entries.zip\` - duplicate-name identity fixture.
6. \`http-root/nested_archive.zip\` - nested-path archive fixture.
7. \`http-root/malformed.zip\` - malformed zip failure fixture.
8. \`http-root/strong_encrypted.zip\` - encrypted zip failure fixture.

## Source Map

1. \`regular.zip\` <- \`references/cloudzip/pkg/zipfile/testdata/regular.zip\`
2. \`uncompressed.zip\` <- \`references/cloudzip/pkg/zipfile/testdata/uncompressed.zip\`
3. \`big_directory.zip\` <- \`references/cloudzip/pkg/zipfile/testdata/big_directory.zip\`
4. \`zip64.zip\` <- \`references/cloudzip/pkg/zipfile/testdata/zip64.zip\`
5. \`duplicate_entries.zip\` <- \`references/zip4j/src/test/resources/test-archives/zip_with_duplicate_entries.zip\`
6. \`nested_archive.zip\` <- \`references/commons-compress/src/test/resources/OSX_ArchiveWithNestedArchive.zip\`
7. \`malformed.zip\` <- \`references/cloudzip/pkg/zipfile/testdata/malformed.zip\`
8. \`strong_encrypted.zip\` <- \`references/zip4j/src/test/resources/test-archives/strong_encrypted.zip\`
EOF

printf 'Generated spike fixtures in %s\n' "$generated_dir"
