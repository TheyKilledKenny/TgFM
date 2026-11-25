# Telegram File Manager | Functional and Technical Specification

Telegram File Manager (TgFM) lets Android users browse attachments in their Telegram channels and groups, select them manually or by applying rules, save them on their phone, share them with other apps, and send them to an FTP server. The app connects to the user's personal Telegram account through TDLib. Reading messages, selecting attachments, and transferring files all take place on the device.

## Sign-in, language, and navigation

On first launch, open **Settings**. To connect to Telegram, the user must grant file access and enter the API ID and API hash obtained from `my.telegram.org`. The ID is a positive integer and the hash consists of 32 hexadecimal digits. Ask for the phone number with its international dialing code, the verification code, and any additional steps Telegram requires, including a two-step verification password, email verification, or confirmation on another device. While waiting for the code, allow the user to request another one. Direct users to the official Telegram app if Telegram requires registration or a purchase. Display sign-in errors in the Telegram section.

On subsequent launches, use the saved session. Explicitly starting credential setup again creates a new session. Ask for confirmation before logging out. If the session is authorized and a favorite is active, open **Selection**. If either prerequisite is missing, open **Settings** when the user tries to open Selection. A temporary network outage with a valid session must still allow the user to browse results already read.

Use the first supported language in Android's ordered list of preferred languages. Support Italian and English, with English as the fallback. Settings offers *System language*, *Italiano*, and *English*. Persist the choice across restarts and synchronize it with Android's per-app language settings on Android 13 and later. Localize the interface, accessibility labels, dialogs, app messages, dates, and file sizes. Preserve names, captions, saved criteria, and placeholders as entered.

| Bottom navigation order | Content and actions |
|---|---|
| Selection | Active channel, period, read action, view, search, selection, and details. Fixed Save, Share, and Save to FTP buttons |
| Downloads | Local folder and files, list refresh, selection, open, share, FTP, and individual or bulk deletion |
| Filters | Rules by favorite, manual editor, examples, and confirmation of proposed rules |
| Settings | Language, account, favorites, file access, working folder, cache, archive handling, and FTP server |

Keep selection and transfer controls above the bottom navigation bar even when lists are empty. Disable actions that require selected files when the selection is empty. Provide a way back from each secondary view. During an operation, show progress, error details, and a cancel action.

## Data model and persistence

| Object | Identity and data |
|---|---|
| Channel or group | Telegram ID, title, and profile picture when available. Each favorite has a profile and can have several rules. Only one favorite is active at a time |
| Favorite profile | Cover images on or off, initial period, and a three-way archive preference. Defaults are off, Today, and Use general setting, respectively |
| Attachment | Composite key consisting of chat ID, message ID, and TDLib file ID. Name, MIME type, category, size, posting date, caption, and optional archive volumes, cover image, and preview of the attachment itself |
| Rule | UUID, favorite ID, title, enabled state, include or exclude action, pattern, substring, set of extensions, and optional MIME type and category |
| Local file | Path, original name, MIME type, length, and attachment key if known. The private download index maps paths to metadata and updates atomically |

Store favorites, the active channel, rules, the attachment view, and general preferences in DataStore. Keep the folder path, language preference, and credentials in their respective configuration stores. Encrypt Telegram credentials and FTP configuration with AES-GCM using a key held in Android Keystore. Generate a random 32-byte key for the TDLib database of each new session. Exclude app data from Android backups. Keep the list of posts read, manual selection, and scan progress in memory.

## Favorites and message reading

