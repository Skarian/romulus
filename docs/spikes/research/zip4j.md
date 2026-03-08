# Zip4j Research Dump (module SHA `466ffa78f9b070d8fdc183b24bb907926842de33`)

Module root: [references/zip4j](/Users/nskaria/projects/romulus/references/zip4j)

## Spike mapping

1. Spike 2 (`unarchive runtime`): Zip4j is a ZIP-only extraction/writing library with split ZIP, Zip64, encryption, stream APIs, and zip-slip protection, but no `.rar`/`.7z` support.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [CompressionMethod.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/enums/CompressionMethod.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java), [MiscZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/MiscZipFileIT.java)
2. Spike 3 (`remote zip enumeration/selective internal download`): Zip4j supports local-file random-access enumeration (`ZipFile`) and sequential stream iteration (`ZipInputStream`), but has no built-in remote HTTP/range transport layer.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [HeaderReader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderReader.java), [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java)
3. Spike 4 (`integration`): Zip4j provides entry metadata (`FileHeader`) and extraction APIs, but filename-based API paths create duplicate-name ambiguity that integration code must handle explicitly.
Evidence: [FileHeader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/FileHeader.java), [HeaderUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderUtil.java), [ExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractFileTask.java), [RemoveFilesFromZipTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/RemoveFilesFromZipTask.java)

## Source map (key files/functions)

1. Public API surface:
[ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java)
Symbols: `getFileHeaders`, `getFileHeader`, `extractAll`, `extractFile`, `getInputStream`, `isValidZipFile`, `createSplitZipFile`, `mergeSplitFiles`, `setRunInThread`
2. Header parsing and entry model:
[HeaderReader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderReader.java),
[HeaderUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderUtil.java),
[FileHeader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/FileHeader.java),
[AbstractFileHeader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/AbstractFileHeader.java),
[ZipModel.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/ZipModel.java)
3. Extraction pipeline:
[ExtractAllFilesTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractAllFilesTask.java),
[ExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractFileTask.java),
[AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java),
[UnzipUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/UnzipUtil.java)
4. Stream read path:
[ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java),
[ZipEntryInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipEntryInputStream.java),
[InflaterInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/InflaterInputStream.java),
[AesCipherInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/AesCipherInputStream.java),
[ZipStandardCipherInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipStandardCipherInputStream.java)
5. Split archive handling:
[ZipStandardSplitFileInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipStandardSplitFileInputStream.java),
[NumberedSplitFileInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/NumberedSplitFileInputStream.java),
[NumberedSplitRandomAccessFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/NumberedSplitRandomAccessFile.java),
[SplitOutputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/outputstream/SplitOutputStream.java),
[MergeSplitZipFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/MergeSplitZipFileTask.java)
6. Error/progress model:
[ZipException.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/exception/ZipException.java),
[AsyncZipTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AsyncZipTask.java),
[ProgressMonitor.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/progress/ProgressMonitor.java)
7. Constants/config:
[InternalZipConstants.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/InternalZipConstants.java),
[Zip4jConfig.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/Zip4jConfig.java),
[UnzipParameters.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/UnzipParameters.java),
[ZipParameters.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/ZipParameters.java)
8. Behavioral tests used as evidence:
[ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java),
[ZipInputStreamIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/io/inputstream/ZipInputStreamIT.java),
[MiscZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/MiscZipFileIT.java),
[ZipFileZip64IT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ZipFileZip64IT.java),
[HeaderUtilTest.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/headers/HeaderUtilTest.java),
[RemoveFilesFromZipIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/RemoveFilesFromZipIT.java)

## Library API usage model (enumeration/selective extract/streams)

### 1) Enumeration model

1. `ZipFile` is path/file based (`ZipFile(String)`, `ZipFile(File)`), then `readZipInfo()` reads central directory via `RandomAccessFile` + `HeaderReader.readAllHeaders`.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [HeaderReader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderReader.java)
2. `getFileHeaders()` returns cloned `FileHeader` objects from `zipModel.getCentralDirectory().getFileHeaders()`.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [FileHeaderFactory.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/FileHeaderFactory.java)
3. `getFileHeader(name)` uses exact, case-sensitive name match, with slash/backslash fallback normalization.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [HeaderUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderUtil.java), [HeaderUtilTest.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/headers/HeaderUtilTest.java)
4. On non-existent zip path, `getFileHeaders()` returns empty list (new empty `ZipModel` path).
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [ZipFileTest.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ZipFileTest.java)

