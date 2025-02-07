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

package com.faendir.zachtronics.bot.cw.discord;

import com.faendir.zachtronics.bot.cw.CwQualifier;
import com.faendir.zachtronics.bot.cw.model.CwPuzzle;
import com.faendir.zachtronics.bot.cw.repository.CwSolutionRepository;
import com.faendir.zachtronics.bot.discord.command.AbstractRebuildCommand;
import com.faendir.zachtronics.bot.discord.command.option.CommandOption;
import com.faendir.zachtronics.bot.discord.command.option.OptionHelpersKt;
import com.faendir.zachtronics.bot.discord.command.security.Secured;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@CwQualifier
public class CwRebuildCommand extends AbstractRebuildCommand<CwPuzzle> {
    @Getter
    private final CommandOption<String, CwPuzzle> puzzleOption = OptionHelpersKt.enumOptionBuilder("puzzle", CwPuzzle.class, CwPuzzle::getDisplayName)
            .description("Puzzle name. Can be shortened or abbreviated. E.g. `Sig Cross`, `SNR`")
            .required()
            .build();
    @Getter
    private final List<CommandOption<?, ?>> options = List.of(puzzleOption);
    @Getter
    private final Secured secured = CwSecured.ADMINS_ONLY;
    @Getter
    private final CwSolutionRepository repository;
}