1. Load the main and archived chat lists through TDLib. Request pages of 100 chats until each list is exhausted. Include accessible channels, supergroups, and basic groups. Deduplicate by ID, sort by title, and support search.
2. In Settings, tapping a profile picture with its checkbox adds or removes the favorite. Use the initial when no picture is available. If no favorite is active, make the first one added active. Refresh titles while preserving profiles and rules. Removing a favorite deletes its rules. Ask for confirmation once if it has any rules. If the active favorite is removed, activate the first remaining one.
3. Each row's settings action lets the user choose cover images, the initial period (*Today*, *Last 7 days*, *All files*), and how to send archives (*Use general setting*, *Original archive*, *Archive contents*). In Selection, show the active favorite's picture and name and allow switching favorites. Separate the header from the reading options with a divider. On a channel change, clear the results and selection and apply the new channel's profile.
4. **Read** queries the active channel. For an individual scan, also allow a specific day or an inclusive date range. Interpret dates in the device's time zone, from the start of the first day up to, but excluding, the start of the day after the last. Last 7 days includes today and the preceding six days. Reject reversed ranges and dates outside the selectable years 1970 to 2037.
5. For a bounded period, use `getChatMessageByDate` to locate the last message before the end boundary. Read backward with `getChatHistory` in pages of 100, deduplicating overlapping results by attachment key. Stop at the start boundary, the end of the history, or when the cursor stops advancing. For *All files*, start with the most recent message. Display each page's results before requesting the next. Cancel keeps the attachments already read and marks the scan as partial. A new scan rebuilds the list.
6. Extract accessible file metadata from documents, photos, audio and voice messages, videos and video messages, animations, and stickers, including those within composite Telegram content. Ignore thumbnails as standalone attachments. For a photo, select the variant with the largest pixel area as the selectable file. Use the declared name, MIME type, and size when available. Otherwise, derive a stable name from the category and identifiers, infer the MIME type from the extension, and use the estimated size.

## File list, cover images, and details

Group archive volumes before classifying files. The three views show selected files, selected files plus possible matches, or all files. The initial view is All files. In every view, show selected files first and preserve scan order within the selected and unselected groups. Search across files, examples, and channels is case-insensitive, treats spaces, hyphens, and underscores as equivalent separators, and requires all search terms to be present. Search changes visibility. *Select all* and *Deselect all* act on the entire eligible list, including hidden rows.

When cover images are enabled, associate a message's photo with an eligible document, audio file, or video in the immediately following message in the same chat. An intervening message breaks the association. For a grouped archive, use the cover of the first part posted. Hide a photo used as a cover and exclude it from both manual and automatic selection. When cover images are disabled, the photo becomes a standalone attachment again.

The left column of each row controls selection. For a file with a cover, show the thumbnail with a check mark overlay to indicate selection. Otherwise, show a checkbox. Keep the control in a consistent position for rows without photos. Tapping the rest of the row opens the details. Show the name, size, date, and match indicator. In the details, also show the category, extension, file caption, and cover caption if it differs.

An image attachment's own preview works even when cover images are disabled or the image was sent as a document. Recognize images by the photo or animation category, an image MIME type, or an image extension, including JPEG, PNG, GIF, WebP, BMP, HEIF, HEIC, AVIF, and TIFF. Use the saved copy if it can be decoded. Otherwise, use Telegram's thumbnail or embedded minithumbnail. Request only visible previews, allow at most two concurrent thumbnail loads, and scale the bitmap to a maximum side length of 400 pixels. Indicate when no decodable image is available. Opening details does not start saving the original.

## Filters

Group rules by channel and allow the user to collapse or expand one group or all groups. Require confirmation to create, edit, enable, disable, or delete a rule. The editor requires a favorite, a title, and at least one criterion. Show how many attachments already read from that channel match the rule.

