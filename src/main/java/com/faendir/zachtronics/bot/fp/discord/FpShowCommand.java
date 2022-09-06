/*
 * Copyright (c) 2022
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

package com.faendir.zachtronics.bot.fp.discord;

import com.faendir.zachtronics.bot.discord.command.AbstractShowCommand;
import com.faendir.zachtronics.bot.discord.command.option.CommandOption;
import com.faendir.zachtronics.bot.discord.command.option.OptionHelpersKt;
import com.faendir.zachtronics.bot.fp.FpQualifier;
import com.faendir.zachtronics.bot.fp.model.FpCategory;
import com.faendir.zachtronics.bot.fp.model.FpPuzzle;
import com.faendir.zachtronics.bot.fp.model.FpRecord;
import com.faendir.zachtronics.bot.fp.repository.FpSolutionRepository;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import kotlin.Pair;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
@FpQualifier
public class FpShowCommand extends AbstractShowCommand<FpCategory, FpPuzzle, FpRecord> {
    private final CommandOption<String, FpPuzzle> puzzleOption = OptionHelpersKt.enumOptionBuilder("puzzle", FpPuzzle.class, FpPuzzle::getDisplayName)
            .description("Puzzle name. Can be shortened or abbreviated. E.g. `fake surv`, `HD`")
            .required()
            .build();
    private final CommandOption<String, FpCategory> categoryOption = OptionHelpersKt.enumOptionBuilder("category", FpCategory.class, FpCategory::getDisplayName)
            .description("Category. E.g. `CP`, `LC`")
            .required()
            .build();
    @Getter
    private final List<CommandOption<?, ?>> options = List.of(puzzleOption, categoryOption);
    @Getter
    private final FpSolutionRepository repository;

    @NotNull
    @Override
    public Pair<FpPuzzle, FpCategory> findPuzzleAndCategory(@NotNull ChatInputInteractionEvent event) {
        FpPuzzle puzzle = puzzleOption.get(event);
        FpCategory category = categoryOption.get(event);
        if (!puzzle.getSupportedCategories().contains(category))
            throw new IllegalArgumentException(
                    "Category " + category.getDisplayName() + " does not support " + puzzle.getDisplayName());
        return new Pair<>(puzzle, category);
    }
}
