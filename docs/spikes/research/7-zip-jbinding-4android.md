# 7-Zip-JBinding-4Android Research Dump
Module commit SHA: `875f38aac441f41e6eb693177e020e97971dca97`

## Spike mapping

| Spike | What this module can answer from source | Primary evidence |
| --- | --- | --- |
| Spike 2 (`spike-2-unarchive-runtime.md`) | Open/extract API contracts, format handling for `zip/rar/7z`, password/multipart paths, JNI/runtime constraints, error/result surfaces | `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:20`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:769`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/JavaToCPP/JavaToCPPSevenZip.cpp:136`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ExtractOperationResult.java:10` |
| Spike 4 (`spike-4-integration.md`) | Archive item identity fields, per-item extraction result hooks, open/extract progress callbacks, caller-owned orchestration boundaries | `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/PropID.java:29`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveExtractCallback.java:12`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveOpenCallback.java:9`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:31` |

Specs linked from this research target:
- `/Users/nskaria/projects/romulus/docs/spikes/specs/spike-2-unarchive-runtime.md`
- `/Users/nskaria/projects/romulus/docs/spikes/specs/spike-4-integration.md`

## Source map (key files/functions)

### Public API and Java-side implementation
- Open/init/version APIs: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:244`, `:360`, `:708`, `:769`, `:806`, `:834`, `:839`, `:949`, `:974`, `:994`
- Archive extraction interface: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:20`
- Extract callback interface: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveExtractCallback.java:12`
- Open callbacks: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveOpenCallback.java:9`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveOpenVolumeCallback.java:9`
- Error/result enums and properties: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ExtractOperationResult.java:10`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ExtractAskMode.java:9`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/PropID.java:12`
- Format registry: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ArchiveFormat.java:199`
- Java implementation details (`extractSlow`, close guard, archive format binding): `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/InArchiveImpl.java:37`

### Stream contracts and volume helpers
- Seekable input requirement: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInStream.java:9`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ISeekableStream.java:9`
- Multi-thread callback notes on stream methods: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ISeekableStream.java:31`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ISequentialInStream.java:19`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ISequentialOutStream.java:15`
- RandomAccessFile adapter: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/RandomAccessFileInStream.java:15`
- 7z volume concatenation helper: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/VolumedArchiveInStream.java:14`

### JNI/native bridge path
- Native open path and codec iteration: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/JavaToCPP/JavaToCPPSevenZip.cpp:136`
- Open callback multiplexing (open callback + optional volume/password interfaces): `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/UniversalArchiveOpenCallback.h:10`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/UniversalArchiveOpenCallback.cpp:6`
- Head-cache stream used for autodetect: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CHeadCacheInStream.h:7`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CHeadCacheInStream.cpp:117`
- Extract callback JNI adapter: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CPPToJava/CPPToJavaArchiveExtractCallback.h:7`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CPPToJava/CPPToJavaArchiveExtractCallback.cpp:59`
- Volume callback JNI adapter (`null` stream path returns `S_FALSE`): `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CPPToJava/CPPToJavaArchiveOpenVolumeCallback.cpp:41`

### Build/runtime packaging and test harness
- Android module/runtime config: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/build.gradle:7`
- Native source inclusion (7z/rar/zip handlers) and shared library target: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/CMakeLists.txt:125`, `:186`, `:201`, `:354`
- Snippet extraction/multipart examples: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/ExtractItemsStandard.java:20`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/ExtractItemsSimple.java:17`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/OpenMultipartArchive7z.java:20`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/OpenMultipartArchiveRar.java:20`
- Main extraction regression suite entries (`zip/rar/7z`, pass/header/volume): `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/AllTestSuite.java:133`, `:139`, `:148`, `:178`, `:186`, `:201`, `:278`, `:284`, `:293`, `:325`, `:333`, `:348`
- Extraction harness for format autodetect/password/header-password/volume behavior: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/ExtractFileAbstractTest.java:299`, `:336`, `:347`, `:363`, `:413`, `:475`, `:542`

## Library API usage model (interfaces, callbacks, open/extract patterns)

### 1) Initialization model
- `openInArchive(...)` always calls `ensureLibraryIsInitialized()` first. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:769`, `:808`, `:835`, `:839`
- Auto-init path uses `initSevenZipFromPlatformJAR()` once; if that fails, later opens fail with runtime exception. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:839`
- Manual init options exist: `initSevenZipFromPlatformJAR(...)` and `initLoadedLibraries()`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:360`, `:708`
- Init diagnostics/status are queryable: `isInitializedSuccessfully()`, `getLastInitializationException()`, `getUsedPlatform()`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:244`, `:254`, `:280`

### 2) Open model
- Open requires `IInStream`; this is seekable+sequential by contract. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:769`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInStream.java:9`
- Explicit format open (`archiveFormat != null`) uses one codec path, no head-cache wrapper in native open branch. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/JavaToCPP/JavaToCPPSevenZip.cpp:175`
- Autodetect open (`archiveFormat == null`) wraps stream in `CHeadCacheInStream` and iterates codecs. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/JavaToCPP/JavaToCPPSevenZip.cpp:199`, `:209`
- Open callback instance is wrapped by a native universal callback that conditionally exposes open-volume/password interfaces based on Java callback type. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/UniversalArchiveOpenCallback.cpp:37`, `:45`, `:54`

