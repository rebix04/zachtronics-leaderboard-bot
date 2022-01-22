/*
 * Copyright (c) 2021
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

package com.faendir.zachtronics.bot.sc.repository;

import com.faendir.discord4j.command.parse.SingleParseResult;
import com.faendir.zachtronics.bot.BotTest;
import com.faendir.zachtronics.bot.reddit.RedditService;
import com.faendir.zachtronics.bot.reddit.Subreddit;
import com.faendir.zachtronics.bot.repository.CategoryRecord;
import com.faendir.zachtronics.bot.sc.model.*;
import com.faendir.zachtronics.bot.validation.ValidationResult;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.faendir.zachtronics.bot.sc.model.ScCategory.*;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;

@BotTest
@Disabled("Massive tests only for manual testing or migrations")
class SolRepoManualTest {

    @Autowired
    private ScSolutionRepository repository;
    @Autowired
    private RedditService redditService;

    @Test
    public void testFullIO() {
        for (ScPuzzle p : ScPuzzle.values()) {
            List<ValidationResult<ScSubmission>> submissions =
                    repository.findCategoryHolders(p, true)
                              .stream()
                              .map(CategoryRecord::getRecord)
                              .filter(not(ScRecord::isOldVideoRNG)) // would be added with no asterisk
                              .mapMulti(SolRepoManualTest::addRecordToSubmissions)
                              .<ValidationResult<ScSubmission>>map(ValidationResult.Valid::new)
                              .toList();

            repository.submitAll(submissions);

            System.out.println("Done " + p.getDisplayName());
        }
        System.out.println("Done");
    }

    private static void addRecordToSubmissions(@NotNull ScRecord record, Consumer<ScSubmission> cons) {
        if (record.getDataPath() != null) {
            try {
                String data = Files.readString(record.getDataPath());
                ScSubmission s = new ScSubmission(record.getPuzzle(), record.getScore(), record.getAuthor(),
                                                  record.getDisplayLink(), data);
                cons.accept(s);
            }
            catch (IOException ignored) {
            }
        }
    }

    @Test
    public void rebuildAllWiki() {
        for (ScPuzzle puzzle: ScPuzzle.values()) {
            repository.rebuildRedditLeaderboard(puzzle, "");
            System.out.println("Done " + puzzle.getDisplayName());
        }

        String pages = Arrays.stream(ScGroup.values())
                             .map(ScGroup::getWikiPage).distinct()
                             .map(p -> redditService.getWikiPage(Subreddit.SPACECHEM, p))
                             .map(s -> s.replaceAll("file:/tmp/sc-archive[0-9]+/",
                                                    "https://raw.githubusercontent.com/spacechem-community-developers/spacechem-archive/master"))
                             .collect(Collectors.joining("\n\n---\n"));
        System.out.println(pages);
    }

    @Test
    public void bootstrapPsv() throws IOException {
        Path repoPath = Paths.get("../spacechem/archive");

        for (ScPuzzle puzzle : ScPuzzle.values()) {
            Path indexPath = repoPath.resolve(puzzle.getGroup().name()).resolve(puzzle.name()).resolve("solutions.psv");
            try (ICSVWriter writer = new CSVWriterBuilder(Files.newBufferedWriter(indexPath)).withSeparator('|').build()) {

                for (CategoryRecord<ScRecord, ScCategory> cr : repository.findCategoryHolders(puzzle, true)) {
                    ScRecord record = cr.getRecord();
                    String author = record.getAuthor();
                    String categories = cr.getCategories().stream()
                                          .map(ScCategory::name)
                                          .collect(Collectors.joining(","));

                    String[] csvRecord = new String[]{record.getScore().toDisplayString(),
                                                      author,
                                                      record.getDisplayLink(),
                                                      record.isOldVideoRNG() ? "linux" : null,
                                                      categories};
                    writer.writeNext(csvRecord, false);
                }
            }
        }
    }

    @Test
    public void tagNewCategories() throws IOException {
        Path repoPath = Paths.get("../spacechem/archive");
        List<ScCategory> newCategories = List.of(CNBP, SNBP, RCNBP, RSNBP);

        for (ScPuzzle puzzle : ScPuzzle.values()) {
            Path puzzlePath = repoPath.resolve(puzzle.getGroup().name()).resolve(puzzle.name());
            List<ScSolution> solutions = repository.unmarshalSolutions(puzzlePath);
            if (solutions.isEmpty())
                continue;
            for (ScCategory category : newCategories) {
                if (!puzzle.getSupportedCategories().contains(category))
                    continue;
                solutions.stream()
                         .filter(s -> category.supportsScore(s.getScore()))
                         .min(Comparator.comparing(ScSolution::getScore, category.getScoreComparator()))
                         .orElseThrow()
                         .getCategories()
                         .add(category);
            }
            repository.marshalSolutions(solutions, puzzlePath);
        }
    }

    @Test
    public void pushStateAuthorsToSolutionFiles() throws IOException {
        Path repoPath = Paths.get("../spacechem/archive");

        for (ScPuzzle puzzle : ScPuzzle.values()) {
            Path puzzlePath = repoPath.resolve(puzzle.getGroup().name()).resolve(puzzle.name());
            List<ScSolution> solutions = repository.unmarshalSolutions(puzzlePath);
            for (ScSolution solution : solutions) {
                Path path = puzzlePath.resolve(ScSolutionRepository.makeScoreFilename(solution.getScore()));
                if (Files.exists(path)) {
                    String export = Files.readString(path);
                    ScSolutionMetadata metadata = ScSolutionMetadata.fromHeader(export, puzzle);
                    if (!solution.getAuthor().equalsIgnoreCase(metadata.getAuthor())) {
                        ScSolutionMetadata newMetadata = new ScSolutionMetadata(puzzle, solution.getAuthor(), solution.getScore(),
                                                                                metadata.getDescription());
                        ScSubmission submission = newMetadata.extendToSubmission(null, export);
                        Files.writeString(path, submission.getData(), StandardOpenOption.TRUNCATE_EXISTING);
                    }
                }
            }
        }
    }

    @Test
    public void loadSolnetVideos() throws IOException {
        Path solnetDumpPath = Paths.get("../spacechem/solutionnet/data/score_dump.csv");
        Path repoPath = Paths.get("../spacechem/archive");

        CSVReader reader = new CSVReaderBuilder(Files.newBufferedReader(solnetDumpPath)).withFieldAsNull(CSVReaderNullFieldIndicator.BOTH)
                                                                                        .withSkipLines(1)
                                                                                        .build();
        Map<ScPuzzle, List<ScSubmission>> solnetSubmissions = StreamSupport.stream(reader.spliterator(), false)
                                                                           .map(SolRepoManualTest::fromSolnetData)
                                                                           .filter(s -> s.getDisplayLink() != null)
                                                                           .filter(s -> s.getPuzzle() != ScPuzzle.warp_boss)
                                                                           .collect(groupingBy(ScSubmission::getPuzzle));

        for (ScPuzzle puzzle : ScPuzzle.values()) {
            List<ScSubmission> puzzleSubmissions = solnetSubmissions.get(puzzle);
            if (puzzleSubmissions == null)
                continue;
            Path puzzlePath = repoPath.resolve(puzzle.getGroup().name()).resolve(puzzle.name());
            List<ScSolution> solutions = repository.unmarshalSolutions(puzzlePath);
            List<ScSolution> newSolutions = new ArrayList<>();
            boolean edited = false;
            for (ScSolution solution : solutions) {
                String matchingVideo = null;
                if (solution.getDisplayLink() == null) {
                    ScScore searchScore = new ScScore(solution.getScore().getCycles(), solution.getScore().getReactors(),
                                                      solution.getScore().getSymbols(), false, false);
                    String searchAuthor = solution.getAuthor();
                    matchingVideo = puzzleSubmissions.stream()
                                                            .filter(s -> s.getScore().equals(searchScore) &&
                                                                         s.getAuthor().equalsIgnoreCase(searchAuthor))
                                                            .findFirst()
                                                            .map(ScSubmission::getDisplayLink)
                                                            .orElse(null);
                }

                if (matchingVideo != null) {
                    String cleanVideoLink = matchingVideo.replaceAll("&hd=1|hd=1&|&feature=share|&feature=related", "");
                    newSolutions.add(new ScSolution(solution.getScore(), solution.getAuthor(), cleanVideoLink, false));
                    edited = true;
                }
                else {
                    newSolutions.add(solution);
                }
            }
            if (edited) {
                repository.marshalSolutions(newSolutions, puzzlePath);
            }
        }
    }

    @NotNull
    private static ScSubmission fromSolnetData(@NotNull String[] fields) {
        // Username,Level Category,Level Number,Level Name,Reactor Count,Cycle Count,Symbol Count,Upload Time,Youtube Link
        // Iridium,63corvi,1,QT-1,1,20,5,2011-07-09 07:51:58.320983,https://www.youtube.com/watch?v=hRM5IpSv5aU
        assert fields.length == 9 : Arrays.toString(fields);
        String author = fields[0];

        String levelName = StringEscapeUtils.unescapeHtml3(fields[3]);
        SingleParseResult<ScPuzzle> puzzleParseResult = ScPuzzle.parsePuzzle(levelName);
        if (puzzleParseResult instanceof SingleParseResult.Ambiguous) {
            puzzleParseResult = ScPuzzle.parsePuzzle(levelName + " (" + fields[2] + ")");
        }
        ScPuzzle puzzle = puzzleParseResult.orElse(ScPuzzle.warp_boss);

        ScScore score = new ScScore(Integer.parseInt(fields[5]), Integer.parseInt(fields[4]), Integer.parseInt(fields[6]), false, false);
        String videoLink = fields[8];
        return new ScSubmission(puzzle, score, author, videoLink, "");
    }
}