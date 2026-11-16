package com.textpic.studio.ime

import com.textpic.studio.data.db.TemplateEntity

/** Filling in the `{name}`, `{product}` and `{price}` holes in a template. */
object TemplateRules {
    private val token = Regex("\\{([A-Za-z0-9_]+)\\}")

    /** The placeholders in the order they appear, without duplicates. */
    fun placeholders(body: String): List<String> =
        token.findAll(body).map { it.groupValues[1] }.distinct().toList()

    fun needsValues(template: TemplateEntity): Boolean = placeholders(template.body).isNotEmpty()

    /** Replaces every placeholder that has a value; the rest stay visible. */
    fun fill(body: String, values: Map<String, String>): String =
        token.replace(body) { match -> values[match.groupValues[1]] ?: match.value }
}