- **Name.** Match the pattern against the entire filename without its extension, ignoring case. Preserve numeric dates with dot separators at the end of the name. Treat pattern text literally except for `{data}`. The *Exact prefix before the date* field places the prefix, including the separator entered by the user, immediately before `{data}`. The *Contains text* criterion searches for a substring in the filename without its extension.
- **Date in the name.** Recognize a day followed by an Italian or English month name, including abbreviations, with an optional year. Also recognize numeric year-month-day or day-month-year dates, or eight consecutive digits. Accepted separators are a period, space, hyphen, or underscore. Matching is syntactic. For example, `La_Gente_{data}` matches `La_Gente_14_Novembre_2026.epub` and excludes `La_Gente_Roma_14_Novembre_2026.pdf` from definite matches.
- **Type.** Extensions are alternatives, separated by commas and normalized by removing the leading period. An empty list accepts any extension. The MIME criterion accepts a complete value or a family such as `image/*`. Telegram category is an independent criterion. An inclusion rule with only the extension `pdf` includes every PDF.
- **Combining rules.** Apply the channel's enabled rules. All specified criteria within a rule must match. A matching exclusion takes precedence. Otherwise, one matching inclusion is sufficient to classify a file as a definite match and select it. Possible matches and unmatched files remain available for manual selection.
- **Possible matches.** Evaluate only valid inclusion rules compatible with the file type. For a pattern, normalize the prefix before the date and strip trailing separators. If the prefix is at least four characters long, classify the file as a possible match if its name contains the prefix or the start of its name has a similarity of at least 0.80 to it. For a text-only criterion, compare at least four characters with the filename without its extension using a threshold of 0.75. Similarity is one minus the Levenshtein distance divided by the longer of the two lengths.
- **From examples.** Allow users to search, mark wanted and unwanted files, show only marked files, and clear the labels. Replace recognizable dates with `{data}` and group by pattern and type criteria. When there are positive examples, propose inclusions and use negative examples to reject ambiguous proposals. With only negative examples, propose exclusions. Flag conflicting examples and names without a date that can be generalized. In the confirmation view, show only files that would be selected, taking saved rules into account as well. Deduplicate equivalent rules and recalculate the selection after saving.

## Working folder, downloads, and sharing

Use `Download/TgFM` initially. The folder picker accepts a readable and writable local subfolder on shared storage or an SD card. Verify the canonical path and exclude the volume root, the Android directory tree, and the app cache. TDLib requires a local path. Request special all-files access on Android 11 and later and shared-storage write permission on Android 8 through 10. Recheck access when returning from system settings. Changing folders reopens TDLib at the new location and leaves existing files in their original folder.

TDLib receives file data under `.tgfm-cache/tdlib` in the chosen folder. On completion, atomically move the file from the cache to its destination on the same filesystem. Clearing the cache is an explicit action available when no transfers are running. In Downloads, list by name the visible regular files directly inside the current folder, including files without TgFM metadata. Support refresh, multiple selection, opening a file via an icon, and confirmed deletion of one file or the selection.

| Action | Effect |
|---|---|
| Save from Selection | Transfers selected files to the working folder. For an archive, keeps the original archive files, including all volumes |
| Share from Selection | Finishes saving the entire selection and opens Android sharing. If a file is missing, keeps completed files and reports the problem |
| Share from Downloads | Grants access to the selected local files or their archive entries according to the confirmed choice |
| Save to FTP | Sends the selected Telegram attachments or local files to the configured server after confirmation of the destination and whether to keep local copies |

For ordinary downloads, run up to four transfers concurrently and serialize operations on the same file ID. Before downloading, verify that the message still contains the attachment. Reuse a copy with the same source key and a compatible size. The *Delete previous downloads* option is off by default. When enabled, it removes only indexed files in the current folder before Save or Share. For an ordinary overwrite, delete the existing file of the same name at the start. A failure can therefore leave the destination without a copy. Reserve distinct destinations for concurrent transfers, replace invalid characters, limit filenames to 180 UTF-8 bytes, and append a number before the extension when a unique name is needed.

Show the completed count, total, current activity, and byte counts per file when available. Keep successful transfers after cancellation or failure. For an ordinary queue, allow retrying only incomplete files without repeating the initial cleanup. Selection in Downloads is independent of the Telegram message selection.

Open a file with `ACTION_VIEW`. For sharing, use `ACTION_SEND` with one URI or `ACTION_SEND_MULTIPLE` with several URIs. Include `EXTRA_STREAM`, `ClipData`, and temporary read permission. Use the common MIME type if all types are identical, otherwise use `*/*`. Providers expose `content://` URIs, validate paths, and allow read-only access. Handle the absence of a compatible app. Local deletion affects only the device.

## Archives

Recognize ZIP, RAR, 7z, TAR, TAR.GZ, TGZ, TAR.BZ2, and TAR.XZ. Recognize multipart families using `.partN.rar`, `.r00` onward with an initial RAR file, `.z01` onward with a final ZIP file, and sequentially numbered `.7z.001` and `.zip.001` volumes. Group by chat, case-insensitive base name, and numbering scheme. Show a single row with the logical name and combined size. If volume names are ambiguous, keep individual parts visible and ask the user to narrow the period before attempting an ambiguous transfer.

