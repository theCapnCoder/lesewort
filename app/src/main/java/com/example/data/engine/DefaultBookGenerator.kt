package com.example.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.data.parser.BookParser
import com.example.data.repository.BookRepository
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DefaultBookGenerator {

    private fun generateCoverJpg(): ByteArray {
        val width = 600
        val height = 900
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw forest deep-green background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#152C16")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw elegant ornamental border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E4D29B") // Cream gold
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawRect(25f, 25f, (width - 25).toFloat(), (height - 25).toFloat(), borderPaint)

        val thinBorderPaint = Paint().apply {
            color = Color.parseColor("#E4D29B")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(40f, 40f, (width - 40).toFloat(), (height - 40).toFloat(), thinBorderPaint)

        // Draw Title text
        val titlePaint = Paint().apply {
            color = Color.parseColor("#E4D29B")
            textSize = 64f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
        canvas.drawText("Grimms", (width / 2).toFloat(), 260f, titlePaint)
        canvas.drawText("Märchen", (width / 2).toFloat(), 340f, titlePaint)

        // Subtitle
        val subPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.NORMAL)
        }
        canvas.drawText("Zweisprachiges Lesebuch", (width / 2).toFloat(), 440f, subPaint)
        canvas.drawText("Rotkäppchen", (width / 2).toFloat(), 530f, subPaint)
        canvas.drawText("Hänsel und Gretel", (width / 2).toFloat(), 580f, subPaint)
        canvas.drawText("Der sieben Geißlein", (width / 2).toFloat(), 630f, subPaint)

        // Author
        val authorPaint = Paint().apply {
            color = Color.parseColor("#A8BBA2") // Muted dusty green
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.ITALIC)
        }
        canvas.drawText("Brüder Grimm", (width / 2).toFloat(), 760f, authorPaint)

        // Compress
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val bytes = stream.toByteArray()
        bitmap.recycle()
        return bytes
    }

    private fun createDefaultGermanEpubBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)

        // 1. mimetype (MUST be stored uncompressed as the first entry)
        val mimeEntry = ZipEntry("mimetype").apply {
            method = ZipOutputStream.STORED
            size = 20
            compressedSize = 20
            crc = 0x2cab616fL // CRC-32 of "application/epub+zip"
        }
        zos.putNextEntry(mimeEntry)
        zos.write("application/epub+zip".toByteArray(Charsets.US_ASCII))
        zos.closeEntry()

        // Helper to put entry
        fun putTextEntry(name: String, content: String) {
            zos.putNextEntry(ZipEntry(name))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        // 2. META-INF/container.xml
        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()
        putTextEntry("META-INF/container.xml", containerXml)

        // 3. OEBPS/content.opf
        val opfXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Grimms Märchen</dc:title>
                <dc:creator>Brüder Grimm</dc:creator>
                <dc:language>de</dc:language>
                <meta name="cover" content="cover-image"/>
              </metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="cover-image" href="cover.jpg" media-type="image/jpeg"/>
                <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                <item id="chapter2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                <item id="chapter3" href="chapter3.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx">
                <itemref idref="chapter1"/>
                <itemref idref="chapter2"/>
                <itemref idref="chapter3"/>
              </spine>
            </package>
        """.trimIndent()
        putTextEntry("OEBPS/content.opf", opfXml)

        // 4. OEBPS/toc.ncx
        val ncxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint id="navPoint-1" playOrder="1">
                  <navLabel><text>Rotkäppchen</text></navLabel>
                  <content src="chapter1.xhtml"/>
                </navPoint>
                <navPoint id="navPoint-2" playOrder="2">
                  <navLabel><text>Hänsel und Gretel</text></navLabel>
                  <content src="chapter2.xhtml"/>
                </navPoint>
                <navPoint id="navPoint-3" playOrder="3">
                  <navLabel><text>Der Wolf und die sieben jungen Geißlein</text></navLabel>
                  <content src="chapter3.xhtml"/>
                </navPoint>
              </navMap>
            </ncx>
        """.trimIndent()
        putTextEntry("OEBPS/toc.ncx", ncxXml)

        // 5. OEBPS/chapter1.xhtml
        val ch1 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Rotkäppchen</title></head>
            <body>
              <h1>Rotkäppchen</h1>
              <p>Es war einmal eine kleine süße Dirne, die hatte jedermann lieb, der sie nur ansah, am allerliebsten aber ihre Großmutter, die wusste gar nicht, was sie alles dem Kinde geben sollte. Einmal schenkte sie ihm ein Käppchen von rotem Samt, und weil ihm das so wohl stand und es nichts anderes mehr tragen wollte, hieß es nur das Rotkäppchen.</p>
              <p>Eines Tages sprach seine Mutter zu ihm: „Komm, Rotkäppchen, da hast du ein Stück Kuchen und eine Flasche Wein, bring das der Großmutter hinaus; sie ist krank und schwach und wird sich daran laben. Mach dich auf, bevor es heiß wird, und wenn du hinauskommst, so geh hübsch sittsam und lauf nicht vom Weg ab, sonst fällst du und zerbrichst das Glas, und die Großmutter hat nichts.“</p>
              <p>Rotkäppchen versprach der Mutter, alles gut auszurichten, und gab ihr die Hand darauf. Die Großmutter aber wohnte draußen im Wald, eine halbe Stunde vom Dorf. Wie nun Rotkäppchen in den Wald kam, begegnete ihm der Wolf. Rotkäppchen aber wusste nicht, was das für ein böses Tier war, und fürchtete sich nicht vor ihm.</p>
              <p>„Guten Tag, Rotkäppchen“, sprach er. — „Schönen Dank, Wolf.“ — „Wo hinaus so früh, Rotkäppchen?“ — „Zur Großmutter.“ — „Was trägst du unter der Schürze?“ — „Kuchen und Wein: gestern haben wir gebacken, da soll sich die kranke und schwache Großmutter etwas Gutes tun und sich stärken.“</p>
              <p>„Wo wohnt deine Großmutter, Rotkäppchen?“ — „Noch eine gute Viertelstunde weiter im Wald, unter den drei großen Eichbäumen, da steht ihr Haus, unten sind die Nusshecken, das wirst du ja wissen“, sagte Rotkäppchen. Der Wolf dachte bei sich: „Das zarten junge Ding, das ist ein fetter Bissen, der wird noch besser schmecken als die Alte: du musst es listig anfangen, damit du beide schnappst.“</p>
            </body>
            </html>
        """.trimIndent()
        putTextEntry("OEBPS/chapter1.xhtml", ch1)

        // 6. OEBPS/chapter2.xhtml
        val ch2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Hänsel und Gretel</title></head>
            <body>
              <h1>Hänsel und Gretel</h1>
              <p>Vor einem großen Walde wohnte ein armer Holzhacker mit seiner Frau und seinen zwei Kindern; das Bübchen hieß Hänsel und das Mädchen Gretel. Er hatte wenig zu beißen und zu brechen, und einmal, als eine große Teuerung ins Land kam, konnte er auch das täglich Brot nicht mehr schaffen.</p>
              <p>Wie er sich nun abends im Bett Gedanken machte und sich vor Sorgen herumwälzte, seufzte er und sprach zu seiner Frau: „Was soll aus uns werden? Wie können wir unsere armen Kinder ernähren, da wir für uns selbst nichts mehr haben?“</p>
              <p>„Weißt du was, Mann“, antwortete die Frau, „wir wollen morgen früh am Tage die Kinder hinaus in den Wald führen, wo er am dicksten ist: da machen wir ihnen ein Feuer an und geben jedem noch ein Stückchen Brot, dann gehen wir an unsere Arbeit und lassen sie allein. Sie finden den Weg nicht wieder heim, und wir sind sie los.“ — „Nein, Frau“, sagte der Mann, „das tue ich nicht; wie sollt ich's übers Herz bringen, meine Kinder im Walde allein zu lassen! Die wilden Tiere würden bald kommen und sie zerreißen.“</p>
              <p>„O du Narr“, sagte sie, „dann müssen wir alle viere Hungers sterben, du musst nur die Bretter für die Särge hobeln“, und ließ ihm keine Ruhe, bis er einwilligte. „Aber die armen Kinder dauern mich doch“, sagte der Mann.</p>
              <p>Die zwei Kinder hatten vor Hunger auch nicht einschlafen können und hatten gehört, was die Stiefmutter zum Vater gesagt hatte. Gretel weinte bittere Tränen und sprach zu Hänsel: „Nun ist's um uns geschehen.“ — „Still, Gretel“, sprach Hänsel, „gräme dich nicht, ich will uns schon helfen.“ Und als die Alten eingeschlafen waren, stand er auf, zog sein Röcklein an, machte die Untentür auf und schlich sich hinaus.</p>
            </body>
            </html>
        """.trimIndent()
        putTextEntry("OEBPS/chapter2.xhtml", ch2)

        // 7. OEBPS/chapter3.xhtml
        val ch3 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Der Wolf und die sieben jungen Geißlein</title></head>
            <body>
              <h1>Der Wolf und die sieben jungen Geißlein</h1>
              <p>Es war einmal eine alte Geiß, die hatte sieben junge Geißlein und hatte sie lieb, wie eine Mutter ihre Kinder lieb hat. Eines Tages wollte sie in den Wald gehen und Futter holen; da rief sie alle sieben herbei und sprach: „Liebe Kinder, ich will hinaus in den Wald, seid auf eurer Hut vor dem Wolf; wenn er hereinkommt, so frisst er euch alle mit Haut und Haar. Der Bösewicht verstellt sich oft, aber an seiner rauhen Stimme und an seinen schwarzen Füßen werdet ihr ihn gleich erkennen.“</p>
              <p>Die Geißlein sprachen: „Liebe Mutter, wir wollen uns schon in acht nehmen, ihr könnt ohne Sorge fortgehen.“ Da meckerte die Alte und machte sich getrost auf den Weg.</p>
              <p>Es dauerte nicht lange, so klopfte jemand an die Haustür und rief: „Macht auf, ihr lieben Kinder, eure Mutter ist da und hat jedem von euch etwas mitgebracht.“ Aber die Geißlein hörten an der rauhen Stimme, dass es der Wolf war. „Wir machen nicht auf“, riefen sie, „du bist unsere Mutter nicht; die hat eine feine und liebliche Stimme, deine Stimme aber ist rauh: du bist der Wolf.“</p>
            </body>
            </html>
        """.trimIndent()
        putTextEntry("OEBPS/chapter3.xhtml", ch3)

        // 8. OEBPS/cover.jpg
        val coverBytes = generateCoverJpg()
        zos.putNextEntry(ZipEntry("OEBPS/cover.jpg"))
        zos.write(coverBytes)
        zos.closeEntry()

        zos.close()
        return baos.toByteArray()
    }

    suspend fun installDefaultBookIfNeeded(
        context: Context,
        viewModel: MainViewModel,
        repository: BookRepository
    ) = withContext(Dispatchers.IO) {
        try {
            val existingBooks = repository.allBooks.first()
            if (existingBooks.isNotEmpty()) {
                return@withContext
            }

            // Create default German EPUB in cache
            val epubBytes = createDefaultGermanEpubBytes()
            val tempFile = File(context.cacheDir, "default_german_book.epub")
            FileOutputStream(tempFile).use { fos ->
                fos.write(epubBytes)
            }

            // Parse and import
            val parsedBook = BookParser.parseEpubFile(tempFile)
            if (parsedBook != null && parsedBook.chapters.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    viewModel.importBook(context, parsedBook)
                }
            }

            tempFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateJapaneseCoverJpg(): ByteArray {
        val width = 600
        val height = 900
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Warm dark crimson / red Japanese background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#4A0E17")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw elegant ornamental border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#FDE68A")
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawRect(25f, 25f, (width - 25).toFloat(), (height - 25).toFloat(), borderPaint)

        // Draw Japanese sun circle
        val sunPaint = Paint().apply {
            color = Color.parseColor("#BC002D")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle((width / 2).toFloat(), 380f, 130f, sunPaint)

        // Japanese Kanji Title
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 72f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        canvas.drawText("桃太郎", (width / 2).toFloat(), 405f, titlePaint)

        // Subtitle Romaji
        val subPaint = Paint().apply {
            color = Color.parseColor("#FDE68A")
            textSize = 30f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
        canvas.drawText("MOMOTARO", (width / 2).toFloat(), 560f, subPaint)

        // Description
        val descPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.NORMAL)
        }
        canvas.drawText("The Peach Boy — Graded Reader", (width / 2).toFloat(), 610f, descPaint)

        // Author
        val authorPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 26f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.ITALIC)
        }
        canvas.drawText("Japanese Folk Tale", (width / 2).toFloat(), 760f, authorPaint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val bytes = stream.toByteArray()
        bitmap.recycle()
        return bytes
    }

    fun createJapaneseMomotaroBook(): com.example.data.parser.ParsedBook {
        val ch1 = com.example.data.parser.ParsedChapter(
            title = "第1章 桃太郎の誕生",
            content = """昔々、ある所に、おじいさんとおばあさんが住んでいました。
おじいさんは山へ柴刈りに、おばあさんは川へ洗濯に行きました。

おばあさんが川で洗濯をしていると、川上から大きな桃が、どんぶらこ、どんぶらこと流れてきました。
「おや、これはずいぶん大きな桃だね。家に持って帰って、おじいさんと一緒に食べよう」。
おばあさんは桃を拾い上げて、家に持ち帰りました。

夕方、おじいさんが山から帰ってきました。
「おばあさん、何か食べるものはあるかい？」
「おじいさん、川でこんなに大きな桃を拾ったんですよ」。

おじいさんとおばあさんが桃を切ろうとすると、桃がぱかっと割れて、中から元気な男の赤ちゃんが生まれました。
「これは神様からの授かりものだ」。
二人は大喜びして、桃から生まれた男の子に「桃太郎」という名前をつけました。

桃太郎は毎日ご飯をもりもり食べて、すくすくと大きく、力持ちに育ちました。"""
        )

        val ch2 = com.example.data.parser.ParsedChapter(
            title = "第2章 鬼退治への旅立ち",
            content = """その頃、都や村では、鬼ヶ島に住む悪い鬼たちがやってきては、人々を苦しめたり、大切な宝物を奪ったりしていました。
村人たちはいつも怯えて暮らしていました。

それを聞いた桃太郎は、おじいさんとおばあさんの前で言いました。
「おじいさん、おばあさん。私は鬼ヶ島へ行って、悪い鬼たちを退治してきます」。

おじいさんとおばあさんは驚きましたが、桃太郎の決意が固いことを知り、応援することにしました。
おばあさんは、日本一おいしいきびだんごを作ってくれました。
おじいさんは、立派な刀と「日本一の桃太郎」と書かれた旗を用意してくれました。

桃太郎はきびだんごを腰の袋に入れ、刀を差して、元気に旅立ちました。"""
        )

        val ch3 = com.example.data.parser.ParsedChapter(
            title = "第3章 頼もしい仲間たち",
            content = """桃太郎が山道を歩いていると、一匹の犬が駆けてきました。
「桃太郎さん、桃太郎さん、腰につけたきびだんごを一つくださいな。お供します」。
桃太郎は「いいよ、日本一のきびだんごをあげよう」と言って、きびだんごを一つ渡しました。犬はきびだんごを食べると、桃太郎の後ろについて歩き始めました。

しばらく行くと、山の中から猿がやってきました。
「桃太郎さん、きびだんごを一つください。私もお供します」。
桃太郎は猿にもきびだんごをあげました。

さらに歩くと、今度はキジが飛んできました。
「桃太郎さん、きびだんごをくださいな。鬼退治のお手伝いをします」。
桃太郎はキジにもきびだんごを渡しました。

こうして、犬、猿、キジという頼もしい仲間ができました。"""
        )

        val ch4 = com.example.data.parser.ParsedChapter(
            title = "第4章 鬼ヶ島の戦いと凱旋",
            content = """一行は海に出て、船に乗って鬼ヶ島へと向かいました。
しばらく舟を漕ぐと、黒い岩に囲まれた恐ろしい鬼ヶ島が見えてきました。

城の門は固く閉ざされていましたが、キジが空高く飛び上がり、門の内側から鍵を開けました。
犬は門番の鬼たちに吠えかかり、猿は素早く壁をよじ登って鬼たちを混乱させました。

桃太郎は城の奥へと進み、鬼の親分と対決しました。
「村人たちを苦しめる悪い鬼め、成敗してくれる！」。
桃太郎の強い力に鬼たちは敵わず、親分は涙を流して降参しました。
「まいりました、もう二度と悪さはしません。宝物もお返しします」。

桃太郎と仲間たちは、奪われた金銀財宝を車に積み、意気揚々と村へ帰りました。
おじいさんとおばあさんは無事な桃太郎の姿を見て涙を流して喜びました。
村人たちも大喜びし、桃太郎たちは末永く幸せに暮らしました。"""
        )

        val coverBytes = generateJapaneseCoverJpg()
        return com.example.data.parser.ParsedBook(
            title = "桃太郎 (Momotaro)",
            author = "日本の昔話 (Japanese Folklore)",
            chapters = listOf(ch1, ch2, ch3, ch4),
            coverBytes = coverBytes
        )
    }
}
