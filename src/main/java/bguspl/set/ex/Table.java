package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    protected final int MAX_PLAYER_TOKENS = 3;

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

    // Place a card in a random empty slot and return the slot number
    public synchronized int placeCard(int card) {
        List<Integer> emptySlots = new ArrayList<Integer>(slotToCard.length);
        for (int i = 0; i < slotToCard.length; i++) {
            if (slotToCard[i] == null) {
                emptySlots.add(i);
            }
        }
        if (emptySlots.isEmpty()) {
            // There are no empty slots
            return -1;
        }
        Random random = new Random();
        int slot = emptySlots.get(random.nextInt(emptySlots.size()));
        placeCard(card, slot);
        return slot;
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

    public void removeCardById(int card) {
        removeCard(cardToSlot[card]);
    }

    // Remove all the cards from the board and return them.
    public synchronized void removeAllCards() {
        List<Integer> randomOrderSlots = IntStream.range(0,slotToCard.length).boxed().collect(Collectors.toList());
        Collections.shuffle(randomOrderSlots);
        for (int slot : randomOrderSlots) {
            if (slotToCard[slot] != null) {
                removeCard(slot);
            }
        }
    }

    public synchronized boolean updatePlayerToken(int player, int slot) {
        if (slotToCard[slot] == null) {
            return false; // Slot is empty
        }
        if (!slotToPlayerToken[slot][player]) {
            return placeToken(player, slot);
        } else {
            return removeToken(player, slot);
        }
    }

    public synchronized int getTokenCounter(int player) {
        return tokenPlayerCounter[player];
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     * @return       - true if a token was successfully placed.
     */
    public synchronized boolean placeToken(int player, int slot) {
        if (slotToPlayerToken[slot][player] || tokenPlayerCounter[player] == MAX_PLAYER_TOKENS) {
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
     * @return       - true if a token was successfully removed.
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

    public synchronized List<Integer> getCardsWithTokens(int player) {
        List<Integer> cards = new ArrayList<Integer>(MAX_PLAYER_TOKENS);
        for (int i = 0; i < slotToPlayerToken.length; i++) {
            if (slotToPlayerToken[i][player]) {
                env.logger.log(Level.INFO, "slot: " + i + " card: " + slotToCard[i]);
                cards.add(slotToCard[i]);
            }
        }
        return cards;
    }
}
