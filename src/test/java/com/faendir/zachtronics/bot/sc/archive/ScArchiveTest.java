package com.faendir.zachtronics.bot.sc.archive;

import com.faendir.zachtronics.bot.Application;
import com.faendir.zachtronics.bot.BotTest;
import com.faendir.zachtronics.bot.archive.ArchiveResult;
import com.faendir.zachtronics.bot.sc.model.ScPuzzle;
import com.faendir.zachtronics.bot.sc.model.ScScore;
import com.faendir.zachtronics.bot.sc.model.ScSolution;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

@BotTest(Application.class)
public class ScArchiveTest {

    @Autowired
    private ScArchive archive;

    @Test
    public void testArchiveScore() {
        // we start with a 100/100/100
        ScScore score = new ScScore(100, 100, 100, true, true);
        assertTrue(doArchiveScore(score) instanceof ArchiveResult.Failure); // our score is a BP, fail
        score = new ScScore(100, 100, 100, false, true);
        assertTrue(doArchiveScore(score) instanceof ArchiveResult.Failure); // our score is a P, fail
        score = new ScScore(100, 100, 100, false, false);
        assertTrue(doArchiveScore(score) instanceof ArchiveResult.Success); // identical score, different content, accept
        assertTrue(doArchiveScore(score) instanceof ArchiveResult.AlreadyArchived); // identical score and content, we have it
        score = new ScScore(10, 100, 1000, true, true);
        assertTrue(doArchiveScore(score) instanceof ArchiveResult.Success); // new frontier piece
        score = new ScScore(1000, 100, 10, true, true);
        assertTrue(doArchiveScore(score) instanceof ArchiveResult.Success); // new frontier piece
        score = new ScScore(10, 10, 10, true, true);
        assertTrue(doArchiveScore(score) instanceof ArchiveResult.Success); // beats them both
        score = new ScScore(20, 20, 20, true, true);
        assertTrue(doArchiveScore(score) instanceof ArchiveResult.Failure); // fails flat
        score = new ScScore(20, 20, 20, false, true);
        assertTrue(doArchiveScore(score) instanceof ArchiveResult.Success); // new non bugged record
    }

    @Test
    public void testArchiveAttachment() {
        // we start at 100/100/100
        ScScore score = new ScScore(50, 50, 50, false, false);
        assertTrue(doArchiveScore(score) instanceof ArchiveResult.Success); // 50/50/50

        String content = "SOLUTION:Of Pancakes and Spaceships,12345ieee,45-1-14\nbunch of stuff...";
        assertTrue(doArchiveContent(content) instanceof ArchiveResult.Success); // 45/1/14
    }

    @NotNull
    private ArchiveResult doArchiveScore(ScScore score) {
        return archive.archive(new ScSolution(ScPuzzle.research_example_1, score, ""));
    }

    @NotNull
    private ArchiveResult doArchiveContent(String content) {
        return archive.archive(ScSolution.fromContentNoValidation(content, null));
    }
}
