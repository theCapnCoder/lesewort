package com.example.data.parser

import java.io.File
import java.util.*
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min

/**
 * Parser for EPUB format books.
 * Handles extraction of metadata, chapters, and cover images from EPUB files.
 */
class EpubParser(
    private val textProcessor: TextProcessor = TextProcessor(),
    private val chapterManager: ChapterManager = ChapterManager(),
    private val timestampUtils: TimestampUtils = TimestampUtils()
) {

    /**
     * Parses an EPUB file and returns a ParsedBook.
     *
     * @param file The EPUB file to parse
     * @return ParsedBook containing metadata and chapters, or null if parsing failed
     */
    fun parseEpub(file: File): ParsedBook? {
        var zipFile: ZipFile? = null
        return try {
            zipFile = ZipFile(file)

            // 1. Parse container.xml to locate the OPF file path
            val containerEntry = zipFile.getEntry("META-INF/container.xml") ?: return null
            val containerContent = zipFile.getInputStream(containerEntry).bufferedReader().readText()
            val opfPath = Regex("""full-path="([^"]+)"""").find(containerContent)?.groupValues?.get(1) ?: return null

            val opfParent = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

            // 2. Read OPF file content
            val opfEntry = zipFile.getEntry(opfPath) ?: return null
            val opfContent = zipFile.getInputStream(opfEntry).bufferedReader().readText()

            // 3. Extract metadata Info
            var bookTitle = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(opfContent)?.groupValues?.get(1)?.trim() ?: ""
            bookTitle = bookTitle.replace(Regex("<[^>]*>"), "")
            if (bookTitle.isEmpty()) {
                bookTitle = file.name.substringBeforeLast(".")
            }

            var author = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(opfContent)?.groupValues?.get(1)?.trim() ?: ""
            author = author.replace(Regex("<[^>]*>"), "")
            if (author.isEmpty()) {
                author = "Unknown Author"
            }

            // 4. Parse manifest and spine items
            val manifestItems = mutableMapOf<String, ManifestItem>()
            val itemPattern = Regex("""<item\s+([^>]+)""", RegexOption.IGNORE_CASE)
            val attrPattern = Regex("""(\w+)\s*=\s*"([^"]+)"""")

            itemPattern.findAll(opfContent).forEach { match ->
                val attrsAttr = match.groupValues[1]
                val attrs = attrPattern.findAll(attrsAttr).associate { it.groupValues[1] to it.groupValues[2] }
                val id = attrs["id"]
                val href = attrs["href"]
                val properties = attrs["properties"] ?: ""
                val mediaType = attrs["media-type"] ?: ""
                if (id != null && href != null) {
                    manifestItems[id] = ManifestItem(id, href, properties, mediaType)
                }
            }

            val spineIds = mutableListOf<String>()
            val itemrefPattern = Regex("""<itemref\s+([^>]+)""", RegexOption.IGNORE_CASE)
            itemrefPattern.findAll(opfContent).forEach { match ->
                val attrsAttr = match.groupValues[1]
                val attrs = attrPattern.findAll(attrsAttr).associate { it.groupValues[1] to it.groupValues[2] }
                val idref = attrs["idref"]
                if (idref != null) {
                    spineIds.add(idref)
                }
            }

            // 5. Read Navigation Maps (EPUB3 nav -> EPUB2 toc.ncx)
            val navMap = mutableMapOf<String, String>() // normalized_href_within_epub -> Title

            // Priority 1: EPUB3 Navigation (<nav>)
            val navItem = manifestItems.values.find { it.properties.contains("nav") }
            if (navItem != null) {
                val navFullPath = opfParent + navItem.href
                val navEntry = zipFile.getEntry(navFullPath)
                if (navEntry != null) {
                    val navContent = zipFile.getInputStream(navEntry).bufferedReader().readText()
                    // Extract <a href="href">title</a>
                    val aPattern = Regex("""<a\s+[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    aPattern.findAll(navContent).forEach { aMatch ->
                        val rawHref = aMatch.groupValues[1]
                        val fileHref = rawHref.substringBefore("#")
                        val text = aMatch.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                        if (fileHref.isNotEmpty() && text.isNotEmpty()) {
                            val navParent = if (navItem.href.contains("/")) navItem.href.substringBeforeLast("/") + "/" else ""
                            val resolvedHref = chapterManager.normalizePath(navParent + fileHref)
                            navMap[resolvedHref] = text
                        }
                    }
                }
            }

            // Priority 2: EPUB2 NCX (toc.ncx)
            if (navMap.isEmpty()) {
                val ncxItem = manifestItems.values.find { it.mediaType == "application/x-dtbncx+xml" || it.href.endsWith(".ncx") }
                if (ncxItem != null) {
                    val ncxFullPath = opfParent + ncxItem.href
                    val ncxEntry = zipFile.getEntry(ncxFullPath)
                    if (ncxEntry != null) {
                        val ncxContent = zipFile.getInputStream(ncxEntry).bufferedReader().readText()
                        val labelPattern = Regex("""<navPoint[^>]*>.*?<text[^>]*>(.*?)</text>.*?<content[^>]*src="([^"]+)"[^>]*>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                        labelPattern.findAll(ncxContent).forEach { match ->
                            val labelText = match.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                            val rawSrc = match.groupValues[2]
                            val fileSrc = rawSrc.substringBefore("#")
                            val ncxParent = if (ncxItem.href.contains("/")) ncxItem.href.substringBeforeLast("/") + "/" else ""
                            val resolvedSrc = chapterManager.normalizePath(ncxParent + fileSrc)
                            if (fileSrc.isNotEmpty() && labelText.isNotEmpty()) {
                                navMap[resolvedSrc] = labelText
                            }
                        }
                    }
                }
            }

            // 6. Build list of parsed chapters
            val rawChapters = mutableListOf<ParsedChapter>()
            for (idref in spineIds) {
                val manifestItem = manifestItems[idref] ?: continue
                val fileFullPath = opfParent + manifestItem.href
                val entry = zipFile.getEntry(fileFullPath) ?: continue
                val content = zipFile.getInputStream(entry).bufferedReader().readText()

                // Calculate chapter title
                val relativePathFromRoot = chapterManager.normalizePath(opfParent + manifestItem.href)
                var chapterTitle = navMap[relativePathFromRoot] ?: navMap[manifestItem.href]
                if (chapterTitle.isNullOrBlank()) {
                    chapterTitle = chapterManager.extractHeading(content)
                }

                val cleanText = textProcessor.cleanHtml(content)
                // Only include chapters that have readable text
                if (cleanText.isNotEmpty()) {
                    val lines = cleanText.lines().map { it.trim() }.filter { it.isNotEmpty() }

                    // If currently chapterTitle is null/empty or generic, look for a better one inside the content!
                    if (chapterTitle.isNullOrBlank() || chapterManager.isGenericTitle(chapterTitle, bookTitle)) {
                        val extractedTitle = chapterManager.extractTitleFromLines(lines, bookTitle, chapterTitle ?: "")
                        if (!extractedTitle.isNullOrBlank()) {
                            chapterTitle = extractedTitle
                        }
                    }

                    if (chapterTitle.isNullOrBlank()) {
                        chapterTitle = ""
                    }

                    rawChapters.add(ParsedChapter(chapterTitle, cleanText))
                }
            }

            // 7. Extract cover bytes if present - COMPREHENSIVE MULTI-STAGE EXTRACTOR (Moon Reader style)
            var coverBytes: ByteArray? = null
            try {
                // Track checked paths to avoid double reading
                val searchedPaths = mutableSetOf<String>()

                // Helper to check if a specific relative path (from opf) exists in Zip and load it
                fun tryLoadCoverByHref(href: String?): Boolean {
                    if (href == null || href.isBlank()) return false
                    val resolvedPath = chapterManager.normalizePath(opfParent + href)
                    if (searchedPaths.contains(resolvedPath)) return false
                    searchedPaths.add(resolvedPath)

                    val entry = zipFile.getEntry(resolvedPath)
                    if (entry != null && !entry.isDirectory) {
                        try {
                            zipFile.getInputStream(entry).use { input ->
                                val bytes = input.readBytes()
                                if (bytes.size > 1024) { // Ignore tiny placeholders/pixels
                                    coverBytes = bytes
                                    return true
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Fallback: search zip case-insensitively for this file path
                    val lowerPath = resolvedPath.lowercase()
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (e.name.lowercase() == lowerPath && !e.isDirectory) {
                            try {
                                zipFile.getInputStream(e).use { input ->
                                    val bytes = input.readBytes()
                                    if (bytes.size > 1024) {
                                        coverBytes = bytes
                                        return true
                                    }
                                }
                            } catch (err: Exception) {
                                err.printStackTrace()
                            }
                        }
                    }
                    return false
                }

                // Stage 1: Check EPUB 3 cover-image property in manifest
                val coverPropertyItem = manifestItems.values.find {
                    it.properties.lowercase().contains("cover-image")
                }
                if (coverPropertyItem != null && tryLoadCoverByHref(coverPropertyItem.href)) {
                    // Success!
                }

                // Stage 2: Check EPUB 2 standard <meta name="cover" content="id" /> or variants
                if (coverBytes == null) {
                    val metaTags = Regex("""<meta\s+([^>]+)""", RegexOption.IGNORE_CASE)
                    val attrPattern = Regex("""(\w+)\s*=\s*"([^"]+)"""")
                    metaTags.findAll(opfContent).forEach { match ->
                        if (coverBytes == null) {
                            val attrsStr = match.groupValues[1]
                            val attrs = attrPattern.findAll(attrsStr).associate {
                                it.groupValues[1].lowercase() to it.groupValues[2]
                            }
                            val isCoverMeta = attrs["name"]?.lowercase() == "cover" ||
                                              attrs["property"]?.lowercase() == "cover" ||
                                              attrs["id"]?.lowercase() == "cover"
                            if (isCoverMeta) {
                                val contentId = attrs["content"]
                                if (contentId != null) {
                                    val item = manifestItems[contentId] ?: manifestItems.values.find { it.id.lowercase() == contentId.lowercase() }
                                    if (item != null && tryLoadCoverByHref(item.href)) {
                                        // Success!
                                    }
                                }
                            }
                        }
                    }
                }

                // Stage 3: Check Manifest Item IDs for common cover patterns
                if (coverBytes == null) {
                    val probableIds = listOf("cover", "cover-image", "cover_image", "book-cover", "epub-cover", "thumbnail", "thumb", "jacket")
                    for (id in probableIds) {
                        if (coverBytes != null) break
                        val item = manifestItems.values.find { it.id.lowercase() == id }
                        if (item != null && tryLoadCoverByHref(item.href)) {
                            // Success!
                        }
                    }
                }

                // Stage 4: Check Manifest Item Hrefs for filenames containing cover, jacket, front, title
                if (coverBytes == null) {
                    val item = manifestItems.values.find {
                        val file = it.href.substringAfterLast("/").lowercase()
                        (file.contains("cover") || file.contains("jacket") || file.contains("front") || file.contains("title")) &&
                        (it.mediaType.lowercase().startsWith("image/") || file.endsWith(".jpg") || file.endsWith(".jpeg") || file.endsWith(".png") || file.endsWith(".webp"))
                    }
                    if (item != null) {
                        tryLoadCoverByHref(item.href)
                    }
                }

                // Stage 5: Cover HTML page (parse first spine items/cover page for inner images)
                if (coverBytes == null) {
                    val coverHtmlItem = manifestItems.values.find {
                        val file = it.href.lowercase()
                        (file.contains("cover") || file.contains("titlepage") || file.contains("title_page")) &&
                        (it.mediaType.lowercase().contains("xml") || it.mediaType.lowercase().contains("html") || file.endsWith(".xhtml") || file.endsWith(".html"))
                    }
                    if (coverHtmlItem != null) {
                        val htmlPath = chapterManager.normalizePath(opfParent + coverHtmlItem.href)
                        val htmlEntry = zipFile.getEntry(htmlPath)
                        if (htmlEntry != null) {
                            val htmlContent = zipFile.getInputStream(htmlEntry).bufferedReader().readText()
                            val imgMatches = Regex("""<img[^>]+src="([^"]+)"""", RegexOption.IGNORE_CASE).findAll(htmlContent)
                            val svgMatches = Regex("""<image[^>]+(?:xlink:href|href)="([^"]+)"""", RegexOption.IGNORE_CASE).findAll(htmlContent)

                            val extractedHrefs = mutableListOf<String>()
                            imgMatches.forEach { extractedHrefs.add(it.groupValues[1]) }
                            svgMatches.forEach { extractedHrefs.add(it.groupValues[1]) }

                            val htmlParent = if (coverHtmlItem.href.contains("/")) coverHtmlItem.href.substringBeforeLast("/") + "/" else ""
                            for (extractedHref in extractedHrefs) {
                                val cleanHref = extractedHref.trim()
                                val resolvedImageHref = chapterManager.normalizePath(htmlParent + cleanHref)
                                if (tryLoadCoverByHref(resolvedImageHref)) {
                                    break
                                }
                            }
                        }
                    }
                }

                // Stage 6: Scan all ZIP file entry names directly (Fallback)
                if (coverBytes == null) {
                    val entries = zipFile.entries()
                    val fallbackCandidates = mutableListOf<java.util.zip.ZipEntry>()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory) {
                            val nameLower = entry.name.lowercase()
                            val filename = nameLower.substringAfterLast("/")
                            val isImage = nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp")
                            if (isImage) {
                                if (filename.contains("cover") || filename.contains("jacket") || filename.contains("front") || filename.contains("titlepage") || filename.contains("title_page") || filename.contains("book_art")) {
                                    fallbackCandidates.add(entry)
                                }
                            }
                        }
                    }
                    fallbackCandidates.sortBy { it.name.length }
                    for (entry in fallbackCandidates) {
                        try {
                            zipFile.getInputStream(entry).use { input ->
                                val bytes = input.readBytes()
                                if (bytes.size > 2048) { // Prefer a real image over tiny UI buttons
                                    coverBytes = bytes
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Stage 7: Last Fallback - first matching image in manifest
                if (coverBytes == null) {
                    val imageItems = manifestItems.values.filter {
                        it.mediaType.lowercase().startsWith("image/") ||
                        it.href.lowercase().let { href -> href.endsWith(".jpg") || href.endsWith(".jpeg") || href.endsWith(".png") || href.endsWith(".webp") }
                    }
                    val sortedImages = imageItems.sortedWith(compareByDescending {
                        val file = it.href.lowercase()
                        file.contains("art") || file.contains("title") || file.contains("book") || file.contains("page")
                    })
                    for (item in sortedImages) {
                        if (tryLoadCoverByHref(item.href)) {
                            break
                        }
                    }
                }

                // Stage 8: Extreme Fallback - largest image found anywhere in the ZIP archive (larger than 15KB)
                if (coverBytes == null) {
                    val entries = zipFile.entries()
                    var bestCandidate: java.util.zip.ZipEntry? = null
                    var maxCandidateSize = 0L
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory) {
                            val nameLower = entry.name.lowercase()
                            val isImage = nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp")
                            if (isImage && entry.size > 15360) { // > 15KB
                                if (bestCandidate == null || entry.size > maxCandidateSize) {
                                    bestCandidate = entry
                                    maxCandidateSize = entry.size
                                }
                            }
                        }
                    }
                    if (bestCandidate != null) {
                        try {
                            zipFile.getInputStream(bestCandidate!!).use { input ->
                                val bytes = input.readBytes()
                                if (bytes.size > 1024) {
                                    coverBytes = bytes
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (ce: Exception) {
                ce.printStackTrace()
            }

            // 8. Split any oversized chapters and protect against duplicate title naming
            val manageableChapters = chapterManager.splitLargeChapters(bookTitle, rawChapters, targetWords = 2200, maxWords = 3200)
            val processedChapters = chapterManager.postProcessChapterTitles(bookTitle, manageableChapters)
            return ParsedBook(bookTitle, author, processedChapters, coverBytes)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                zipFile?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}