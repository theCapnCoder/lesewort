package com.example.data.parser

import android.content.Context
import android.net.Uri
import com.example.data.engine.LinguisticEngine
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

data class ParsedChapter(
    val title: String,
    val content: String
)

data class ParsedBook(
    val title: String,
    val author: String,
    val chapters: List<ParsedChapter>,
    val coverBytes: ByteArray? = null
)

data class ManifestItem(
    val id: String,
    val href: String,
    val properties: String,
    val mediaType: String
)

object BookParser {

    fun parseEpubFile(file: File): ParsedBook? {
        return parseEpub(file)
    }

    fun parseUri(context: Context, uri: Uri): ParsedBook? {
        val contentResolver = context.contentResolver
        val name = getFileName(context, uri) ?: "imported_book"
        val extension = name.substringAfterLast(".").lowercase()

        // Copy uri to temp file to handle with standard APIs
        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}_$name")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        val parsedBook = try {
            when (extension) {
                "epub" -> parseEpub(tempFile)
                "srt", "vtt" -> {
                    val text = readFileContent(tempFile)
                    parseSrt(name, text)
                }
                "txt" -> {
                    val text = readFileContent(tempFile)
                    parseTxt(name, text)
                }
                else -> {
                    val text = readFileContent(tempFile)
                    if (isSrtFormat(text)) {
                        parseSrt(name, text)
                    } else if (text.isNotEmpty()) {
                        parseTxt(name, text)
                    } else {
                        null
                    }
                }
            }
        } finally {
            tempFile.delete()
        }

        return parsedBook
    }

    private fun readFileContent(file: File): String {
        val bytes = try { file.readBytes() } catch (e: Exception) { return "" }
        if (bytes.isEmpty()) return ""

        // Check BOMs
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        // Try UTF-8 first
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            try {
                String(bytes, java.nio.charset.Charset.forName("windows-1251"))
            } catch (e2: Exception) {
                String(bytes, java.nio.charset.Charset.defaultCharset())
            }
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name
    }

    private fun parseEpub(file: File): ParsedBook? {
        var zipFile: ZipFile? = null
        try {
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
                            val resolvedHref = normalizePath(navParent + fileHref)
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
                            val resolvedSrc = normalizePath(ncxParent + fileSrc)
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
                val relativePathFromRoot = normalizePath(opfParent + manifestItem.href)
                var chapterTitle = navMap[relativePathFromRoot] ?: navMap[manifestItem.href]
                if (chapterTitle.isNullOrBlank()) {
                    chapterTitle = extractHeading(content)
                }

                val cleanText = cleanHtml(content)
                // Only include chapters that have readable text
                if (cleanText.isNotEmpty()) {
                    val lines = cleanText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    
                    // If currently chapterTitle is null/empty or generic, look for a better one inside the content!
                    if (chapterTitle.isNullOrBlank() || isGenericTitle(chapterTitle, bookTitle)) {
                        val extractedTitle = extractTitleFromLines(lines, bookTitle, chapterTitle ?: "")
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
                    val resolvedPath = normalizePath(opfParent + href)
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
                        val htmlPath = normalizePath(opfParent + coverHtmlItem.href)
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
                                val resolvedImageHref = normalizePath(htmlParent + cleanHref)
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
            val manageableChapters = splitLargeChapters(bookTitle, rawChapters, targetWords = 2200, maxWords = 3200)
            val processedChapters = postProcessChapterTitles(bookTitle, manageableChapters)
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

    private fun extractHeading(content: String): String? {
        val headingRegex = Regex("""<(h[1-6])[^>]*>(.*?)</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val match = headingRegex.find(content)
        return match?.groupValues?.get(2)?.replace(Regex("<[^>]*>"), "")?.trim()
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/")
        val resolvedList = mutableListOf<String>()
        for (part in parts) {
            if (part == "." || part.isEmpty()) continue
            if (part == "..") {
                if (resolvedList.isNotEmpty()) resolvedList.removeAt(resolvedList.size - 1)
            } else {
                resolvedList.add(part)
            }
        }
        return resolvedList.joinToString("/")
    }

    private fun cleanHtml(html: String): String {
        var text = html
        // Extract out unreadable head & script styles
        text = text.replace(Regex("""<head[^>]*>.*?</head>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        
        // Treat spacing elements
        text = text.replace(Regex("""<p\b[^>]*>""", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n\n")
        text = text.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("""<div\b[^>]*>""", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("""</div>""", RegexOption.IGNORE_CASE), "\n")
        
        // Strip other tags
        text = text.replace(Regex("""<[^>]*>""", RegexOption.DOT_MATCHES_ALL), "")
        
        // Standard conversions
        text = text.replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#160;", " ")
        
        text = text.replace(Regex("\n{3,}"), "\n\n")
        return text.trim()
    }

    fun parseTxt(fileName: String, text: String): ParsedBook {
        val bookTitle = fileName.substringBeforeLast(".")
        val author = "Текст"
        
        val trimmedText = text.removePrefix("\uFEFF").trim()
        if (trimmedText.isEmpty()) {
            return ParsedBook(bookTitle, author, listOf(ParsedChapter(bookTitle, "")))
        }

        // 1. Try to detect natural chapter headings in the text
        val rawChapters = splitTxtByDetectedChapters(bookTitle, trimmedText)

        // 2. Ensure that no individual chapter is huge (> 2800 words), splitting at paragraph boundaries
        val manageableChapters = splitLargeChapters(bookTitle, rawChapters, targetWords = 1800, maxWords = 2800)
        val finalChapters = postProcessChapterTitles(bookTitle, manageableChapters)

        return ParsedBook(bookTitle, author, finalChapters)
    }

    fun parseStory(title: String, author: String, text: String): ParsedBook {
        val trimmedText = text.removePrefix("\uFEFF").trim()
        val bookTitle = title.ifBlank { "Сгенерированный рассказ" }
        if (trimmedText.isEmpty()) {
            return ParsedBook(bookTitle, author, listOf(ParsedChapter(bookTitle, "")))
        }
        val rawChapters = splitTxtByDetectedChapters(bookTitle, trimmedText)
        val manageableChapters = splitLargeChapters(bookTitle, rawChapters, targetWords = 1800, maxWords = 2800)
        val finalChapters = postProcessChapterTitles(bookTitle, manageableChapters)
        return ParsedBook(bookTitle, author, finalChapters)
    }

    private fun fastCountWords(str: String): Int {
        var count = 0
        var inWord = false
        for (i in 0 until str.length) {
            val c = str[i]
            if (!c.isWhitespace()) {
                if (!inWord) {
                    count++
                    inWord = true
                }
            } else {
                inWord = false
            }
        }
        return count
    }

    /**
     * Splits plain text into chapters if heading markers (e.g. Chapter 1, Глава 1, Part I) are present,
     * or returns the entire text as a single chapter to be chunked by splitLargeChapters.
     */
    private fun splitTxtByDetectedChapters(bookTitle: String, text: String): List<ParsedChapter> {
        val lines = text.lines()
        val chapterRegex = Regex(
            """^\s*(?:глава|chapter|part|часть|раздел|section|episode|эпизод|книга|book)\s*[ivxldcm0-9_]*.*$""",
            RegexOption.IGNORE_CASE
        )
        val romanNumeralRegex = Regex("""^\s*[ivxldcm]+\.?\s*$""", RegexOption.IGNORE_CASE)
        val markdownHeaderRegex = Regex("""^#{1,3}\s+.*$""")
        val dividerRegex = Regex("""^\s*[*-_=~]{3,}\s*$""")

        data class HeadingMatch(val lineIndex: Int, val title: String)
        val matches = mutableListOf<HeadingMatch>()

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.length > 80) continue

            if (chapterRegex.matches(line) || romanNumeralRegex.matches(line) || markdownHeaderRegex.matches(line)) {
                val cleanTitle = line.removePrefix("#").removePrefix("#").removePrefix("#").trim()
                if (cleanTitle.isNotEmpty() && !isSentencesProse(line)) {
                    matches.add(HeadingMatch(i, cleanTitle))
                }
            } else if (dividerRegex.matches(line) && i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                if (nextLine.isNotEmpty() && nextLine.length <= 80 && !isSentencesProse(nextLine)) {
                    matches.add(HeadingMatch(i, nextLine))
                }
            }
        }

        // If we found at least 2 distinct chapter headings separated across the text, split by them
        if (matches.size >= 2) {
            val result = mutableListOf<ParsedChapter>()
            
            // Prologue / preface before the first chapter heading
            if (matches[0].lineIndex > 0) {
                val prologueContent = lines.subList(0, matches[0].lineIndex).joinToString("\n").trim()
                if (prologueContent.isNotEmpty()) {
                    result.add(ParsedChapter("Введение", prologueContent))
                }
            }

            for (mIndex in matches.indices) {
                val currentMatch = matches[mIndex]
                val startLine = currentMatch.lineIndex + 1
                val endLine = if (mIndex + 1 < matches.size) matches[mIndex + 1].lineIndex else lines.size
                
                val chapterContent = lines.subList(startLine, endLine).joinToString("\n").trim()
                if (chapterContent.isNotEmpty()) {
                    result.add(ParsedChapter(currentMatch.title, chapterContent))
                }
            }

            if (result.isNotEmpty()) {
                return result
            }
        }

        // Fallback: entire text in one chapter
        return listOf(ParsedChapter(bookTitle, text))
    }

    /**
     * Splits any chapter exceeding maxWords into comfortable, highly readable sub-parts
     * (~7000-9000 words per part), splitting at natural paragraph (\n\n) or sentence boundaries.
     */
    fun splitLargeChapters(
        bookTitle: String,
        chapters: List<ParsedChapter>,
        targetWords: Int = 8000,
        maxWords: Int = 11000
    ): List<ParsedChapter> {
        val result = mutableListOf<ParsedChapter>()

        for ((chIndex, chapter) in chapters.withIndex()) {
            val wordCount = fastCountWords(chapter.content)
            if (wordCount <= maxWords) {
                result.add(chapter)
                continue
            }

            val baseTitle = chapter.title.ifBlank { "Глава ${chIndex + 1}" }
            val parts = splitContentIntoParts(chapter.content, baseTitle, targetWords)
            result.addAll(parts)
        }

        return result
    }

    /**
     * Splits large text into smaller parts by double newlines (\n\n), respecting sentence boundaries.
     */
    fun splitContentIntoParts(
        content: String,
        baseTitle: String,
        targetWords: Int = 8000
    ): List<ParsedChapter> {
        val rawParagraphs = content.split(Regex("\\n\\s*\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (rawParagraphs.isEmpty()) {
            return listOf(ParsedChapter(baseTitle, content))
        }

        val parts = mutableListOf<ParsedChapter>()
        var currentPartParas = mutableListOf<String>()
        var currentWordCount = 0
        var partIndex = 1

        fun flushPart() {
            if (currentPartParas.isNotEmpty()) {
                val partContent = currentPartParas.joinToString("\n\n")
                val isGeneric = baseTitle.startsWith("Часть", ignoreCase = true) || baseTitle.startsWith("Глава", ignoreCase = true)
                val partTitle = if (isGeneric) {
                    "$baseTitle (Часть $partIndex)"
                } else {
                    "Часть $partIndex"
                }
                parts.add(ParsedChapter(partTitle, partContent))
                currentPartParas = mutableListOf()
                currentWordCount = 0
                partIndex++
            }
        }

        for (para in rawParagraphs) {
            val paraWords = fastCountWords(para)
            
            // If a single paragraph is colossal (> 400 words), split it at sentence boundaries
            if (paraWords > 400) {
                val sentences = splitGiantParagraph(para)
                for (sentence in sentences) {
                    val sWords = fastCountWords(sentence)
                    if (currentWordCount + sWords > targetWords && currentPartParas.isNotEmpty()) {
                        flushPart()
                    }
                    currentPartParas.add(sentence)
                    currentWordCount += sWords
                }
            } else {
                if (currentWordCount + paraWords > targetWords && currentPartParas.isNotEmpty()) {
                    flushPart()
                }
                currentPartParas.add(para)
                currentWordCount += paraWords
            }
        }
        flushPart()

        return if (parts.isNotEmpty()) parts else listOf(ParsedChapter(baseTitle, content))
    }

    private fun splitGiantParagraph(para: String): List<String> {
        val sentenceRegex = Regex("""(?<=[.!?]["'»”]?)\s+""")
        val rawSentences = para.split(sentenceRegex).map { it.trim() }.filter { it.isNotEmpty() }
        if (rawSentences.size <= 1) return listOf(para)

        val groups = mutableListOf<String>()
        var currentGroup = StringBuilder()
        var sentenceCount = 0

        for (s in rawSentences) {
            if (currentGroup.isNotEmpty()) {
                currentGroup.append(" ")
            }
            currentGroup.append(s)
            sentenceCount++

            if (sentenceCount >= 4 || currentGroup.length >= 350) {
                groups.add(currentGroup.toString().trim())
                currentGroup = StringBuilder()
                sentenceCount = 0
            }
        }

        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup.toString().trim())
        }

        return if (groups.isNotEmpty()) groups else listOf(para)
    }

    private fun postProcessChapterTitles(bookTitle: String, chapters: List<ParsedChapter>): List<ParsedChapter> {
        val bookTitleClean = bookTitle.trim().lowercase()
        val allSameTitle = chapters.distinctBy { it.title.trim().lowercase() }.size <= 1 && chapters.size > 1
        
        return chapters.mapIndexed { index, chapter ->
            val originalTitle = chapter.title.trim()
            val cleanTitle = originalTitle.lowercase()
            
            val finalTitle = when {
                originalTitle.isEmpty() -> {
                    "Глава ${index + 1}"
                }
                allSameTitle -> {
                    "Глава ${index + 1}"
                }
                cleanTitle == bookTitleClean && chapters.size > 1 -> {
                    "Часть ${index + 1}"
                }
                else -> originalTitle
            }
            chapter.copy(title = finalTitle)
        }
    }

    private fun isGenericTitle(title: String, bookTitle: String): Boolean {
        val clean = title.trim().lowercase()
        if (clean.isEmpty()) return true
        
        val bookTitleClean = bookTitle.trim().lowercase()
        if (clean == bookTitleClean) return true
        
        // Pattern for generic titles: e.g. "Глава 1", "Chapter 1", "Part IV", etc.
        val genericRegex = Regex(
            """^(?:глава|chapter|part|часть|раздел|section|episode|эпизод|книга|book)\s*[ivxldcm0-9_]*\.?\s*$""", 
            RegexOption.IGNORE_CASE
        )
        if (genericRegex.matches(clean)) return true
        
        // Just numbers: "1", "1.", "I", "II"
        val numbersRegex = Regex("""^[ivxldcm0-9]+\.?\s*$""", RegexOption.IGNORE_CASE)
        if (numbersRegex.matches(clean)) return true
        
        return false
    }

    private fun isSentencesProse(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        
        // If it has terminal punctuation and is relatively long, it's prose
        if ((trimmed.endsWith(".") || trimmed.endsWith("?") || trimmed.endsWith("!")) && trimmed.length > 40) {
            return true
        }
        
        // Multiple sentences check: contains ". " or "? " or "! " followed by an uppercase letter
        val sentenceBoundaries = Regex("""[.!?]\s+[A-ZА-ЯЁ]""")
        if (sentenceBoundaries.containsMatchIn(trimmed)) {
            return true
        }
        
        // Too many words: say, more than 10 words is unlikely to be a chapter title
        val words = trimmed.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.size > 10) {
            return true
        }
        
        // Number only checking: if it is just a pure number/year/etc
        if (trimmed.all { it.isDigit() }) {
            return true
        }
        
        return false
    }

    private fun extractTitleFromLines(lines: List<String>, bookTitle: String, fallback: String): String? {
        if (lines.isEmpty()) return null
        
        for (i in 0 until minOf(lines.size, 4)) {
            val line = lines[i].trim()
            
            if (line.lowercase() == bookTitle.trim().lowercase()) {
                continue
            }
            
            if (line.length > 100) {
                continue
            }
            
            if (isGenericTitle(line, bookTitle)) {
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (nextLine.length in 2..100 && !isGenericTitle(nextLine, bookTitle) && !isSentencesProse(nextLine)) {
                        val cleanGeneric = line.trimEnd('.', ':', ' ', ',')
                        return "$cleanGeneric. $nextLine"
                    }
                }
                return line
            }
            
            if (line.length in 2..100 && !isSentencesProse(line)) {
                return line
            }
        }
        
        return if (fallback.isNotEmpty()) fallback else null
    }

    fun isSrtFormat(text: String): Boolean {
        val srtTimestampRegex = Regex("""\d{1,2}:\d{2}:\d{2}[.,]\d{3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[.,]\d{3}""")
        val vttTimestampRegex = Regex("""\d{1,2}:\d{2}[.,]\d{3}\s*-->\s*\d{1,2}:\d{2}[.,]\d{3}""")
        return srtTimestampRegex.containsMatchIn(text) || vttTimestampRegex.containsMatchIn(text) || text.contains("-->")
    }

    data class SubtitleCue(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    fun parseTimestampToMs(timeStr: String): Long? {
        val clean = timeStr.trim().replace(',', '.')
        val parts = clean.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val secParts = parts[2].split(".")
                    val seconds = secParts[0].toLong()
                    val millis = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                    (hours * 3600 + minutes * 60 + seconds) * 1000L + millis
                }
                2 -> {
                    val minutes = parts[0].toLong()
                    val secParts = parts[1].split(".")
                    val seconds = secParts[0].toLong()
                    val millis = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                    (minutes * 60 + seconds) * 1000L + millis
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun formatTimestamp(ms: Long, showHours: Boolean): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (showHours || hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    /**
     * Parses SRT or VTT subtitles into structured, comfortable chapters with 1-hour timestamp intervals.
     * e.g., "Часть 1 (0:00:00 - 1:00:15)", "Часть 2 (1:00:15 - 2:01:00)"
     * Each chapter contains ~1 hour of content (~7000-8000 words) formatted into natural reading paragraphs.
     */
    fun parseSrt(fileName: String, text: String): ParsedBook {
        val bookTitle = fileName.substringBeforeLast(".")
        val cues = extractSubtitleCues(text)

        if (cues.isEmpty()) {
            val cleanText = extractCleanTextFromSrt(text)
            return parseTxt(fileName, cleanText).copy(author = "Субтитры")
        }

        val showHours = cues.last().endMs >= 3600_000L || true
        val chapters = mutableListOf<ParsedChapter>()
        val currentChapterCues = mutableListOf<SubtitleCue>()
        var currentChapterWordCount = 0
        val targetChapterWords = 8000
        val hardMaxChapterWords = 10000
        val targetChapterDurationMs = 60 * 60 * 1000L // 60 minutes (1 hour)
        val sentenceEndRegex = Regex("""[.!?]["'»”]?$""")
        val clauseEndRegex = Regex("""[,;:]["'»”]?$""")

        fun flushChapter(partNumber: Int) {
            if (currentChapterCues.isEmpty()) return

            val startMs = currentChapterCues.first().startMs
            val endMs = currentChapterCues.last().endMs
            val startStr = formatTimestamp(startMs, showHours)
            val endStr = formatTimestamp(endMs, showHours)
            val chapterTitle = "Часть $partNumber ($startStr - $endStr)"

            // Group subtitle cues into paragraphs of 3-4 sentences
            val paragraphList = mutableListOf<String>()
            var currentPara = StringBuilder()
            var sentenceCountInPara = 0

            for (cue in currentChapterCues) {
                if (currentPara.isNotEmpty()) {
                    currentPara.append(" ")
                }
                currentPara.append(cue.text)

                val endsSentence = sentenceEndRegex.containsMatchIn(cue.text)
                if (endsSentence) {
                    sentenceCountInPara++
                    if (sentenceCountInPara >= 3 || currentPara.length >= 250) {
                        paragraphList.add(currentPara.toString().trim())
                        currentPara = StringBuilder()
                        sentenceCountInPara = 0
                    }
                } else if (currentPara.length >= 350) {
                    paragraphList.add(currentPara.toString().trim())
                    currentPara = StringBuilder()
                    sentenceCountInPara = 0
                }
            }

            if (currentPara.isNotEmpty()) {
                paragraphList.add(currentPara.toString().trim())
            }

            val chapterContent = paragraphList.joinToString("\n\n")
            if (chapterContent.isNotEmpty()) {
                chapters.add(ParsedChapter(chapterTitle, chapterContent))
            }

            currentChapterCues.clear()
            currentChapterWordCount = 0
        }

        var partNumber = 1
        var chapterStartMs = cues.first().startMs

        for (cue in cues) {
            val cueWords = fastCountWords(cue.text)
            currentChapterCues.add(cue)
            currentChapterWordCount += cueWords

            val currentChapterDuration = cue.endMs - chapterStartMs
            val isSentenceBoundary = sentenceEndRegex.containsMatchIn(cue.text)
            val isClauseBoundary = isSentenceBoundary || clauseEndRegex.containsMatchIn(cue.text)

            val shouldFlush = when {
                currentChapterDuration >= 55 * 60 * 1000L && isSentenceBoundary -> true
                currentChapterDuration >= 60 * 60 * 1000L -> true
                currentChapterWordCount >= hardMaxChapterWords -> true
                currentChapterWordCount >= targetChapterWords && isSentenceBoundary -> true
                else -> false
            }

            if (shouldFlush) {
                flushChapter(partNumber)
                partNumber++
                chapterStartMs = cue.endMs
            }
        }
        flushChapter(partNumber)

        val manageable = if (chapters.isNotEmpty()) {
            splitLargeChapters(bookTitle, chapters, targetWords = 8000, maxWords = 10000)
        } else {
            val cleanText = extractCleanTextFromSrt(text)
            val parts = splitContentIntoParts(cleanText, "Часть 1", targetWords = 8000)
            parts
        }

        return ParsedBook(bookTitle, "Субтитры", manageable)
    }

    private fun extractSubtitleCues(rawText: String): List<SubtitleCue> {
        val timestampRegex = Regex("""(\d{1,2}:\d{2}(?::\d{2})?[.,]\d{2,3})\s*-->\s*(\d{1,2}:\d{2}(?::\d{2})?[.,]\d{2,3})""", RegexOption.IGNORE_CASE)
        val numberOnlyRegex = Regex("""^\d+$""")
        val tagRegex = Regex("""<[^>]*>""")
        val srtCodeRegex = Regex("""\{[^}]*\}""")

        val lines = rawText.lines()
        val cues = mutableListOf<SubtitleCue>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || line.startsWith("WEBVTT", ignoreCase = true) || line.startsWith("NOTE", ignoreCase = true)) {
                i++
                continue
            }

            val tsMatch = timestampRegex.find(line)
            if (tsMatch != null) {
                val startMs = parseTimestampToMs(tsMatch.groupValues[1]) ?: 0L
                val endMs = parseTimestampToMs(tsMatch.groupValues[2]) ?: startMs

                i++
                val textBuf = mutableListOf<String>()
                while (i < lines.size) {
                    val subLine = lines[i].trim()
                    if (subLine.isEmpty()) break
                    if (timestampRegex.containsMatchIn(subLine)) break
                    if (numberOnlyRegex.matches(subLine) && i + 1 < lines.size && timestampRegex.containsMatchIn(lines[i + 1])) {
                        break
                    }

                    val cleaned = subLine
                        .replace(tagRegex, "")
                        .replace(srtCodeRegex, "")
                        .replace("&nbsp;", " ")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&#160;", " ")
                        .trim()

                    if (cleaned.isNotEmpty()) {
                        textBuf.add(cleaned)
                    }
                    i++
                }

                if (textBuf.isNotEmpty()) {
                    cues.add(SubtitleCue(startMs, endMs, textBuf.joinToString(" ")))
                }
            } else {
                i++
            }
        }

        return cues
    }

    fun extractCleanTextFromSrt(rawText: String): String {
        val timestampRegex = Regex("""\d{1,2}:\d{2}:\d{2}[.,]\d{3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[.,]\d{3}.*""", RegexOption.IGNORE_CASE)
        val numberOnlyRegex = Regex("""^\d+$""")
        val tagRegex = Regex("""<[^>]*>""")
        val srtCodeRegex = Regex("""\{[^}]*\}""")

        val lines = rawText.lines()
        val cleanSubtitleLines = mutableListOf<String>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || line.startsWith("WEBVTT", ignoreCase = true) || line.startsWith("NOTE", ignoreCase = true)) {
                i++
                continue
            }

            if (timestampRegex.matches(line) || line.contains("-->")) {
                i++
                val textBuf = mutableListOf<String>()
                while (i < lines.size) {
                    val subLine = lines[i].trim()
                    if (subLine.isEmpty()) {
                        break
                    }
                    if (timestampRegex.matches(subLine) || subLine.contains("-->")) {
                        break
                    }
                    if (numberOnlyRegex.matches(subLine) && i + 1 < lines.size && (lines[i + 1].contains("-->") || timestampRegex.matches(lines[i + 1].trim()))) {
                        break
                    }

                    val cleaned = subLine
                        .replace(tagRegex, "")
                        .replace(srtCodeRegex, "")
                        .replace("&nbsp;", " ")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&#160;", " ")
                        .trim()

                    if (cleaned.isNotEmpty()) {
                        textBuf.add(cleaned)
                    }
                    i++
                }
                if (textBuf.isNotEmpty()) {
                    cleanSubtitleLines.add(textBuf.joinToString(" "))
                }
            } else if (numberOnlyRegex.matches(line) && i + 1 < lines.size && (lines[i + 1].contains("-->") || timestampRegex.matches(lines[i + 1].trim()))) {
                i++
            } else {
                val cleaned = line
                    .replace(tagRegex, "")
                    .replace(srtCodeRegex, "")
                    .replace("&nbsp;", " ")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .trim()
                if (cleaned.isNotEmpty() && !numberOnlyRegex.matches(cleaned)) {
                    cleanSubtitleLines.add(cleaned)
                }
                i++
            }
        }

        val paragraphList = mutableListOf<String>()
        var currentPara = StringBuilder()
        var sentenceCountInPara = 0

        val sentenceEndRegex = Regex("""[.!?]["'»”]?$""")

        for (sub in cleanSubtitleLines) {
            if (currentPara.isNotEmpty()) {
                currentPara.append(" ")
            }
            currentPara.append(sub)

            val endsWithSentencePunctuation = sentenceEndRegex.containsMatchIn(sub)
            if (endsWithSentencePunctuation) {
                sentenceCountInPara++
                if (sentenceCountInPara >= 3 || currentPara.length >= 200) {
                    paragraphList.add(currentPara.toString().trim())
                    currentPara = StringBuilder()
                    sentenceCountInPara = 0
                }
            }
        }

        if (currentPara.isNotEmpty()) {
            paragraphList.add(currentPara.toString().trim())
        }

        return paragraphList.joinToString("\n\n")
    }
}

