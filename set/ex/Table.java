package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    // Information for the tokens - slotsToken[i][j] == ture iff player[j] has token on slot [i]
    protected boolean[][] slotsToken;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.slotsToken = new boolean[env.config.tableSize][env.config.players];
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() { //synchronized?
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public synchronized int countCards() { //synchronized
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        synchronized(this) {
        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        }
        env.ui.placeCard(card, slot);
        
        

    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        synchronized(this) {
            Integer card = slotToCard[slot];
            cardToSlot[card] = null;
            slotToCard[slot] = null;
            for(int i = 0 ; i<env.config.players ; i++) {
 
                slotsToken[slot][i] = false;
            }
        }
        env.ui.removeTokens(slot);
        env.ui.removeCard(slot);

    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {

        if (slotsToken[slot][player]) {
            removeToken(player, slot);

        }
        else {
            slotsToken[slot][player] = true;   
        }
        env.ui.placeToken(player, slot);
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        if (slotsToken[slot][player]) {
            slotsToken[slot][player] = false;
            env.ui.removeToken(player, slot);
            return true;
        }
        return false;

    }

    public synchronized int countTokens(int playerId) {
        int counter = 0;
        for (int i = 0 ; i < env.config.tableSize ; i++) {
            if ( slotsToken[i][playerId]) {
                counter++;
            }
        }
        return counter;
    }

    public synchronized int[] playersSlot(int playerId) {
        int[] slots = new int[3];
        int counter = 0;
        for (int i = 0 ; i < env.config.tableSize ; i++) {
            if (slotsToken[i][playerId]) {
                slots[counter] = i;
                counter++;
            }
        }
        return slots;
    }
}