### 2) Selective extract model

1. `extractFile(String fileName, ...)` resolves one header (or folder prefix case) and extracts through `ExtractFileTask`.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [ExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractFileTask.java)
2. `extractFile(FileHeader, ...)` delegates to `extractFile(fileHeader.getFileName(), ...)`; it does not extract by offset identity.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java)
3. Directory-selective extraction is filename-prefix based (`getFileHeadersUnderDirectory`), and works even when explicit directory entry is missing.
Evidence: [ExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractFileTask.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)
4. `newFileName` rewrites output target names for file or folder extraction path.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [ExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractFileTask.java)

### 3) Stream model

1. `ZipFile.getInputStream(FileHeader)` gives entry stream by preparing split input at header offset and opening `ZipInputStream` at that entry.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [UnzipUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/UnzipUtil.java)
2. `ZipInputStream` supports sequential `getNextEntry()` iteration from an arbitrary `InputStream`.
Evidence: [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java), [ZipInputStreamIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/io/inputstream/ZipInputStreamIT.java)
3. Nested ZIPs can be parsed by chaining streams (`ZipFile.getInputStream(fileHeader)` -> `new ZipInputStream(inputStream, password)`).
Evidence: [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)
4. Add-stream path (`addStream`) requires `ZipParameters.fileNameInZip`, forces non-thread mode, and writes extended local headers.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [AddStreamToZipTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AddStreamToZipTask.java), [AddFilesToZipIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/AddFilesToZipIT.java)

## Zip format capabilities and limits

### Supported capabilities in source

1. ZIP create/add/extract/remove/rename APIs.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [README.md](/Users/nskaria/projects/romulus/references/zip4j/README.md)
2. Compression methods: `STORE` and `DEFLATE` (plus internal AES marker `AES_INTERNAL_ONLY`).
Evidence: [CompressionMethod.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/enums/CompressionMethod.java), [AbstractAddFileToZipTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractAddFileToZipTask.java)
3. Encryption methods: AES and ZIP standard; strong ZIP-standard variant is read-labeled but not supported for extraction.
Evidence: [EncryptionMethod.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/enums/EncryptionMethod.java), [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java), [AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)
4. Zip64 read/write/update coverage (size/offset/count expansion).
Evidence: [HeaderReader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderReader.java), [InternalZipConstants.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/InternalZipConstants.java), [ZipFileZip64IT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ZipFileZip64IT.java)
5. Split ZIP support:
Standard `.z01/.z02/.../.zip` read/write/merge and numbered `.zip.001/.zip.002/...` read.
Evidence: [SplitOutputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/outputstream/SplitOutputStream.java), [MergeSplitZipFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/MergeSplitZipFileTask.java), [UnzipUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/UnzipUtil.java), [NumberedSplitRandomAccessFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/NumberedSplitRandomAccessFile.java)
6. Charset handling for filenames/comments with configurable charset.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [HeaderUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderUtil.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)
7. Symlink archive/extract behavior with policy control.
Evidence: [ZipParameters.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/ZipParameters.java), [UnzipParameters.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/UnzipParameters.java), [AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)

### Limits and hard constraints in source

1. ZIP-only codebase: no `.rar`/`.7z` archive parser/adapter in source tree.
Evidence: [README.md](/Users/nskaria/projects/romulus/references/zip4j/README.md), [src/main/java/net/lingala/zip4j](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j)
2. Update operations are blocked for split/spanned archives.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [AddFilesToZipIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/AddFilesToZipIT.java), [RenameFilesInZipIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/RenameFilesInZipIT.java), [RemoveFilesFromZipIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/RemoveFilesFromZipIT.java)
3. Min split length is `65536` bytes.
Evidence: [InternalZipConstants.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/InternalZipConstants.java), [SplitOutputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/outputstream/SplitOutputStream.java)
4. End-of-central-directory reverse seek scans at most `MAX_COMMENT_SIZE` (`65536`) bytes.
Evidence: [InternalZipConstants.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/InternalZipConstants.java), [HeaderReader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderReader.java)
5. Runtime buffer constraints: default `4096`, minimum `512`.
Evidence: [InternalZipConstants.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/InternalZipConstants.java), [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java)
6. Build metadata says Java 8 source/target.
Evidence: [pom.xml](/Users/nskaria/projects/romulus/references/zip4j/pom.xml)

## Error model and operational constraints

