package model;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class GameTest {

    @Test
    public void completeTest() {
        final String[] moves = new String[] {
                "N1",
                "N2",
                "S1",
                "N1",
                "S6",
                "N2",
                "S4",
                "N2",
                "S3",
                "N2",
                "S2",
                "N1",
                "S6",
                "N2",
                "S5",
                "N1",
                "S6",
                "S4",
                "N2",
                "S5",
                "N6",
                "S3",
                "N1",
                "S6",
                "S4",
                "N5",
                "S6",
                "S5",
                "S4",
                "N6",
                "N1",
                "S6",
                "S2",
                "N4",
                "S1",
                "N6",
                "N5",
                "S3",
                "S5",
                "S4",
                "N1"
        };

        final boolean turn = true;
        final int startingIndex = 0;
        final Game game = new Game(turn, startingIndex);

        for (String str : moves) {
            final int move = Integer.parseInt(str.substring(1));
            game.move(game.normalized(move));
        }

        final int[] pits = game.getState();

        assertThat(pits[Game.SMALL_PIT_NUMBER_PER_USER], is(23));
        assertThat(pits[pits.length - 1], is(44));
    }
}
