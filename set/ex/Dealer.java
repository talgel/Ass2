package bguspl.set.ex;
import bguspl.set.Env;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.Collections;
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
    public volatile Queue<Player> claimedPlayer;

    //The Dealers thread
    public Thread dealerThread;

    //Last time timer updated
    private long timeUpdated = System.currentTimeMillis();

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    //private long reshuffleTime = 60000;
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;

        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        // new
        claimedPlayer = new LinkedList<Player>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for(Player player : players) {
            Thread t = new Thread(player);
            t.start();
        }
        
        while (!shouldFinish()) {
            shuffleDeck();
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        for(Player p : players) 
            p.terminate();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
            env.ui.setCountdown(env.config.turnTimeoutMillis,false);
            timeUpdated = System.currentTimeMillis();
        while (!terminate && System.currentTimeMillis()- timeUpdated < env.config.turnTimeoutMillis) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
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
    private void removeCardsFromTable() {
        
        while(claimedPlayer.size() > 0) {
            Player nextPlayer = claimedPlayer.remove();
            synchronized(nextPlayer) {
            if(nextPlayer.mySet.size() == 3) {
                int[] currentSet = new int[3];
                for (int i = 0 ; i < 3 ;i++) {
                    currentSet[i] = table.slotToCard[nextPlayer.mySet.get(i)];
                }
                boolean isSet = env.util.testSet(currentSet);
                if(isSet){ 
                    while(!nextPlayer.mySet.isEmpty()) {
                        Integer slot = nextPlayer.mySet.get(0);
                        smartRemove(slot);
                    }
                    env.ui.setCountdown(env.config.turnTimeoutMillis,false);
                    timeUpdated = System.currentTimeMillis();
                    nextPlayer.panishOrScore = 1;
                    //point player
                }
                else {
                    nextPlayer.panishOrScore = 2;
                    //panish player
                }
            }
        }
        synchronized(claimedPlayer){
            claimedPlayer.notifyAll();
        }
        }
    
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
    synchronized(table){
        for (int i = 0 ; i < env.config.tableSize ; i ++) {
            if(table.slotToCard[i] == null && deck.size() > 0) {
                table.placeCard(deck.remove(0), i);
            }
        }
    }
    
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized(claimedPlayer){
        try {
            claimedPlayer.wait(100);
        }
        catch(InterruptedException e) {}
    }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long timeElapsed = System.currentTimeMillis() - timeUpdated;
        long timer = env.config.turnTimeoutMillis-timeElapsed;
        if(timer < env.config.turnTimeoutWarningMillis ) {
            if (timer < 0) {
                env.ui.setCountdown(0, true);
            }
            else{env.ui.setCountdown(timer, true);}
            
        }
        else {
            env.ui.setCountdown(timer, reset);
        }
        
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized(table){
        for(int i = 0 ; i<env.config.tableSize ; i ++) {
            if (table.slotToCard[i]!=null) {
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
        List<Player> winningPlayers=new LinkedList<Player>();
 
        for(Player player : players )
        {
        if (winningPlayers.isEmpty()){
                winningPlayers.add(player);
            }
        else{
            if(player.score()==winningPlayers.get(0).score())
                winningPlayers.add(player);
            else if (player.score()>winningPlayers.get(0).score()) 
            {
                winningPlayers.clear();
                winningPlayers.add(player);
            }
        }
        }
        int[] winnerArrey = new int[winningPlayers.size()];
        for(int i=0 ; i<winningPlayers.size();i++)
        {
            winnerArrey[i]=winningPlayers.get(i).id;
        } 
        env.ui.announceWinner(winnerArrey);
    }


    public void smartRemove(int slot) {
    for(int i = 0 ; i < players.length ; i++) {
        synchronized(table){
            if (table.slotsToken[slot][i]) {
                synchronized(players[i]){
                    players[i].mySet.remove((Integer) slot);
                }
            }
        }
    }
    table.removeCard(slot);
    }

    public void shuffleDeck() {
        List<Integer> newDeck = new LinkedList<Integer>();
        while(!deck.isEmpty()) {
            int size = deck.size();
            newDeck.add(deck.remove((int) (Math.random()*size)));
        }
        while(!newDeck.isEmpty()) {
            deck.add(newDeck.remove(0));
        }
        
    }
}
