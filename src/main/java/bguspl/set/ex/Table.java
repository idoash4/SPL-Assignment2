package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.logging.Level;
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

    protected final boolean[][] slotToPlayerToken;

    protected final int[] tokenPlayerCounter;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot, boolean[][] slotToPlayerToken, int[] tokenPlayerCounter) {
        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.slotToPlayerToken = slotToPlayerToken;
        this.tokenPlayerCounter = tokenPlayerCounter;
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {
        this(env, new Integer[env.config.tableSize],
                new Integer[env.config.deckSize],
                new boolean[env.config.tableSize][env.config.players],
                new int[env.config.players]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public synchronized void hints() {
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
    public synchronized int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    // Returns the number of empty slots in the board
    public synchronized int countEmptySlots() {
        return slotToCard.length - countCards();
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
            env.ui.placeCard(card, slot);
        }
    }

    // Place a card in the next empty slot and return the slot number
    public synchronized int placeCard(int card) {
        for (int i = 0; i < slotToCard.length; i++) {
            if (slotToCard[i] == null) {
                placeCard(card, i);
                return i;
            }
        }
        return -1;
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        synchronized (this) {
            int card = slotToCard[slot];
            slotToCard[slot] = null;
            cardToSlot[card] = null;
            removeTokens(slot);
            env.ui.removeCard(slot);
        }
    }

    public synchronized void removeCard(int[] cards) {
        for (int card : cards) {
            removeCard(cardToSlot[card]);
        }
    }

    // Remove all the cards from the board and return them.
    public synchronized List<Integer> removeAllCards() {
        List<Integer> cards = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        for (int i = 0; i < slotToCard.length; i++) {
            if (slotToCard[i] != null) {
                removeCard(i);
            }
        }
        return cards;
    }

    public synchronized int updatePlayerToken(int player, int slot) {
        if (!slotToPlayerToken[slot][player]) {
            placeToken(player, slot);

        } else {
            removeToken(player, slot);
        }
        return tokenPlayerCounter[player];
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public synchronized boolean placeToken(int player, int slot) {
        if (slotToPlayerToken[slot][player]) {
            return false;
        }
        slotToPlayerToken[slot][player] = true;
        tokenPlayerCounter[player]++;
        env.ui.placeToken(player, slot);
        return true;
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public synchronized boolean removeToken(int player, int slot) {
        if (!slotToPlayerToken[slot][player]) {
            return false;
        }
        slotToPlayerToken[slot][player] = false;
        tokenPlayerCounter[player]--;
        env.ui.removeToken(player, slot);
        return true;
    }

    public synchronized void removeTokens(int slot) {
        for (int i = 0; i < slotToPlayerToken[slot].length; i++) {
            removeToken(i, slot);
        }
    }

    public synchronized int[] getCardsWithTokens(int player) {
        int[] cards = new int[3];
        int index = 0;
        for (int i = 0; i < slotToPlayerToken.length && index < 3; i++) {
            if (slotToPlayerToken[i][player]) {
                env.logger.log(Level.INFO, "slot: " + i + " card: " + slotToCard[i]);
                cards[index] = slotToCard[i];
                index++;
            }
        }
        return cards;
    }
}