### 3) Archive inspection model
- Item inspection surface: `getNumberOfItems()`, `getProperty(index, PropID)`, `getStringProperty(index, PropID)`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:39`, `:57`, `:73`
- Archive-level inspection surface: `getArchiveProperty`, `getNumberOfArchiveProperties`, `getArchivePropertyInfo`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:143`, `:191`, `:206`
- Opened format can be read via `getArchiveFormat()`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:220`

### 4) Extraction model (callback path)
- Bulk extraction entrypoint: `extract(int[] indices, boolean testMode, IArchiveExtractCallback)`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:96`
- Callback lifecycle surface:
  - `getStream(index, askMode)` returns sink or `null` to skip item.
  - `prepareOperation(askMode)` pre-op hook.
  - `setOperationResult(result)` per-item result hook.
  - `setTotal/setCompleted` inherited progress hooks.
  Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveExtractCallback.java:21`, `:32`, `:49`, `:66`; `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IProgress.java:9`
- JNI layer maps ask/result enum indices to Java enums and forwards callback method calls. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CPPToJava/CPPToJavaArchiveExtractCallback.cpp:70`, `:101`, `:122`

### 5) Extraction model (slow path)
- Slow API: `extractSlow(index, outStream)` and `extractSlow(index, outStream, password)`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:112`, `:129`
- Both slow calls are implemented by wrapping one-item `extract(...)` internally. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/InArchiveImpl.java:119`, `:130`
- API docs warn repeated `extractSlow(...)` calls are inefficient; simple API repeats the same warning. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:100`, `:115`; `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/simple/ISimpleInArchiveItem.java:268`, `:283`

### 6) Password model
- Header/index password at open time: `openInArchive(..., String passwordForOpen)`; docs explicitly state this is for open/index, not extraction payload. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:792`, `:806`
- Extraction password paths:
  - callback path: implement `ICryptoGetTextPassword` on extract callback.
  - slow path: `extractSlow(..., password)`.
  Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveExtractCallback.java:6`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:129`
- JNI extraction callback adapter conditionally exposes `ICryptoGetTextPassword` only if Java callback implements it. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CPPToJava/CPPToJavaArchiveExtractCallback.h:30`, `:52`
- Test harness explicitly exercises password, header-password, and callback password modes. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/ExtractFileAbstractTest.java:99`, `:103`, `:111`, `:347`

### 7) Multipart model
- SevenZip class javadoc states:
  - `IArchiveOpenVolumeCallback` currently used for multipart `RAR`.
  - multipart `7z` uses `VolumedArchiveInStream`.
  Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:124`, `:125`, `:126`
- `VolumedArchiveInStream` enforces first-part naming `.7z.001` and concatenates volumes logically by stream seeks/reads. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/VolumedArchiveInStream.java:34`, `:81`, `:168`, `:214`
- `VolumedArchiveInStream.close()` is intentionally unsupported; caller-owned callback cache must close files. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/VolumedArchiveInStream.java:231`
- RAR multipart snippet uses `IArchiveOpenVolumeCallback` plus `IArchiveOpenCallback`; 7z snippet uses `VolumedArchiveInStream`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/OpenMultipartArchiveRar.java:22`, `:129`; `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/OpenMultipartArchive7z.java:18`, `:115`

