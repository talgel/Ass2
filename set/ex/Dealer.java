package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ex.Player.panishOrScore;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    // players claimed sets
    // public volatile BlockingQueue<Player> claimedPlayer;
    public volatile Queue<Player> claimedSetPlayers;

    // The Dealers thread
    public Thread dealerThread;

    // Last time timer updated
    private long timeUpdated = System.currentTimeMillis();

    // Threads order list
    public Stack<Player> threadList;

    // Avoiding Magic Numbers- timeout for sleepUntilAwokenOrTimeout
    private final int napTime = 100;
    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    // private long reshuffleTime = Long.MAX_VALUE;
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        // new
        claimedSetPlayers = new LinkedList<Player>();
        threadList = new Stack<Player>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        synchronized (threadList) {
            for (Player player : players) {
                Thread t = new Thread(player);
                t.start();
                try {
                    threadList.wait();
                } catch (InterruptedException e) {
                }
            }
        }
        while (!shouldFinish()) {
            shuffleDeck();
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        terminate();
        announceWinners();

        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        timeUpdated = System.currentTimeMillis();
        while (!terminate && System.currentTimeMillis() - timeUpdated < env.config.turnTimeoutMillis) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeSetCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        while (!threadList.isEmpty()) {
            Player p = threadList.pop();
            p.terminate();
            try {
                p.playerThread.join();
            } catch (InterruptedException e) {
            }

        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeSetCardsFromTable() {
        while (!claimedSetPlayers.isEmpty()) {
            Player nextPlayer = claimedSetPlayers.peek();
            synchronized (nextPlayer.set) {
                synchronized (table) {
                    if (nextPlayer.set.size() == env.config.featureSize) {
                        int[] setCards = new int[env.config.featureSize];
                        for (int i = 0; i < env.config.featureSize; i++) {
                            Integer slot = nextPlayer.set.get(i);
                            Integer card = table.slotToCard[slot];
                            setCards[i] = card;
                        }
                        boolean isSet = env.util.testSet(setCards);
                        if (isSet) {
                            while (!nextPlayer.set.isEmpty()) {
                                Integer slot = nextPlayer.set.get(0);
                                smartRemove(slot);
                            }
                            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
                            timeUpdated = System.currentTimeMillis();
                            nextPlayer.penaltyOrScore = panishOrScore.SCORE;
                        } else {
                            while (!nextPlayer.set.isEmpty()) {
                                Integer slot = nextPlayer.set.get(0);
                                nextPlayer.set.remove((Integer) slot);
                                table.removeToken(nextPlayer.id, slot);
                            }
                            nextPlayer.penaltyOrScore = panishOrScore.PANISH;
                        }
                    }
                }
            }
            synchronized (claimedSetPlayers) {
                claimedSetPlayers.remove();
                claimedSetPlayers.notifyAll();
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        synchronized (table) {
            for (int i = 0; i < env.config.tableSize; i++) {
                if (table.slotToCard[i] == null && deck.size() > 0) {
                    table.placeCard(deck.remove(0), i);
                }
            }
        }

    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized (claimedSetPlayers) {
            try {
                claimedSetPlayers.wait(napTime);
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long timeElapsed = System.currentTimeMillis() - timeUpdated;
        long timer = env.config.turnTimeoutMillis - timeElapsed;
        if (timer < env.config.turnTimeoutWarningMillis) {
            if (timer < 0) {
                env.ui.setCountdown(0, true);
            } else {
                env.ui.setCountdown(timer, true);
            }

        } else {
            env.ui.setCountdown(timer, reset);
        }

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized (table) {
            for (int i = 0; i < env.config.tableSize; i++) {
                if (table.slotToCard[i] != null) {
                    int card = table.slotToCard[i];
                    deck.add(card);
                    smartRemove(i);
                }
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List<Player> winningPlayers = new LinkedList<Player>();

        for (Player player : players) {
            if (winningPlayers.isEmpty()) {
                winningPlayers.add(player);
            } else {
                if (player.score() == winningPlayers.get(0).score())
                    winningPlayers.add(player);
                else if (player.score() > winningPlayers.get(0).score()) {
                    winningPlayers.clear();
                    winningPlayers.add(player);
                }
            }
        }
        int[] winnerArray = new int[winningPlayers.size()];
        for (int i = 0; i < winningPlayers.size(); i++) {
            winnerArray[i] = winningPlayers.get(i).id;
        }
        env.ui.announceWinner(winnerArray);
    }

    public void smartRemove(int slot) {
        synchronized (table) {
            for (int i = 0; i < players.length; i++) {
                if (table.slotsToken[slot][i]) {
                    synchronized (players[i].set) {
                        players[i].set.remove((Integer) slot);
                    }
                }
            }

            table.removeCard(slot);
        }
    }

    public void shuffleDeck() {
        List<Integer> newDeck = new LinkedList<Integer>();
        while (!deck.isEmpty()) {
            int size = deck.size();
            newDeck.add(deck.remove((int) (Math.random() * size)));
        }
        while (!newDeck.isEmpty()) {
            deck.add(newDeck.remove(0));
        }

    }
}
