# Naming Behavior

## Trigger

1. Files are resolved and displayed on Home Files page.
2. User toggles rename behavior.
3. Queue task is created.
4. Download output target is reserved at write time.

## Expected Result

1. Files page and Downloads page rows display the original file name.
2. Renaming applies only to downloaded output naming.
3. When unarchive or recursive unarchive is enabled for supported archives (`.zip`, `.rar`, `.7z`), rename applies only to final non-archive output files; rename does not apply to archive filenames.
4. In archive-selection mode, rename applies to selected internal-file outputs; rename does not apply to the outer `.zip` container name.
5. Each queued file keeps both:
   - original file name for identity and resolver rematch,
   - output target metadata used for destination file creation.
6. Output target metadata is:
   - a single output name for direct-file saves,
   - an extracted-output summary or manifest for unarchive rows.
7. On the Home Files page, `Apply rename` in the `File preferences` dialog toggles whether queued output targets follow the source rename rule for that page only, as defined in [`files.md`](files.md).
8. In schema terms, a rename rule exists only when `entries[i].rename` is present and valid with both `pattern` and `replacement`, as defined in [`schema.json`](../schema.json).
9. If `entries[i].rename` is absent, no rename rule exists:
   - direct-file output name defaults to original file name,
   - `Apply rename` is not shown.
10. If a rename rule exists, Files starts with `Apply rename` enabled, and user can toggle it off for that page only.
11. Downloads details include both original file name and output target summary so users can see what will be written; details behavior follows [`downloads.md`](downloads.md).
12. If a direct-file output name collides in destination folder, app auto-suffixes with numbered format ` (n)`.
13. If extracted outputs collide in destination folder, the affected final output files are auto-suffixed with the same numbered format ` (n)`.
14. Archive-selection mode behavior and queue semantics follow [`archive-selection.md`](archive-selection.md).

## Failure Behavior

1. Invalid rename regex is rejected at source validation time.
2. Rename failures fall back to original naming and do not block queueing.
