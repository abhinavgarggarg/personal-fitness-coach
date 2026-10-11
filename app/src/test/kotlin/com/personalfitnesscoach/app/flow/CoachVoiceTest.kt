package com.personalfitnesscoach.app.flow

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * COACH-001, the coaching voice: calm, competent, honest, brief, non-judgemental — no body-shaming, guilt, punishment exercise, extreme
 * motivation or medical claims. Every word the app shows lives in the string resources (D-088), so they are checked here as a whole.
 */
class CoachVoiceTest {
    private fun valuesDir(): File = listOf("src/main/res/values", "app/src/main/res/values").map { File(it) }.first { it.isDirectory }

    /** Every app string, name → text (the escape for apostrophes undone). */
    private val strings: Map<String, String> by lazy {
        val out = LinkedHashMap<String, String>()
        valuesDir().listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!.sorted().forEach { f ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val list = doc.getElementsByTagName("string")
            for (i in 0 until list.length) {
                val e = list.item(i)
                out[e.attributes.getNamedItem("name").nodeValue] = e.textContent.replace("\\'", "'")
            }
        }
        out
    }

    private val prohibited = listOf(
        // body-shaming
        Regex("\\b(flabby|ugly|disgusting|gross|bikini body|beach body|fatty|chubby|out of shape)\\b"),
        // guilt and blame
        Regex("\\b(lazy|guilt|guilty|shame|ashamed|no excuses|excuses|you failed|you let yourself|disappoint\\w*|you missed again|broke your streak|streak lost|lost your streak)\\b"),
        // punishment exercise
        Regex("\\b(punish\\w*|burn it off|burn off|earn your|earn it|make up for)\\b"),
        // extreme motivation
        Regex("(no pain,? no gain|beast mode|crush it|destroy|go hard or go home|until you puke|to the max|insane|brutal|killer workout)"),
        // medical claims
        Regex("\\b(cures?|guarantee\\w*|treats your|heals your|reverses|detox)\\b"),
    )

    @Test fun `TC-COACH-001a no screen text body-shames, blames, punishes, hypes or makes medical claims`() {
        assertTrue("strings found", strings.size > 500)
        val bad = strings.flatMap { (name, text) -> prohibited.mapNotNull { r -> r.find(text.lowercase())?.let { "$name: '${it.value}' in \"$text\"" } } }
        assertTrue("COACH-001 wording:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun `TC-COACH-001b coach answers are brief, refer to a professional, and missed or stopped sessions are told without guilt`() {
        // Brief: every coach line, answer and suggested question fits in a few short sentences.
        val coach = strings.filterKeys { it.startsWith("coach_") || it.startsWith("oos_") || it.startsWith("q_") || it.startsWith("why_") }
        assertTrue(coach.size >= 40)
        coach.forEach { (name, text) -> assertTrue("$name is ${text.length} characters", text.length <= 260) }
        // SAF-009 referrals name someone who can help (and the urgent one says to stop and call).
        for (k in listOf("oos_medication", "oos_supplements", "oos_diet", "oos_medical", "oos_pain")) {
            assertTrue(k, Regex("doctor|pharmacist|dietitian|physiotherapist").containsMatchIn(strings.getValue(k)))
        }
        assertTrue(strings.getValue("oos_urgent").contains("stop exercising now") && strings.getValue("oos_urgent").contains("%1\$s"))
        // ADH-001/002: missed, partial and stopped sessions are described neutrally, and a streak is never "lost".
        val neutral = strings.filterKeys { it.startsWith("today_welcome") || it.startsWith("hist_outcome") || it.startsWith("hist_detail_stopped") ||
            it.startsWith("prog_streak") || it.startsWith("today_streak") || it.startsWith("ach_") }
        assertTrue(neutral.size >= 10)
        neutral.forEach { (name, text) ->
            assertTrue("$name: \"$text\"", !Regex("\\b(missed|failed|lost|reset|behind|only)\\b").containsMatchIn(text.lowercase()))
        }
    }
}
