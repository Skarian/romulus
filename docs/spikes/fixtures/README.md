# Spike Fixtures

This folder defines the fixture inventory and the local generation workflow for spike execution.

## 1. Purpose

1. Keep spike inputs separate from migration implementation.
2. Reuse checked-in reference archives where possible instead of fabricating new fixtures.
3. Keep user-supplied Real-Debrid inputs local and out of git.

## 2. Local Workflow

1. Run `just init-env` in `docs/spikes`, or copy [`../.env.example`](/Users/nskaria/projects/romulus/docs/spikes/.env.example) to `../.env.local`, only if you need the user-supplied Spike 1 or Spike 4 inputs.
2. Fill the Real-Debrid token plus the Spike 1 and Spike 4 fields you care about when those spikes are in play.
3. Spike 1 now has two input groups:
   - fast-path matrix inputs,
   - separate uncached provider-acquisition inputs.
4. The fixture generator now summarizes both the likely-cached Spike 1 matrix inputs and the known-uncached Spike 1 inputs.
5. Run:

```bash
cd docs/spikes
just fixtures
```

6. Review the generated workspace in `generated/`.
7. Spike 2 and Spike 3 fixture generation should succeed even when `../.env.local` does not exist, because those spikes use repo-provided fixtures only.

## 3. Field Format

1. Selected-path fields use comma-separated path lists.
2. `SPIKE1_ROOT_SELECTED_PATHS` and `SPIKE1_DIRECTORY_SELECTED_PATHS` should use provider file paths from the torrent file list.
3. `SPIKE1_UNCACHED_SELECTED_PATH` should use one provider file path from the uncached torrent file list.
4. `SPIKE4_SELECTED_INTERNAL_PATHS` should use internal file paths from the targeted zip.
5. `SPIKE4_IGNORE_GLOBS` should use comma-separated glob patterns.
6. Blank `SPIKE4_TORRENT_PATH` means `/`.
7. `SPIKE4_UNARCHIVE` should use `true` or `false`.
8. `SPIKE4_RECURSIVE_UNARCHIVE` should use `true` or `false`.

## 4. Generated Workspace

1. `generated/README.md` - summary of what was prepared.
2. `generated/spike-1/user-input.md` - sanitized Spike 1 readiness summary for both the likely-cached matrix inputs and the known-uncached inputs when `.env.local` is present, otherwise a local-input-missing placeholder.
3. `generated/spike-2/input/` - copied local archive fixtures for Android unarchive runs.
4. `generated/spike-2/fixtures.md` - Spike 2 fixture manifest and source mapping.
5. `generated/spike-3/http-root/` - copied remote-zip fixtures to serve over local HTTP.
6. `generated/spike-3/fixtures.md` - Spike 3 fixture manifest and source mapping.
7. `generated/spike-4/user-input.md` - sanitized Spike 4 readiness summary from `.env.local` when present, otherwise a local-input-missing placeholder.

## 5. User-Supplied Inputs

1. I cannot fabricate live Real-Debrid account state.
2. You provide these through local ignored file `../.env.local`:
   - `RD_API_TOKEN`,
   - `SPIKE1_MAGNET`,
   - Spike 1 scope and selected-path inputs,
   - `SPIKE1_UNCACHED_MAGNET`,
   - `SPIKE1_UNCACHED_SELECTED_PATH`,
   - `SPIKE4_MAGNET`,
   - Spike 4 archive-selection and output-shaping inputs.
3. The uncached Spike 1 fields feed the separate explicit `just spike-1-uncached` provider-acquisition flow.
4. The generation step does not print or persist the token value. It records only presence or missing status plus non-secret path selections.

## 6. Repo-Provided Fixture Set

1. Spike 2 local archive coverage currently includes:
   - normal zip,
   - normal 7z,
   - normal rar,
   - multipart 7z,
   - multipart rar,
   - nested zip,
   - encrypted archive failures,
   - corrupted archive failures.
2. Spike 3 remote zip fixture inventory currently includes:
   - normal range-friendly zip,
   - uncompressed zip,
   - big-directory zip,
   - zip64 zip,
   - duplicate-entry zip,
   - nested-archive zip,
   - malformed zip,
   - encrypted zip failure.
3. The latest verified Spike 3 matrix explicitly exercised:
   - normal range-friendly zip,
   - big-directory zip,
   - zip64 zip,
   - duplicate-entry zip,
   - malformed zip,
   - encrypted zip failure,
   - plus simulated no-range and constrained-range transport modes from the local HTTP server.

## 7. Concrete Reference Sources

1. Spike 2 copies from:
   - [`references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/simple.zip`](/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/simple.zip)
   - [`references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/encoding/unicode_file_names.zip`](/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/encoding/unicode_file_names.zip)
   - [`references/commons-compress/src/test/resources/bla.7z`](/Users/nskaria/projects/romulus/references/commons-compress/src/test/resources/bla.7z)
   - [`references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/multiple-files/rar/archive1.zip.0.rar`](/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/multiple-files/rar/archive1.zip.0.rar)
   - [`references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-7z.7z.001`](/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-7z.7z.001)
   - [`references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-rar.part1.rar`](/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/snippets/multipart-rar.part1.rar)
   - [`references/commons-compress/src/test/resources/OSX_ArchiveWithNestedArchive.zip`](/Users/nskaria/projects/romulus/references/commons-compress/src/test/resources/OSX_ArchiveWithNestedArchive.zip)
   - [`references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/bug/Ticket19-pass.rar`](/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/bug/Ticket19-pass.rar)
   - [`references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/misc/password-protection/two-passwords.7z`](/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/misc/password-protection/two-passwords.7z)
   - [`references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/bug/wrong_crc_getter_in_simple_interface.7z`](/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/bug/wrong_crc_getter_in_simple_interface.7z)
2. Spike 3 copies from:
   - [`references/cloudzip/pkg/zipfile/testdata/regular.zip`](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/testdata/regular.zip)
   - [`references/cloudzip/pkg/zipfile/testdata/uncompressed.zip`](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/testdata/uncompressed.zip)
   - [`references/cloudzip/pkg/zipfile/testdata/big_directory.zip`](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/testdata/big_directory.zip)
   - [`references/cloudzip/pkg/zipfile/testdata/zip64.zip`](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/testdata/zip64.zip)
   - [`references/zip4j/src/test/resources/test-archives/zip_with_duplicate_entries.zip`](/Users/nskaria/projects/romulus/references/zip4j/src/test/resources/test-archives/zip_with_duplicate_entries.zip)
   - [`references/commons-compress/src/test/resources/OSX_ArchiveWithNestedArchive.zip`](/Users/nskaria/projects/romulus/references/commons-compress/src/test/resources/OSX_ArchiveWithNestedArchive.zip)
   - [`references/cloudzip/pkg/zipfile/testdata/malformed.zip`](/Users/nskaria/projects/romulus/references/cloudzip/pkg/zipfile/testdata/malformed.zip)
   - [`references/zip4j/src/test/resources/test-archives/strong_encrypted.zip`](/Users/nskaria/projects/romulus/references/zip4j/src/test/resources/test-archives/strong_encrypted.zip)

## 8. Tooling

1. No extra host CLI install is required for this fixture-generation pass.
2. The workflow only copies checked-in reference fixtures and generates local manifests.
