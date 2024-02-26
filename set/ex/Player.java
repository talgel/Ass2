package bguspl.set.ex;

import java.util.Queue;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    public Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    // Queue holding the incoming presses
    private Queue<Integer> q;

    // list holding the players slots
    public List<Integer> set;

    // int determining if player should be scored (1), penalized (2) or dismissed
    // (0)
    public volatile panishOrScore penaltyOrScore;

    // Pointer to the dealer object
    public Dealer dealer;

    // whether the player is freezed or not
    private boolean freezed;

    //Avoiding magic numbers :
    // AI sleep time between two presses
    private final int aiTime = 20;
    //while being freezed, sleep time between countdown update
    private final int freezeUpdateTime = 100;
    
    //Enum class to determine dealer reaction for set
    public enum panishOrScore {
        NON,
        SCORE,
        PANISH,
    }
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */

    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        q = new LinkedList<Integer>();
        penaltyOrScore = panishOrScore.NON;
        set = new ArrayList<Integer>();
        freezed = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        synchronized(dealer.threadList) {
            playerThread = Thread.currentThread();
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            dealer.threadList.push(this);
            if (!human)
                createArtificialIntelligence();
            dealer.threadList.notifyAll();
        }
        while (!terminate) {
            penaltyOrScore = panishOrScore.NON;
            synchronized (q) {
                while (q.isEmpty()) {
                    try {
                        q.wait();
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
                if (!terminate) {
                    Integer nextSlot = q.remove();
                    synchronized (table) {
                        synchronized (set) {
                            if (table.removeToken(id, nextSlot)) {
                                set.remove((Integer) nextSlot);
                            } else {
                                if (set.size() < env.config.featureSize) {  
                                    if (table.slotToCard[nextSlot] != null){
                                        table.placeToken(id, nextSlot);
                                        set.add(nextSlot);
                                    }
                                }
                            }
                        }

                        q.notifyAll();
                    }
                }

            }
            if (!terminate && set.size() == env.config.featureSize) {
                synchronized (dealer.claimedSetPlayers) {
                    dealer.claimedSetPlayers.add(this);
                    dealer.claimedSetPlayers.notifyAll();
                    while (!terminate && dealer.claimedSetPlayers.contains(this)) {
                        try {
                            dealer.claimedSetPlayers.wait();
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
                if (penaltyOrScore == panishOrScore.SCORE) {
                    this.point();

                } else if (penaltyOrScore == panishOrScore.PANISH) {
                    this.penalty();

                }
            }
        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */

    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                try {
                    Thread.sleep(aiTime);
                } catch (InterruptedException e) {
                }
                synchronized (q) {
                    while (!terminate && isFull()) {
                        try {
                            q.wait();
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                }
                int randomSlot = (int) (Math.random() * env.config.tableSize);
                keyPressed(randomSlot);

            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        playerThread.interrupt();
        if (!human)
            aiThread.interrupt();

    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        synchronized (q) {
            if (table.slotToCard[slot] != null && !isFull() && !freezed) {
                q.add(slot);
                q.notifyAll();
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        long currTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - currTime <= env.config.pointFreezeMillis) {
            freezed = true;
            try {
                Thread.sleep(freezeUpdateTime);
            } catch (InterruptedException ignored) {
            }
            env.ui.setFreeze(id, env.config.pointFreezeMillis - (System.currentTimeMillis() - currTime));
        }
        freezed = false;
        int ignored = table.countCards(); // this part is just for demonstration in

    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long currTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - currTime <= env.config.penaltyFreezeMillis) {
            freezed = true;
            try {
                Thread.sleep(freezeUpdateTime);
            } catch (InterruptedException ignored) {
            }
            env.ui.setFreeze(id, env.config.penaltyFreezeMillis - (System.currentTimeMillis() - currTime));
        }
        freezed = false;
    }

    public int score() {
        return score;
    }

    public boolean isFull() {
        synchronized (q) {
            return (q.size() >= env.config.featureSize);
        }
    }
}