### 8) Resource lifecycle model
- `IInArchive.close()` is terminal by contract. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:13`, `:22`
- `InArchiveImpl.ensureOpened()` throws `SevenZipException("InArchive closed")` after close. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/InArchiveImpl.java:405`, `:407`
- Closed-method behavior is asserted by `CallMethodsOnClosedInStreamTest`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/bug/CallMethodsOnClosedInStreamTest.java:198`, `:205`

## Format support matrix (`zip/rar/7z`) with evidence

| Format | Enum + open support | Extraction evidence | Password evidence | Multipart evidence | Spike-relevant notes |
| --- | --- | --- | --- | --- | --- |
| `zip` | `ArchiveFormat.ZIP`; explicit open is supported by `openInArchive(archiveFormat, ...)`. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ArchiveFormat.java:203`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:769` | Single/multiple extraction suites include ZIP tests. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/singlefile/ExtractSingleFileZipTest.java:5`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/multiplefiles/ExtractMultipleFileZipTest.java:5`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/AllTestSuite.java:348` | ZIP pass and pass-callback tests exist. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/singlefile/ExtractSingleFileZipPassTest.java:5`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/singlefile/ExtractSingleFileZipPassCallbackTest.java:7`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/multiplefiles/ExtractMultipleFileZipPassTest.java:5` | `UNCONFIRMED` for zip-multipart behavior in this module’s spike-relevant snippet coverage; SevenZip javadoc explicitly calls out RAR callback path and 7z stream helper, not ZIP. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:124`, `:126` | Good baseline for Spike 2 single-archive extraction and metadata capture using `PropID.PATH/SIZE/PACKED_SIZE`. |
| `rar` | `ArchiveFormat.RAR` and `ArchiveFormat.RAR5`; open via explicit format or autodetect. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ArchiveFormat.java:218`, `:223`; `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:820` | Single/multiple extraction suites include RAR and RAR5 tests. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/singlefile/ExtractSingleFileRarTest.java:5`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/multiplefiles/ExtractMultipleFileRarTest.java:5`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/AllTestSuite.java:133`, `:178`, `:278`, `:325` | Pass/header-pass/callback variants are present for RAR/RAR5. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/singlefile/ExtractSingleFileRarPassTest.java:5`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/singlefile/ExtractSingleFileRarHeaderPassTest.java:5`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/singlefile/ExtractSingleFileRarPassCallbackTest.java:9` | Multipart RAR path uses `IArchiveOpenVolumeCallback`; snippet + tests cover it. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/OpenMultipartArchiveRar.java:22`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/OpenMultipartArchiveRarTest.java:38` | Spike runs should capture explicit volume lookup behavior (`getStream(filename)`), because missing volume may surface as extraction/open failure state. |
| `7z` | `ArchiveFormat.SEVEN_ZIP`; explicit open and autodetect supported. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ArchiveFormat.java:258`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:820` | Single/multiple extraction suites include 7z tests. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/singlefile/ExtractSingleFileSevenZipTest.java:5`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/multiplefiles/ExtractMultipleFileSevenZipTest.java:5`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/AllTestSuite.java:139`, `:186`, `:284`, `:333` | 7z pass/header-pass/callback variants exist. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/singlefile/ExtractSingleFileSevenZipPassTest.java:5`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/singlefile/ExtractSingleFileSevenZipHeaderPassTest.java:5`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/singlefile/ExtractSingleFileSevenZipPassCallbackTest.java:7` | Multipart 7z path uses `VolumedArchiveInStream`; snippet + tests cover it. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/OpenMultipartArchive7z.java:18`, `:115`; `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/OpenMultipartArchive7zTest.java:38`; `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/VolumedArchiveInStream.java:34` | `PropID.IS_ANTI` is documented as 7z-only metadata for delete-on-extract semantics; caller policy still required. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/PropID.java:119` |

Additional fixture evidence for these formats:
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/simple/zip`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/simple/rar`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/simple/7z`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/multiple-files/zip`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/multiple-files/rar`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/assets/testdata/multiple-files/7z`

## Runtime requirements (JNI/native/seekability/threading)

### JNI/native + Android packaging requirements
- JNI bridge is mandatory for open/init/version operations (`nativeOpenArchive`, `nativeInitSevenZipLibrary`, version getters). `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:949`, `:955`, `:957`
- Android build declares `compileSdkVersion 36`, `minSdkVersion 21`, `targetSdkVersion 36`, and external CMake build. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/build.gradle:7`, `:9`, `:10`, `:22`
- Native library target is built as shared lib and linked with Android `log`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/CMakeLists.txt:354`, `:362`, `:364`
- 7z/rar/zip native handlers are compiled into the library target. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/CMakeLists.txt:125`, `:186`, `:201`
- Android-specific init branch in `SevenZip` bypasses platform-jar resource reads and uses in-code properties constants (`SEVENZIPJBINDING_PLATFORM_PROPERTIES`, `SEVENZIPJBINDING_LIB_PROPERTIES`). `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:206`, `:210`, `:298`, `:499`