Before downloading, if an archive's name would collide with a different local file, suggest an available name that the user can edit. Preserve extensions and volume numbering. Reject invalid names and names already in use. Process one archive family at a time, downloading its volumes in parallel. To read the contents, check available space, required starting or ending volumes, and continuity of the volume numbering.

For Share and FTP, offer a single choice between originals and contents for the operation. The user can override the suggested choice. The general preference defaults to originals. In Selection, use the channel profile. In Downloads, look up the profile using the source key of the selected archives. Use the general preference for unknown sources or if effective choices differ.

In originals mode, send each volume in the family once. In contents mode, read entries sequentially from complete local archives. Skip directories, validate relative paths, and reject absolute paths and parent-directory traversal. Report missing volumes, unreadable or encrypted content, or the absence of usable files. For sharing, expose one read-only URI per entry and decompress on demand through the provider. For FTP, write each entry to its relative remote path under the chosen destination. Avoid extraction folders on the phone. Keep volumes available while reading.

## FTP and FTPS

Configure a host without a scheme or path, a port from 1 to 65535 (default 21), username, password, base folder (default `/`), explicit TLS, and local retention. Explicit TLS and local retention are both off by default. Use passive mode and binary transfer. With FTPS, verify the certificate and hostname and protect the data channel. The connection check tests connectivity, authentication, and access to the base folder. Write permission is verified during upload.

In the confirmation dialog, show the destination, allow a relative subfolder with one or more levels, and initialize *Keep local copy* from the saved preference. Create permitted directory levels on the server under the base folder. Choices in the dialog apply to that transfer only. FTP operates independently of the initial download cleanup.

- For local files, read the existing copy. For ordinary Telegram files, when the destination has no subfolder and the batch contains no archives, reuse the local file if possible. Otherwise, begin uploading while TDLib downloads, reading only the contiguous bytes confirmed by TDLib. Use a 256 KiB buffer. For archives or destinations with a subfolder, complete the local save first.
- Space for the whole file is required. When checking capacity, reserve 16 MiB of free space in addition to known file sizes. With retention enabled, allow up to four ordinary transfers and keep the copies. With retention disabled, process one file at a time and stop the queue on the first error.
- Overwrite directly on the server. Require a positive final response and verify the size when the server supports `SIZE`. For local sources, verify that the length and modification time are consistent before deleting. For an archive, delete its volumes only after the entire transfer succeeds and only if retention is disabled.
- Handle access refusal, inaccessible folders, insufficient space, interruptions, and size mismatches. A failure can leave a partial file on the phone or server. For transfers using archive or subfolder handling, ask for confirmation before attempting to delete the affected remote paths. Deleting a file that overwrote another file of the same name does not restore the original.

## Implementation and build requirements

| Component | Requirement |
|---|---|
| Android application | Package `it.tgfm`, ABI `arm64-v8a`, minimum API 26, compile API 36, target API 35 |
| Interface and state | Kotlin, Jetpack Compose, Material 3, ViewModel, StateFlow, and coroutines. Run network and disk operations off the UI thread. Use a data transfer foreground service with a notification providing access to progress |
| Telegram | Official JSONJava binding and native TDLib library from the same revision. Private session database and file cache on the working volume |
| Files and networking | Zip4j for ZIP, libarchive for other formats, Apache Commons Net for FTP and FTPS. Separate Android providers for local files and archive entries, with temporary per-URI permissions |
| Toolchain | Gradle Wrapper 8.13, Android Gradle Plugin 8.13.2, Kotlin and Compose plugin 2.2.21, JVM 17 bytecode, NDK 27.2.12479018 |

The standard build uses the ARM64 TDLib library included in the project. Run `./gradlew :app:assembleDebug` for the test APK and `./gradlew :app:assembleRelease` for the distribution variant. An update that installs over the existing app requires the same application ID and a compatible signature. Keep signing keys and passwords outside the source tree.
