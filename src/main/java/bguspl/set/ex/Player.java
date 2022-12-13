package bguspl.set.ex;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

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
     * Dealer object
     */
    private final Dealer dealer;

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
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * Incoming Actions queue
     */
    private BlockingQueue<Integer> incomingActions;

    /**
     *
     */
    private final Integer[] tokenToSlot;

    /**
     *
     */
    private final Integer[] slotToToken;

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
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.incomingActions = new ArrayBlockingQueue<>(3);
        this.tokenToSlot = new Integer[3];
        this.slotToToken = new Integer[env.config.tableSize];
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            try {
                int slot = incomingActions.take();
                env.logger.log(Level.INFO, "Processing key for player on slot: " + slot);
                
                if (slotToToken[slot] == null) {
                    placeToken(slot);
                } else {
                    removeToken(slot);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                try {
                    incomingActions.put(ThreadLocalRandom.current().nextInt(0, 12));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if (!incomingActions.offer(slot)) {
            env.logger.log(Level.WARNING, "Failed to add key press to queue");
        }
    }

    private void placeToken(int slot) {
        for (int i = 0; i < tokenToSlot.length; i++) {
            if (tokenToSlot[i] == null) {
                tokenToSlot[i] = slot;
                slotToToken[slot] = i;
                table.placeToken(id, slot);
            }
        }
        env.logger.log(Level.WARNING, "A player attempted to place a 4th token");
    }

    private void removeToken(int slot) {
        int token = slotToToken[slot];

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        incomingActions.clear();
    }

    public int getScore() {
        return score;
    }
}