### Seekability and stream behavior requirements
- `IInStream` extends `ISeekableStream`; open path depends on seek semantics. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInStream.java:9`
- Native autodetect head-cache init seeks to end to determine stream size. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CHeadCacheInStream.cpp:123`
- Reference input adapter uses synchronized `seek/read/close` around `RandomAccessFile`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/RandomAccessFileInStream.java:31`, `:59`, `:79`

### Threading requirements
- Stream callback contracts explicitly state calls may happen from different threads depending on format/data size. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ISeekableStream.java:31`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ISequentialInStream.java:19`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ISequentialOutStream.java:15`
- Exception model includes potential causes from other threads. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZipException.java:22`, `:429`

### Autodetect envelope constants and observed test guard
- Native open path constants: `MAX_CHECK_START_POSITION = 4 MiB`, `CHEAD_CACHE_SIZE = 16384`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/JavaToCPP/JavaToCPPSevenZip.cpp:143`, `:144`
- Performance test asserts low head reads for bad-archive autodetect harness (`<= 4`). `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/performance/HeadCacheOnAutodetectionTest.java:46`

## Error/result model and diagnostics hooks

### Error and result surfaces
- Primary checked error type is `SevenZipException`; callback exceptions are captured and rethrown through operation boundary. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveExtractCallback.java:24`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZipException.java:9`
- Extraction outcome per item is `ExtractOperationResult` (`OK`, `UNSUPPORTEDMETHOD`, `DATAERROR`, `CRCERROR`, `UNAVAILABLE`, `UNEXPECTED_END`, `DATA_AFTER_END`, `IS_NOT_ARC`, `HEADERS_ERROR`, `WRONG_PASSWORD`, fallback `UNKNOWN_OPERATION_RESULT`). `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ExtractOperationResult.java:10`
- Initialization failures use `SevenZipNativeInitializationException`; init state can be queried via `isInitializedSuccessfully()` and `getLastInitializationException()`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZipNativeInitializationException.java:10`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:244`, `:254`

### Diagnostics hooks available to caller
- Open progress hooks: `IArchiveOpenCallback.setTotal(files,bytes)` and `setCompleted(files,bytes)`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveOpenCallback.java:27`, `:45`
- Extract progress/lifecycle hooks: `setTotal`, `setCompleted`, `prepareOperation`, `getStream`, `setOperationResult`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveExtractCallback.java:32`, `:49`, `:66`
- Extended diagnostics helpers: `printStackTraceExtended()` and cause accessors (`getCauseLastThrown`, `getCauseFirstPotentialThrown`, `getCauseLastPotentialThrown`). `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZipException.java:292`, `:424`, `:435`, `:446`

