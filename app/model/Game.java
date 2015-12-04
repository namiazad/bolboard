package model;

import play.Logger;

import java.util.Arrays;

public class Game {
    public final static int STONE_NUMBER_PER_PIT = 6;
    public final static int SMALL_PIT_NUMBER_PER_USER = 6;

    private boolean turn = false;
    private int[] pits = new int[2 * SMALL_PIT_NUMBER_PER_USER + 2]; // {6, 6, 6, 6, 6, 6, 0, 6, 6, 6, 6, 6, 6, 0}
    private final int userStartingIndex;
    private final int opponentStartingIndex;

    public Game(final boolean turn, final int userStartingIndex) {
        this.turn = turn;
        this.userStartingIndex = userStartingIndex;

        if (userStartingIndex == 0) {
            opponentStartingIndex = SMALL_PIT_NUMBER_PER_USER + 1;
        } else {
            opponentStartingIndex = 0;
        }

        for (int i = 0; i < SMALL_PIT_NUMBER_PER_USER; i++) {
            pits[i] = STONE_NUMBER_PER_PIT;
        }

        pits[SMALL_PIT_NUMBER_PER_USER] = 0;

        for (int i = SMALL_PIT_NUMBER_PER_USER + 1; i < pits.length - 1; i++) {
            pits[i] = STONE_NUMBER_PER_PIT;
        }

        pits[pits.length - 1] = 0;
    }

    public boolean getTurn() {
        return turn;
    }

    public int[] getState() {
        return Arrays.copyOf(pits, pits.length);
    }

    private int modularized(int index) {
        return index % pits.length;
    }

    private boolean isInUserRange(final int pitIndex) {
        final int m = modularized(pitIndex);
        return m >= userStartingIndex && m < userStartingIndex + SMALL_PIT_NUMBER_PER_USER;
    }

    private boolean isInOpponentRange(final int pitIndex) {
        final int m = modularized(pitIndex);

        return m >= opponentStartingIndex && m < opponentStartingIndex + SMALL_PIT_NUMBER_PER_USER;
    }

    private boolean isInPlayerRange(final int pitIndex) {
        return (turn && isInUserRange(pitIndex)) || (!turn && isInOpponentRange(pitIndex));
    }

    private int userLargePit() {
        return userStartingIndex + SMALL_PIT_NUMBER_PER_USER;
    }

    private int opponentLargePit() {
        return userStartingIndex == 0 ? pits.length - 1 : userStartingIndex - 1;
    }

    private boolean isLargePitOfPlayer(final int pitIndex) {
        final int modularized = modularized(pitIndex);

        return ((turn && (modularized == userLargePit())) || (!turn && (modularized == opponentLargePit())));
    }

    /**
     * To apply the move on board state, if it is your turn other player's large pit is forbidden, if it is other
     * player's large pit, your large pit is forbidden.
     */
    private boolean isForbiddenLargePit(final int pitIndex) {
        final int modularized = modularized(pitIndex);

        return (turn && (modularized == opponentLargePit())) || (!turn && (modularized == userLargePit()));
    }

    private int playerLargePitIndex() {
        return turn ? userLargePit() : opponentLargePit();
    }
    
    private int getOppositeIndex(final int pitIndex) {
        return 2 * SMALL_PIT_NUMBER_PER_USER - modularized(pitIndex);
    }

    private boolean isEnded() {
        boolean ended = true;
        for (int i = 0; i < SMALL_PIT_NUMBER_PER_USER; i++) {
            if (pits[i] != 0) {
                ended = false;
                break;
            }
        }

        if (!ended) {
            ended = true;
            for (int i = SMALL_PIT_NUMBER_PER_USER + 1; i < pits.length - 1; i++) {
                if (pits.length != 0) {
                    ended = false;
                    break;
                }
            }
        }

        return ended;
    }

    /**
     * Normalizes and index between 1 to 6 to an index between 0 to 13 (both ends inclusive)
     * @param pitIndex
     * @return
     */
    public int normalized(int pitIndex) {
        pitIndex--;

        final int normalized = turn ? pitIndex + userStartingIndex : pitIndex + opponentStartingIndex;

        Logger.debug("Pit index was {} but normalized to {}", pitIndex, normalized);

        return normalized;
    }

    /**
     * @return true if it was the last move (game ended).
     */
    public boolean move(int pitIndex) {
        Logger.debug("Move {} received!", pitIndex);
        Logger.debug("User starting index is: {}. opponent starting index is {}.", userStartingIndex, opponentStartingIndex);

        if (pitIndex < 0 || pitIndex >= pits.length) {
            Logger.debug("Invalid pit number {}", pitIndex);
        } else if (!isInPlayerRange(pitIndex)) {
            Logger.debug("Invalid move: turn {}, number {}", turn, pitIndex);
        } else if (pits[pitIndex] == 0) {
            Logger.debug("Pit {} is empty!", pitIndex);
        } else {
            int stones = pits[pitIndex];

            int index = pitIndex + 1;
            while (stones != 0) {
                if (!isForbiddenLargePit(index)) {
                    stones--;
                    int mod = modularized(index);
                    pits[mod]++;
                    pits[pitIndex]--;
                }
                index++;
            }

            int mod = modularized(index - 1);

            if (isInPlayerRange(mod) && pits[mod] == 1) {
                pits[mod] = 0;
                pits[playerLargePitIndex()]++;
                pits[playerLargePitIndex()] += pits[getOppositeIndex(mod)];
                pits[getOppositeIndex(mod)] = 0;
            }

            if (!isLargePitOfPlayer(mod)) {
                turn = !turn;
            }
        }

        Logger.debug("Board state is {}", MessageProtocols.GameProtocol.buildGameStateMessage(this));

        return isEnded();
    }
}
