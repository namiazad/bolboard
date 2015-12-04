package model;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

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

    /**
     * Verifies that if the last stone ends in the user large pit, he can do one extra move
     */
    @Test
    public void testTurnChange() {
        final boolean turn = true;
        final int startingIndex = 0;
        final Game game = new Game(turn, startingIndex);

        game.move(0);
        assertTrue(game.getTurn());

        game.move(1);
        assertFalse(game.getTurn());
    }

    @Test
    public void testNormalizingIndex() {
        final Game game = new Game(true, 0);

        //Input index is assumed to be between 1 and 6
        assertThat(game.normalized(3), is(2));

        final Game game2 = new Game(true, 7);

        //Input index is assumed to be between 1 and 6
        assertThat(game2.normalized(3), is(9));
        assertThat(game2.normalized(6), is(12));
    }

    @Test
    public void testCapturingCase() {
        final Game game = new Game(true, 0);

        game.moveToState(new int[] {6, 6, 6, 1, 0, 6, 11, 1, 2, 3, 4, 5, 6, 15});

        game.move(game.normalized(4));

        //if the last stone ends in a use's empty pit, that stone and all stones of the pit in front of that will be
        //moved to the user's large pit
        assertArrayEquals(game.getState(), new int[] {6, 6, 6, 0, 0, 6, 14, 1, 0, 3, 4, 5, 6, 15});
    }

    @Test
    public void testIgnoreNegativeMove() {
        final Game game = new Game(true, 0);
        final int[] state = game.getState();

        game.move(-2);
        assertArrayEquals(game.getState(), state);
    }

    @Test
    public void testIgnoreLargeMove() {
        final Game game = new Game(true, 0);
        final int[] state = game.getState();

        game.move(14);
        assertArrayEquals(game.getState(), state);
    }

    @Test
    public void testIgnoreNonPlayerIndex() {
        final Game game = new Game(true, 0);
        final int[] state = game.getState();

        for (int i = 6; i < 14; i++) {
            game.move(i);
            assertArrayEquals(game.getState(), state);
        }

        final Game game2 = new Game(false, 0);
        final int[] state2 = game2.getState();

        for (int i = 0; i < 7; i++) {
            game2.move(i);
            assertArrayEquals(game2.getState(), state2);
        }
    }

    @Test
    public void testSelectingFromEmptyPitIgnored() {
        final Game game = new Game(true, 0);
        game.moveToState(new int[] {6, 6, 6, 1, 0, 6, 11, 1, 2, 3, 4, 5, 6, 15});

        final int[] state = game.getState();

        game.move(game.normalized(5));

        assertArrayEquals(game.getState(), state);
        assertTrue(game.getTurn());
    }

    @Test
    public void testGameEnding() {
        final Game game = new Game(false, 0);
        game.moveToState(new int[] {0, 0, 0, 0, 0, 1, 11, 1, 2, 3, 4, 5, 6, 15});

        //It is other user turn, he applied a move but still the game is going on.
        assertFalse(game.move(game.normalized(1)));

        //It is user game and with the move that he has, the game will end.
        assertTrue(game.move(game.normalized(6)));
    }

    @Test
    public void testCircularSowing() {
        final Game game = new Game(false, 0);
        game.moveToState(new int[] {6, 6, 6, 1, 0, 6, 11, 1, 2, 3, 4, 5, 6, 15});

        //It is opponent's turn, he applied a move which results in putting stone in the user's pit.
        assertFalse(game.move(game.normalized(6)));

        //It is user game and with the move that he has, the game will end.
        assertArrayEquals(game.getState(), new int[] {7, 7, 7, 2, 1, 6, 11, 1, 2, 3, 4, 5, 0, 16});
    }

    //TODO: More tests can be added.
}
