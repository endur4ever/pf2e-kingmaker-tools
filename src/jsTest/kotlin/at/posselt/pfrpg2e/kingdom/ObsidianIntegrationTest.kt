package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.sheet.ObsidianImporter
import kotlin.test.Test
import kotlin.test.assertEquals

class ObsidianIntegrationTest {

    @Test
    fun testMarkdownToHtmlParagraphs() {
        val markdown = listOf(
            "Hello World",
            "",
            "This is a paragraph"
        )
        val expected = "<p>Hello World</p>\n<p></p>\n<p>This is a paragraph</p>\n"
        val html = ObsidianImporter.markdownToHtml(markdown)
        assertEquals(expected, html)
    }

    @Test
    fun testMarkdownToHtmlHeaders() {
        val markdown = listOf(
            "# Header 1",
            "## Header 2",
            "### Header 3"
        )
        val expected = "<h1>Header 1</h1>\n<h2>Header 2</h2>\n<h3>Header 3</h3>\n"
        val html = ObsidianImporter.markdownToHtml(markdown)
        assertEquals(expected, html)
    }

    @Test
    fun testMarkdownToHtmlLists() {
        val markdown = listOf(
            "- Item 1",
            "- Item 2",
            "* Item 3"
        )
        val expected = "<ul>\n<li>Item 1</li>\n<li>Item 2</li>\n<li>Item 3</li>\n</ul>\n"
        val html = ObsidianImporter.markdownToHtml(markdown)
        assertEquals(expected, html)
    }

    @Test
    fun testMarkdownToHtmlInlineFormatting() {
        val markdown = listOf(
            "This is **bold** text",
            "This is *italic* text",
            "This is **bold** and *italic* combined"
        )
        val expected = "<p>This is <strong>bold</strong> text</p>\n<p>This is <em>italic</em> text</p>\n<p>This is <strong>bold</strong> and <em>italic</em> combined</p>\n"
        val html = ObsidianImporter.markdownToHtml(markdown)
        assertEquals(expected, html)
    }

    @Test
    fun testMarkdownToHtmlMixedStructure() {
        val markdown = listOf(
            "# Title",
            "Intro paragraph with *italics*.",
            "- List item 1 with **bold**",
            "- List item 2",
            "",
            "Final conclusion."
        )
        val expected = "<h1>Title</h1>\n" +
                "<p>Intro paragraph with <em>italics</em>.</p>\n" +
                "<ul>\n" +
                "<li>List item 1 with <strong>bold</strong></li>\n" +
                "<li>List item 2</li>\n" +
                "</ul>\n" +
                "<p></p>\n" +
                "<p>Final conclusion.</p>\n"
        val html = ObsidianImporter.markdownToHtml(markdown)
        assertEquals(expected, html)
    }
}
