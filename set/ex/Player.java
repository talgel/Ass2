package bguspl.set.ex;
import java.util.Queue;
import java.util.LinkedList;
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
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
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
    
    // Array holding the players slots
    public Integer[] tokens;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */


    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        q = new LinkedList<Integer>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            synchronized(this){
                while(q.isEmpty()){
                     try {
                    q.wait();
                    }
                    catch(InterruptedException ignored) {}
                }
                Integer nextSlot = q.remove();
                table.placeToken(id, nextSlot);
                if(table.countTokens(id) == 3) {
                    //claimSet.add(id)
                    //claimSet.notifyAll()
                    //wait for answer from dealer

                    // int[] slots = table.playersSlot(id);
                    // boolean isSet = env.util.testSet(slots);
                    // if (!isSet) {
                    //     penalty();
                    // }
                    // else {
                        
                    // }
                }
                
                //place or remove token
                //remove from q
                //if 3 tokens placed - point or penalty
                //clear slotsToken
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */

    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                synchronized(this){
                while(isFull()) {
                    try{
                        q.wait();
                      }
                    catch (InterruptedException ignored) {}
                }
                int randomSlot = (int) Math.random()*env.config.tableSize;
                keyPressed(randomSlot);
                }
                // Original code
                // try {
                //     synchronized (this) { wait(); }
                // } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public synchronized void keyPressed(int slot) {
        // TODO implement
        if (!isFull()) {
            q.add(slot);
            q.notifyAll();
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        try{
            Thread.sleep(env.config.pointFreezeMillis);
          } catch (InterruptedException ignored) {}
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        synchronized(this) {
            env.ui.setScore(id,++score);
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        try{
        Thread.sleep(env.config.penaltyFreezeMillis);
             } catch (InterruptedException ignored) {}
    }

    public int score() {
        return score;
    }

    public synchronized boolean isFull() {
        return (q.size()>2);
    }

    public void startThread() {
        playerThread.start();
    }


}
