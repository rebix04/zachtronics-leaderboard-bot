/*
 * Copyright (c) 2023
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.faendir.zachtronics.bot.utils

import com.faendir.zachtronics.bot.discord.Colors
import com.faendir.zachtronics.bot.model.*
import com.faendir.zachtronics.bot.repository.CategoryRecord
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent
import discord4j.core.event.domain.interaction.InteractionCreateEvent
import discord4j.core.`object`.entity.GuildEmoji
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.InteractionReplyEditMono
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.math.pow

private val wordSeparator = Regex("[\\s-/,:]+")

fun <P> Collection<P>.fuzzyMatch(search: String, name: P.() -> String): List<P> {
    return search.takeIf { it.isEmpty() }?.let { emptyList() }
        ?: find {
            // exact match
            it.name().equals(search, ignoreCase = true)
        }?.let { listOf(it) }
        ?: filter {
            //abbreviation
            val words = it.name().split(wordSeparator)
            search.length == words.size && words.zip(search.asIterable()).all { (word, char) -> word.startsWith(char, ignoreCase = true) }
        }.takeIf { it.isNotEmpty() }
        ?: filter {
            //words contain match
            val words = it.name().split(wordSeparator).iterator()
            for (part in search.split(wordSeparator)) {
                do {
                    if (!words.hasNext()) return@filter false
                } while (!words.next().contains(part, ignoreCase = true))
            }
            true
        }
}

fun Collection<Category>.toMetricsTree(): TreeRoot<Pair<Metric, Category?>> = TreeRoot<Pair<Metric, Category?>>().also { tree ->
    forEach { category -> tree.addPath(category.metrics.dropLast(1).map { it to null } + (category.metrics.last() to category)) }
}

fun Collection<Category>.smartFormat(reference: TreeRoot<Pair<Metric, Category?>>): String {
    val metricsTree = toMetricsTree()
    metricsTree.collapseFullyPresentNodes(reference)
    val shortenedCategories = metricsTree.getAllPaths()
        .map { list -> list.map { it.first } to list.last().second }
        .map { (metrics, category) -> category?.displayName ?: metrics.joinToString("") { it.displayName } }
    return shortenedCategories.joinToString(", ")
}

@Suppress("UNCHECKED_CAST")
fun <B: SafeEmbedMessageBuilder<B>, R : Record<C>?, C : Category> B.embedCategoryRecords(
    records: Iterable<CategoryRecord<R, C>>,
    supportedCategories: List<C> = emptyList()
): B {
    return embedRecords(
        records = records.map { cr -> cr.copy(categories = cr.categories.sortedBy { it as? Comparable<Any> }.toCollection(LinkedHashSet())) }
            .sortedWith(
                Comparator.comparing(
                    { it.categories.firstOrNull() as? Comparable<Any> },
                    Comparator.nullsLast(Comparator.naturalOrder<Comparable<Any>>())
                )
            ),
        supportedCategories = supportedCategories,
        formatCategorySpecific = true
    )
}

fun <B: SafeEmbedMessageBuilder<B>, R : Record<C>?, C : Category> B.embedRecords(
    records: Iterable<CategoryRecord<R, C>>,
    supportedCategories: List<C> = emptyList(),
    formatCategorySpecific: Boolean = false
): B {
    val reference = supportedCategories.toMetricsTree()
    return this.addFields(
        records.map { (record, categories) ->
            EmbedCreateFields.Field.of(
                categories.smartFormat(reference).ifEmptyZeroWidthSpace(),
                record?.toDisplayString(DisplayContext(StringFormat.DISCORD, categories.takeIf { formatCategorySpecific })) ?: "none",
                true
            )
        }
    )
}

fun InteractionCreateEvent.user(): User =
    this.interaction.member.map { it as User }.orElse(this.interaction.user)

fun InteractionReplyEditMono.clear() = withContentOrNull(null).withComponentsOrNull(null).withEmbedsOrNull(null)

fun DeferrableInteractionEvent.editReplyWithFailure(message: String?) =
    editReply().withEmbeds(
        EmbedCreateSpec.builder()
            .title("Failed")
            .color(Colors.FAILURE)
            .description(message?.truncateWithEllipsis(EmbedLimits.DESCRIPTION) ?: "Something went wrong")
            .build()
    ).then()

fun String.truncateWithEllipsis(maxLength: Int) = if (length > maxLength) substring(0, maxLength - 1) + "…" else this

fun String.ifEmptyZeroWidthSpace() = ifEmpty { "\u200B" }

fun <K, V, C : MutableCollection<V>> MutableMap<K, C>.add(key: K, values: C) {
    if (containsKey(key)) {
        getValue(key) += values
    } else {
        put(key, values)
    }
}

inline fun <T : Closeable?, U : Closeable?, R> Pair<T, U>.use(block: (T, U) -> R): R {
    try {
        return block(first, second)
    } finally {
        try {
            first?.close()
        } catch (closeException: Throwable) {
        }
        try {
            second?.close()
        } catch (closeException: Throwable) {
        }
    }
}

fun String?.orEmpty(prefix: String = "", suffix: String = "") = this?.let { prefix + it + suffix } ?: ""

inline fun <reified T: Enum<T>> newEnumSet(): EnumSet<T> = EnumSet.noneOf(T::class.java)

fun isValidLink(string: String): Boolean {
    return try {
        val connection = URL(string).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.setRequestProperty("Accept", "*/*")
        connection.responseCode in (200 until 400) // accept all redirects as well
    } catch (e: Exception) {
        false
    }
}

fun Double.ceil(precision: Int): Double {
    val factor = 10.0.pow(precision)
    return kotlin.math.ceil(this * factor) / factor
}

val Message.url
    get() = "https://discord.com/channels/${guild.map { it.id.asString() }.block() ?: "@me"}/${channelId.asString()}/${id.asString()}"

fun GuildEmoji.asReaction() = ReactionEmoji.custom(this)