# Naming Behavior

## Trigger

1. Files are resolved and displayed on Home Files page.
2. User toggles rename behavior.
3. Queue task is created.
4. Download output target is reserved at write time.

## Expected Result

1. Files page and Downloads page rows display the original file name.
2. Renaming applies only to downloaded output naming.
3. Entry `rename` and `unarchive.layout.rename` are independent:
   - entry `rename` applies to final output file names,
   - `unarchive.layout.rename` applies only to the top-level dedicated extract folder name when `layout.mode` is `dedicatedFolder`.
4. When unarchive or recursive unarchive is enabled for supported archives (`.zip`, `.rar`, `.7z`), entry `rename` applies only to final non-archive output files; entry `rename` does not apply to archive filenames.
5. In archive-selection mode, entry `rename` applies to selected internal-file outputs; entry `rename` does not apply to the outer `.zip` container name.
6. When both entry `rename` and dedicated-folder `layout.rename` are configured, the dedicated-folder rename resolves the container folder name first and the entry rename then applies to final extracted file names inside that folder.
7. Each queued file keeps both:
   - original file name for identity and resolver rematch,
   - output target metadata used for destination file creation.
8. Output target metadata is:
   - a single output name for direct-file saves,
   - an extracted-output summary or manifest for unarchive rows.
9. On the Home Files page, `Apply rename` in the `File preferences` dialog toggles whether queued output targets follow the source rename rule for that page only, as defined in [`files.md`](files.md).
10. In schema terms, a file rename rule exists only when `entries[i].rename` is present and valid with both `pattern` and `replacement`, as defined in [`schema.json`](../schema.json).
11. A dedicated-folder rename rule exists only when `entries[i].unarchive.layout.mode` is `dedicatedFolder` and `entries[i].unarchive.layout.rename` is present and valid.
12. If `entries[i].rename` is absent, no file rename rule exists:
   - direct-file output name defaults to original file name,
   - `Apply rename` is not shown.
13. If a file rename rule exists, Files starts with `Apply rename` enabled, and user can toggle it off for that page only.
14. Downloads details include both original file name and output target summary so users can see what will be written; details behavior follows [`downloads.md`](downloads.md).
15. If a direct-file output name collides in destination folder, app auto-suffixes with numbered format ` (n)`.
16. If extracted outputs collide in destination folder, the affected final output files are auto-suffixed with the same numbered format ` (n)`.
17. Archive-selection mode behavior and queue semantics follow [`archive-selection.md`](archive-selection.md).

## Failure Behavior

1. Invalid rename regex is rejected at source validation time.
2. Rename failures fall back to original naming and do not block queueing.