1. Typed error surface is `ZipException.Type`:
`WRONG_PASSWORD`, `TASK_CANCELLED_EXCEPTION`, `CHECKSUM_MISMATCH`, `UNKNOWN_COMPRESSION_METHOD`, `FILE_NOT_FOUND`, `UNSUPPORTED_ENCRYPTION`, `UNKNOWN`.
Evidence: [ZipException.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/exception/ZipException.java)
2. Wrong-password cases are mapped to `WRONG_PASSWORD` for AES and ZIP-standard flows.
Evidence: [AESDecrypter.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/crypto/AESDecrypter.java), [StandardDecrypter.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/crypto/StandardDecrypter.java), [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)
3. Unknown compression code throws `UNKNOWN_COMPRESSION_METHOD`.
Evidence: [CompressionMethod.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/enums/CompressionMethod.java), [CompressionMethodTest.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/model/enums/CompressionMethodTest.java)
4. Strong encryption is rejected during extraction.
Evidence: [AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java), [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java), [ZipInputStreamIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/io/inputstream/ZipInputStreamIT.java)
5. Zip-slip mitigation: extraction checks canonical output path containment and throws when entry escapes target root.
Evidence: [AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java), [MiscZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/MiscZipFileIT.java)
6. Corruption tolerance is mixed:
extra-data parsing errors are ignored in header parse, but malformed core headers/CRC mismatches throw.
Evidence: [HeaderReader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderReader.java), [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)
7. Progress/async constraints:
`runInThread=true` uses one executor, busy-state blocks concurrent operations, cancellation raises `TASK_CANCELLED_EXCEPTION`.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [AsyncZipTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AsyncZipTask.java), [ProgressMonitor.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/progress/ProgressMonitor.java), [ZipFileTest.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ZipFileTest.java)
8. Extraction operational behavior:
`extractAll` skips entries prefixed `__MACOSX`; failed file extraction attempts delete partially written output file.
Evidence: [ExtractAllFilesTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractAllFilesTask.java), [AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java)
9. `isValidZipFile()` is boolean-only and swallows exceptions (no diagnostic object).
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [MiscZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/MiscZipFileIT.java)
10. No built-in archive cleanup policy post-extraction (source archive retention/deletion is not handled by extract tasks).
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [ExtractAllFilesTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractAllFilesTask.java), [ExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractFileTask.java)

## Remote-zip relevance (what it can/cannot do directly)

### What it can do directly

1. Parse ZIP entries from any provided `InputStream` sequentially with `ZipInputStream#getNextEntry`.
Evidence: [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java), [ZipInputStreamIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/io/inputstream/ZipInputStreamIT.java)
2. Enumerate and selectively extract entries from local/split files with `ZipFile` using random access.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [UnzipUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/UnzipUtil.java)

### What it cannot do directly

1. No URL/HTTP/range-aware API in `ZipFile`; constructors are file-path based.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java)
2. Central-directory enumeration in `ZipFile` depends on `RandomAccessFile` seek operations (`HeaderReader` tail scan + header seeks), so remote range transport must be implemented externally.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [HeaderReader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderReader.java), [ZipStandardSplitFileInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipStandardSplitFileInputStream.java)
3. Selected-entry remote fetch without full archive download is not provided as a built-in transport+parser feature.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java)

## Data identity and duplicate-name implications

