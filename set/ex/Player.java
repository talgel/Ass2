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
    
    // list holding the players slots
    public List<Integer> mySet;

    //int determining if player should be scored (1), penalized (2) or dismissed (0)
    public volatile int panishOrScore;

    //Pointer to the dealer object
    public Dealer dealer;

    // whether the player is freezed or not
    private boolean freezed;


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
        this.dealer = dealer;
        q = new LinkedList<Integer>();
        panishOrScore = 0;
        mySet = new ArrayList<Integer>();
        freezed = false;
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
            panishOrScore = 0;
            synchronized(q){
                while(q.isEmpty()){
                     try {
                    q.wait();
                    }
                    catch(InterruptedException ignored) {}
                }
            
                Integer nextSlot = q.remove();
            
                if(table.removeToken(id, nextSlot)) {
                    mySet.remove((Integer) nextSlot);
                }
                else {
                    if(mySet.size()<3){
                        table.placeToken(id, nextSlot);
                        mySet.add(nextSlot);
                    }
                }
                q.notifyAll();
            }
                if(mySet.size() == 3) {
                synchronized(dealer.claimedPlayer){
                    dealer.claimedPlayer.add(this);
                    dealer.claimedPlayer.notifyAll();
                
                    while(dealer.claimedPlayer.contains(this)) {
                        try{
                            dealer.claimedPlayer.wait();
                        }
                        catch(InterruptedException e) {}
                    }
                }
                    if(panishOrScore == 1) {
                        this.point();
                        
                    }
                    else if (panishOrScore == 2) {
                        this.penalty();
                        
                    }
                    //if not point nor penalty clear set?
                }
                
                //place or remove token
                //remove from q
                //if 3 tokens placed - point or penalty
                //clear slotsToken
            
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
                synchronized(q){
                while(isFull()) {
                    try{
                        q.wait();
                      }
                    catch (InterruptedException ignored) {}
                }
            }
                int randomSlot = (int) (Math.random()*env.config.tableSize);
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
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        synchronized(q){
        if (!isFull() && !freezed) {
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
        int currScore = this.score;
        env.ui.setScore(id,currScore + 1);
        this.score = currScore + 1 ;
        long currTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - currTime  <= env.config.pointFreezeMillis) {
            freezed = true;
            try{
                Thread.sleep(100);
              } catch (InterruptedException ignored) {}
              env.ui.setFreeze(id, env.config.pointFreezeMillis - System.currentTimeMillis() - currTime);
        }
        freezed = false;
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests

    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long currTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - currTime  <= env.config.penaltyFreezeMillis) {
            freezed = true;
            try{
                Thread.sleep(100);
              } catch (InterruptedException ignored) {}
              env.ui.setFreeze(id, env.config.penaltyFreezeMillis - (System.currentTimeMillis() - currTime));
        }
        freezed = false;
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
