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

package com.faendir.zachtronics.bot.om.discord


import com.faendir.zachtronics.bot.discord.command.Command
import com.faendir.zachtronics.bot.discord.command.option.enumOptionBuilder
import com.faendir.zachtronics.bot.discord.command.security.DiscordUser
import com.faendir.zachtronics.bot.discord.command.security.DiscordUserSecured
import com.faendir.zachtronics.bot.model.DisplayContext
import com.faendir.zachtronics.bot.om.*
import com.faendir.zachtronics.bot.om.model.OmPuzzle
import com.faendir.zachtronics.bot.om.model.OmRecord
import com.faendir.zachtronics.bot.om.model.OmScore
import com.faendir.zachtronics.bot.om.model.OmType
import com.faendir.zachtronics.bot.om.repository.OmSolutionRepository
import com.faendir.zachtronics.bot.utils.MultiMessageSafeEmbedMessageBuilder
import com.faendir.zachtronics.bot.utils.SafeMessageBuilder
import com.faendir.zachtronics.bot.utils.orEmpty
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import org.springframework.stereotype.Component
import kotlin.io.path.readBytes

@Component
@OmQualifier
class OmReverifyCommand(private val repository: OmSolutionRepository) : Command.BasicLeaf() {
    override val name = "reverify"
    override val description: String = "Recompute metrics based on filters"

    private val typeOption = enumOptionBuilder<OmType>("type") { displayName }.build()
    private val puzzleOption = omPuzzleOptionBuilder().build()
    private val scoreOption = omScoreOptionBuilder().build()
    override val options = listOf(typeOption, puzzleOption, scoreOption)

    override val secured = DiscordUserSecured(DiscordUser.OM_LB_ADMINS)

    override fun handleEvent(event: ChatInputInteractionEvent): SafeMessageBuilder {
        val puzzleIn = puzzleOption.get(event)
        val type = typeOption.get(event)
        val score = scoreOption.get(event)
        val puzzles = (puzzleIn?.let { listOf(it) } ?: OmPuzzle.entries).filter {
            type == null || type == it.type
        }
        val overrideRecords = mutableListOf<Pair<OmRecord, OmScore>>()
        for (puzzle in puzzles) {
            val puzzleFile = puzzle.file

            val records = repository.findCategoryHolders(puzzle, true)
                .filter { (record, _) -> score == null || record.score.toDisplayString().equals(score, ignoreCase = true) }
            for ((record, _) in records) {
                val newScore = JNISolutionVerifier.open(puzzleFile.readBytes(), record.dataPath.readBytes())
                    .use { verifier -> verifier.getScore(puzzle.type) }
                if (newScore != record.score) {
                    overrideRecords.add(record to newScore)
                }
            }
        }
        if (overrideRecords.isNotEmpty()) {
            repository.overrideScores(overrideRecords)
        }
        return MultiMessageSafeEmbedMessageBuilder().title(
                "Reverify" + type?.displayName.orEmpty(" ") + puzzleIn?.displayName.orEmpty(" ")
            )
            .description(
                """
                    **Modified Records:** ${overrideRecords.size}
                    
                    **Changed Scores:**
                    ${overrideRecords.joinToString("\n") { it.second.toDisplayString(DisplayContext.discord()) }}
                    """.trimIndent()
            )
    }
}