### Relevant edge-case behavior from tests/native bridge
- Garbage archive test accepts occasional false positives (random bytes may accidentally resemble valid header), while expecting mostly exceptions. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/badarchive/GarbageArchiveFileTest.java:55`, `:63`
- Volume callback JNI bridge returns `S_FALSE` when `getStream(filename)` yields `null`. `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CPPToJava/CPPToJavaArchiveOpenVolumeCallback.cpp:71`, `:75`

## Recursive/flatten implications (what library does vs caller must do)

| Concern | Library behavior from source | Caller responsibility for spikes |
| --- | --- | --- |
| Recursive nested archives | No recursive extraction API exists; open/extract acts on one opened archive object at a time (`openInArchive` + `IInArchive.extract*`). Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:769`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:96` | Detect extracted archive payloads and explicitly recurse in caller loop when `recursiveUnarchive=true`; bound depth/visited set in caller policy. |
| Flatten output policy | Library emits bytes to caller-provided `ISequentialOutStream`; it does not expose flatten or destination-policy APIs. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveExtractCallback.java:14` | Implement flatten/no-flatten rules in caller output path resolver. |
| Filename collisions | No collision policy API exists in extraction interfaces. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:20` | Implement deterministic collision policy (`rename`, `overwrite`, `fail`) in caller. |
| Path traversal safety | Library exposes path metadata (`PropID.PATH`) only; no sanitizer helper in API. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/PropID.java:29` | Canonicalize/validate target paths against extraction root before writes. |
| Cleanup of source archives | No source-retention/deletion API in extraction interfaces. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:20` | Implement post-pass cleanup policy in caller orchestration, keyed by outcome. |

## Gaps/unknowns relative to spike specs

### Against Spike 2 (`spike-2-unarchive-runtime.md`)
1. Recursive traversal termination behavior is `UNCONFIRMED` from this module alone because recursion is caller-built, not library-native.
2. Flatten/collision policy behavior is `UNCONFIRMED` in library because output naming/path policy is external to `IArchiveExtractCallback`.
3. Path traversal mitigation is `UNCONFIRMED` in library; caller must provide checks.
4. Cleanup policy (delete/retain archives on success/failure) is `UNCONFIRMED` in library; no API surface for it.
5. Device-specific large archive throughput/storage behavior is `UNCONFIRMED` from source-only inspection; requires spike run measurement.
6. Zip multipart behavior relevant to Spike 2 is `UNCONFIRMED` in this module’s spike-oriented snippet/tests; explicit evidence here is for RAR and 7z multipart paths.

### Against Spike 4 (`spike-4-integration.md`)
1. Cross-stage identity from resolver/network stages is `UNCONFIRMED` here; this module only provides archive-level item metadata (`PropID.*`) and extraction callbacks.
2. Deterministic global policy ordering across ignore/selection/fetch/extract/rename is `UNCONFIRMED` here; this module does not model those pipeline stages.
3. Stage-labeled diagnostics required by integration spike are `UNCONFIRMED` as built-in behavior; caller must add stage labels around open/extract operations and callback events.
4. Remote zip selective internal download behavior is `UNCONFIRMED` here; no network/downloader code in module source.

## Reusable patterns for spike app (adopt/adapt/avoid)

### Adopt
- Prefer explicit `ArchiveFormat` when known to avoid full codec iteration path. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/JavaToCPP/JavaToCPPSevenZip.cpp:175`, `:209`
- Use callback extraction (`extract(indices, false, callback)`) for per-item diagnostics (`askMode`, result) and progress hooks. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:96`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveExtractCallback.java:32`
- Log and persist `getArchiveFormat()` + `PropID.PATH/SIZE/PACKED_SIZE/ENCRYPTED` for integration identity traces. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:220`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/PropID.java:35`, `:60`, `:68`, `:111`
- Enforce explicit close discipline (`IInArchive.close()` and callback-owned stream handles). Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:29`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/VolumedArchiveInStream.java:231`

### Adapt
- For multipart archives, branch by format:
  - `7z`: `VolumedArchiveInStream`.
  - `rar`: `IArchiveOpenVolumeCallback` (optionally combined with open/password callback object).
  Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:124`, `:126`; `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/ExtractFileAbstractTest.java:336`, `:340`, `:343`, `:413`
- Adapt password strategy by archive case:
  - open/header password via `openInArchive(..., String)` or open callback implementing `ICryptoGetTextPassword`.
  - extract password via extract callback implementing `ICryptoGetTextPassword` or `extractSlow(..., password)`.
  Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:792`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:129`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/ExtractFileAbstractTest.java:347`, `:475`

### Avoid
- Avoid repeated `extractSlow(...)` for bulk extraction workloads. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:100`, `:115`
- Avoid unsynchronized custom stream implementations where concurrent callback invocations are possible. Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ISeekableStream.java:31`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ISequentialOutStream.java:15`
- Avoid assuming library-native policy for recursion, flattening, collision handling, traversal prevention, or cleanup; those are caller-owned.

## Evidence checklist additions for spike runs

1. Capture init envelope per run:
- `SevenZip.getSevenZipJBindingVersion()`
- `SevenZip.getSevenZipVersion()`
- `SevenZip.isInitializedSuccessfully()`
- `SevenZip.getLastInitializationException()` when false
- `SevenZip.getUsedPlatform()`
Evidence APIs: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:244`, `:254`, `:280`, `:974`, `:994`

2. For each archive open attempt, log:
- requested format (`explicit` vs `null` autodetect)
- resulting `inArchive.getArchiveFormat()`
- open progress callbacks (`setTotal`, `setCompleted`)
Evidence APIs: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java:820`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveOpenCallback.java:27`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java:220`

3. During extraction, log per item:
- archive index
- `PropID.PATH`, `PropID.SIZE`, `PropID.PACKED_SIZE`, `PropID.ENCRYPTED`, `PropID.IS_FOLDER`
- `ExtractAskMode`
- `ExtractOperationResult`
Evidence APIs: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/PropID.java:35`, `:53`, `:60`, `:68`, `:111`; `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ExtractAskMode.java:14`; `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ExtractOperationResult.java:14`