1. `FileHeader` carries identity-relevant metadata (`fileName`, `offsetLocalHeader`, `diskNumberStart`, sizes, CRC).
Evidence: [FileHeader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/FileHeader.java), [AbstractFileHeader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/AbstractFileHeader.java)
2. `FileHeader.equals/hashCode` include filename + local-header offset semantics (offset via Zip64 if present).
Evidence: [FileHeader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/FileHeader.java)
3. Name lookup APIs are first-match and case-sensitive:
`HeaderUtil.getFileHeader` scans and returns first exact string match.
Evidence: [HeaderUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderUtil.java), [HeaderUtilTest.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/headers/HeaderUtilTest.java)
4. `extractFile(FileHeader, ...)` does not preserve offset identity; it delegates to filename extraction.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java)
5. `removeFile(FileHeader)` and `renameFile(FileHeader, ...)` also delegate to filename, not offset identity.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java)
6. Remove/rename matching rules apply by filename equality or directory prefix across file-header iteration; duplicate names are therefore name-driven in these mutations.
Evidence: [RemoveFilesFromZipTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/RemoveFilesFromZipTask.java), [RenameFilesTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/RenameFilesTask.java)
7. Output collision behavior on extraction is overwrite-oriented:
output path is deterministically resolved from entry name, then written with `FileOutputStream` without collision branching.
Evidence: [AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java)
8. Path normalization during output mapping (`/` and `\` -> system separator) can collapse distinct archive name spellings to one filesystem path.
Evidence: [AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java)
9. Existing tests cover duplicate-entry mutation path (remove operation fixture with duplicate entries), confirming duplicate scenarios are in test scope.
Evidence: [RemoveFilesFromZipIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/RemoveFilesFromZipIT.java)

## Gaps/unknowns relative to spike specs

### Spike 2 (`spike-2-unarchive-runtime.md`)

1. `.rar`/`.7z` handling is not implemented in Zip4j source.
Evidence: [src/main/java/net/lingala/zip4j](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j), [README.md](/Users/nskaria/projects/romulus/references/zip4j/README.md)
2. Recursive archive traversal/orchestration is not provided as a built-in workflow; nested ZIP handling in tests is manual stream chaining.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)
3. Archive-retention/deletion policy after extraction is `UNCONFIRMED` in library API (no explicit post-extract delete toggle in extract calls).
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [ExtractAllFilesTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractAllFilesTask.java)
4. Runtime/storage performance envelopes for target spike runtime are `UNCONFIRMED` in source (no benchmark artifacts in module).
Evidence: [src/main/java/net/lingala/zip4j](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j), [src/test/java/net/lingala/zip4j](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j)

### Spike 3 (`spike-3-remote-zip.md`)

1. Remote HTTP capability requirements are outside library scope; Zip4j has no built-in HTTP client/range request integration.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java)
2. Stable internal-entry identity beyond filename is not exposed as a dedicated public key type; callers must derive from available header fields.
Evidence: [FileHeader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/FileHeader.java), [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java)
3. Selected-only remote retrieval without full archive transport is `UNCONFIRMED` inside Zip4j itself and requires external transport strategy.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [HeaderReader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderReader.java)

### Spike 4 (`spike-4-integration.md`)

1. Cross-stage identity for duplicate names is a known integration risk because several APIs collapse to filename-based lookup.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [HeaderUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderUtil.java)
2. Deterministic collision policy for same output path is write-last behavior from extraction iteration order; integration layer must define whether that is acceptable.
Evidence: [ExtractAllFilesTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractAllFilesTask.java), [AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java)

## Reusable patterns for spike app (adopt/adapt/avoid)

### Adopt

1. Use `ZipFile.getFileHeaders()` + header metadata capture for local ZIP enumeration.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java)
2. Keep zip-slip checks from Zip4j extraction path in any custom extraction branch.
Evidence: [AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java)
3. Use `ProgressMonitor` when running threaded extraction in spike harnesses to get stage progress and cancellation.
Evidence: [ProgressMonitor.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/progress/ProgressMonitor.java), [AsyncZipTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AsyncZipTask.java)
4. Use `UnzipParameters.setExtractSymbolicLinks` as explicit policy switch in spike runs.
Evidence: [UnzipParameters.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/UnzipParameters.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)

### Adapt

1. For duplicate-name-safe selective extraction, prefer `getInputStream(FileHeader)`-driven extraction pipeline rather than `extractFile(FileHeader)` (which name-collapses).
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [UnzipUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/UnzipUtil.java)
2. Build recursion controller outside Zip4j for nested archive passes and termination rules.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)
3. If `__MACOSX` entries are required for debugging parity, use selective extraction path or custom streaming path instead of `extractAll`.
Evidence: [ExtractAllFilesTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractAllFilesTask.java)
4. For remote ZIP flows, add external transport/range layer before handing data to Zip4j components.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [HeaderReader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderReader.java)

### Avoid

1. Avoid using Zip4j as the sole adapter for non-ZIP archive formats in Spike 2.
Evidence: [src/main/java/net/lingala/zip4j](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j)
2. Avoid depending on `isValidZipFile()` for diagnostics; it intentionally returns boolean-only.
Evidence: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java)
3. Avoid assuming FileHeader clones are deep immutable snapshots for all nested fields.
Evidence: [FileHeaderFactory.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/FileHeaderFactory.java)

## Evidence checklist additions for spike runs

1. Capture per-entry enumeration identity: `fileName`, `offsetLocalHeader`, `diskNumberStart`, `compressedSize`, `uncompressedSize`, `crc`, `encryptionMethod`.
Evidence source fields: [FileHeader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/FileHeader.java), [AbstractFileHeader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/AbstractFileHeader.java)
2. For selective extraction runs, include both API route used (`extractFile(name)` vs `getInputStream(fileHeader)`), and prove behavior on duplicate filenames.
Evidence anchors: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [HeaderUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderUtil.java)
3. Add zip-slip test fixture run and assert exact exception message/type.
Evidence anchors: [AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java), [MiscZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/MiscZipFileIT.java)
4. Add wrong-password matrix (`AES`, `ZIP_STANDARD`, null/empty password) with recorded `ZipException.Type`.
Evidence anchors: [AESDecrypter.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/crypto/AESDecrypter.java), [StandardDecrypter.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/crypto/StandardDecrypter.java), [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)
5. Add split archive evidence: standard split read/write/merge and numbered split read behavior.
Evidence anchors: [SplitOutputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/outputstream/SplitOutputStream.java), [UnzipUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/UnzipUtil.java), [MergeSplitZipFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/MergeSplitZipFileTask.java)
6. Add `__MACOSX` handling check when using `extractAll` so output-manifest expectations are explicit.
Evidence anchor: [ExtractAllFilesTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractAllFilesTask.java)
7. For Spike 3 remote runs, explicitly log external transport bytes/range behavior because Zip4j itself does not provide HTTP/range instrumentation.
Evidence anchors: [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java), [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java)

## Appendix: concise file/symbol index with absolute paths

1. [ZipFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/ZipFile.java)
Symbols: `readZipInfo`, `getFileHeaders`, `getFileHeader`, `extractAll`, `extractFile`, `getInputStream`, `isValidZipFile`
2. [HeaderReader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderReader.java)
Symbols: `readAllHeaders`, `readCentralDirectory`, `locateOffsetOfEndOfCentralDirectory`
3. [HeaderUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/headers/HeaderUtil.java)
Symbols: `getFileHeader`, `getFileHeadersUnderDirectory`, `getOffsetStartOfCentralDirectory`
4. [AbstractExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/AbstractExtractFileTask.java)
Symbols: `extractFile`, `assertCanonicalPathsAreSame`, `verifyNextEntry`
5. [ExtractAllFilesTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractAllFilesTask.java)
Symbols: `executeTask`, `prepareZipInputStream`
6. [ExtractFileTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/ExtractFileTask.java)
Symbols: `getFileHeadersToExtract`, `determineNewFileName`
7. [ZipInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipInputStream.java)
Symbols: `getNextEntry`, `initializeCipherInputStream`, `verifyCrc`
8. [UnzipUtil.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/UnzipUtil.java)
Symbols: `createZipInputStream`, `createSplitInputStream`
9. [FileHeader.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/model/FileHeader.java)
Symbols: `equals`, `hashCode`, `getOffsetLocalHeader`
10. [ZipException.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/exception/ZipException.java)
Symbols: `Type` enum
11. [InternalZipConstants.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/util/InternalZipConstants.java)
Symbols: `MIN_SPLIT_LENGTH`, `ZIP_64_SIZE_LIMIT`, `MAX_COMMENT_SIZE`, `BUFF_SIZE`, `MIN_BUFF_SIZE`
12. [SplitOutputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/outputstream/SplitOutputStream.java)
Symbols: `startNextSplitFile`, `checkBufferSizeAndStartNextSplitFile`
13. [ZipStandardSplitFileInputStream.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/ZipStandardSplitFileInputStream.java)
Symbols: `prepareExtractionForFileHeader`, `openRandomAccessFileForIndex`
14. [NumberedSplitRandomAccessFile.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/io/inputstream/NumberedSplitRandomAccessFile.java)
Symbols: `seek`, `openLastSplitFileForReading`
15. [RemoveFilesFromZipTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/RemoveFilesFromZipTask.java)
Symbols: `filterNonExistingEntries`, `shouldEntryBeRemoved`
16. [RenameFilesTask.java](/Users/nskaria/projects/romulus/references/zip4j/src/main/java/net/lingala/zip4j/tasks/RenameFilesTask.java)
Symbols: `getCorrespondingEntryFromMap`, `updateHeadersInZipModel`
17. [ExtractZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ExtractZipFileIT.java)
18. [ZipInputStreamIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/io/inputstream/ZipInputStreamIT.java)
19. [MiscZipFileIT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/MiscZipFileIT.java)
20. [ZipFileZip64IT.java](/Users/nskaria/projects/romulus/references/zip4j/src/test/java/net/lingala/zip4j/ZipFileZip64IT.java)
