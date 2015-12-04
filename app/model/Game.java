package model;

import play.Logger;

public class Game {
    public final static int STONE_NUMBER_PER_PIT = 6;
    public final static int SMALL_PIT_NUMBER_PER_USER = 6;

    private boolean turn = false;
    private int[] pits = new int[2 * SMALL_PIT_NUMBER_PER_USER + 2]; // {6, 6, 6, 6, 6, 6, 0, 6, 6, 6, 6, 6, 6, 0}
    private final int userStartingIndex;

    public Game(final boolean turn, final int userStartingIndex) {
        this.turn = turn;
        this.userStartingIndex = userStartingIndex;


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

    private int modularized(int index) {
        return index % pits.length;
    }

    private boolean isInUserRange(final int pitIndex) {
        final int m = modularized(pitIndex);
        return m >= userStartingIndex && m < userStartingIndex + SMALL_PIT_NUMBER_PER_USER;
    }

    private boolean isInPlayerRange(final int pitIndex) {
        return turn == isInUserRange(modularized(pitIndex));
    }

    private int userLargePit() {
        return userStartingIndex + SMALL_PIT_NUMBER_PER_USER;
    }

    private int opponentLargePit() {
        return userStartingIndex == 0 ? pits.length - 1 : userStartingIndex - 1;
    }

    private boolean isLargePit(final int pitIndex) {
        return modularized(pitIndex) == userLargePit();
    }

    private boolean isOpponentLargePit(final int pitIndex) {
        return modularized(pitIndex) == opponentLargePit();
    }

    private int playerLargePitIndex() {
        if (turn) {
            return userLargePit();
        } else {
            return opponentLargePit();
        }
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
     * @return true if it was the last move (game ended).
     */
    public boolean move(int pitIndex) {
        pitIndex = modularized(pitIndex);

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
                if (!isOpponentLargePit(index)) {
                    stones--;
                    int mod = modularized(index);
                    pits[mod]++;
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

            if (!isLargePit(mod)) {
                turn = !turn;
            }
        }

        return isEnded();
    }
}