4. Capture callback exception envelopes with `SevenZipException.printStackTraceExtended()` and persist:
- first thrown cause
- last thrown cause
- first/last potential causes
Evidence: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZipException.java:292`, `:424`, `:435`, `:446`

5. For multipart runs, capture volume resolution traces:
- each `IArchiveOpenVolumeCallback.getStream(filename)` request
- whether volume stream was resolved or missing (`null`)
Evidence contracts: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveOpenVolumeCallback.java:52`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CPPToJava/CPPToJavaArchiveOpenVolumeCallback.cpp:71`

6. For Spike 2/4 caller-policy concerns (recursion, flatten, collision, traversal, cleanup), record explicit caller decision logs because library APIs do not encode these policies.

7. Add bad-input run evidence:
- include bad-archive attempts and capture whether failures were exceptions or rare false positives
- include autodetect head-read counters where possible
Evidence tests: `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/badarchive/GarbageArchiveFileTest.java:50`, `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/performance/HeadCacheOnAutodetectionTest.java:35`

## Appendix: concise file/symbol index with absolute paths

- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/SevenZip.java`
  - `openInArchive*`, `ensureLibraryIsInitialized`, `initSevenZipFromPlatformJAR*`, `initLoadedLibraries`, `nativeOpenArchive`, `getSevenZipVersion`, `getSevenZipJBindingVersion`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IInArchive.java`
  - `extract`, `extractSlow`, `getProperty`, `getArchiveFormat`, `close`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveExtractCallback.java`
  - `getStream`, `prepareOperation`, `setOperationResult`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveOpenCallback.java`
  - `setTotal`, `setCompleted`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/IArchiveOpenVolumeCallback.java`
  - `getProperty`, `getStream`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ArchiveFormat.java`
  - `ZIP`, `RAR`, `RAR5`, `SEVEN_ZIP`, `isOutArchiveSupported`, `supportMultipleFiles`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/PropID.java`
  - `PATH`, `SIZE`, `PACKED_SIZE`, `IS_FOLDER`, `ENCRYPTED`, `IS_ANTI`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ExtractOperationResult.java`
  - `OK`, `WRONG_PASSWORD`, other extract status values
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/ExtractAskMode.java`
  - `EXTRACT`, `TEST`, `SKIP`
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/InArchiveImpl.java`
  - slow-extract wrapper, close guard (`InArchive closed`)
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/RandomAccessFileInStream.java`
  - synchronized seek/read adapter
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/java/net/sf/sevenzipjbinding/impl/VolumedArchiveInStream.java`
  - `.7z.001` contract, volume seek/read, close behavior
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/JavaToCPP/JavaToCPPSevenZip.cpp`
  - native open path, codec loop, head-cache constants
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CHeadCacheInStream.h`
  - head-cache stream interface
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CHeadCacheInStream.cpp`
  - cache init/seek/read behavior
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/UniversalArchiveOpenCallback.h`
  - native callback multiplexer interface
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/UniversalArchiveOpenCallback.cpp`
  - QueryInterface behavior for open-volume/password callbacks
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CPPToJava/CPPToJavaArchiveExtractCallback.cpp`
  - JNI forwarding of extract callback methods
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/main/cpp/jbinding-cpp/CPPToJava/CPPToJavaArchiveOpenVolumeCallback.cpp`
  - JNI forwarding of volume callback methods
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/build.gradle`
  - Android SDK levels and external native build
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/CMakeLists.txt`
  - included 7z/rar/zip native handlers and shared target
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/ExtractItemsStandard.java`
  - callback extraction example
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/ExtractItemsSimple.java`
  - slow extraction example
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/OpenMultipartArchive7z.java`
  - multipart 7z example
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/snippets/OpenMultipartArchiveRar.java`
  - multipart rar example
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/ExtractFileAbstractTest.java`
  - extraction harness for autodetect/password/header-password/volumes
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/AllTestSuite.java`
  - suite membership for `zip/rar/7z` extraction coverage
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/badarchive/GarbageArchiveFileTest.java`
  - bad archive behavior expectations
- `/Users/nskaria/projects/romulus/references/7-Zip-JBinding-4Android/sevenzipjbinding/src/androidTest/java/net/sf/sevenzipjbinding/junit/performance/HeadCacheOnAutodetectionTest.java`
  - head-cache read-count guard
