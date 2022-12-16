package bguspl.set.ex;

import java.util.concurrent.ArrayBlockingQueue;
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
    protected Thread playerThread;

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
    private final BlockingQueue<Integer> incomingActions;

    private static final int MAX_WAITING_ACTIONS = 3;

    private static final int SLEEP_TIME_MS = 100;

    private long freezeTime = Long.MIN_VALUE;

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
        this.incomingActions = new ArrayBlockingQueue<>(MAX_WAITING_ACTIONS);
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
            try {
                if (!isFrozen()) {
                    env.ui.setFreeze(id, 0);
                    int slot = incomingActions.take();
                    if (!dealer.isReshuffling()) {
                        env.logger.log(Level.INFO, "Processing key for player " + id + " on slot: " + slot);
                        if (table.updatePlayerToken(id, slot) && table.getTokenCounter(id) == table.MAX_PLAYER_TOKENS) {
                            requestSetCheck();
                        }
                    } else {
                        env.logger.log(Level.OFF, "Players " + id + " pressed a key while dealer is reshuffling");
                    }
                } else {
                    env.ui.setFreeze(id, freezeTime - System.currentTimeMillis() > 0 ? freezeTime - System.currentTimeMillis() + 999 : 1000);
                    incomingActions.clear();
                    Thread.sleep(SLEEP_TIME_MS);
                }
            } catch (InterruptedException e) {
                env.logger.log(Level.WARNING, "Player " + id + " thread was interrupted");
            }
        }
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
                try {
                    incomingActions.put(ThreadLocalRandom.current().nextInt(0, 12));
                } catch (InterruptedException e) {
                    env.logger.log(Level.WARNING, "AI thread of player " + id + " was interrupted");
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
        terminate = true;
        if (!human) {
            aiThread.interrupt();
            try { aiThread.join(); } catch (InterruptedException ignored) {}
        }
        playerThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (!incomingActions.offer(slot)) {
            env.logger.log(Level.WARNING, "Failed to add key press to queue");
        }
    }

    public void requestSetCheck() {
        try {
            synchronized (this) {
                env.logger.log(Level.INFO, "Player " + id + " waiting to add himself to set check queue");
                dealer.setChecks.put(id);
                env.logger.log(Level.INFO, "Player " + id + " added himself to set check queue");
                dealer.dealerThread.interrupt();
                while (dealer.setChecks.contains(id)) {
                    env.logger.log(Level.INFO, "Player " + id + " waiting for set check");
                    wait();
                }
                env.logger.log(Level.INFO, "Player " + id + " finished waiting for set check");
            }
        } catch (InterruptedException e) {
            env.logger.log(Level.WARNING, "Player " + id + " thread was interrupted while waiting for set check");
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
        setFreezeTime(System.currentTimeMillis() + env.config.pointFreezeMillis);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        setFreezeTime(System.currentTimeMillis() + env.config.penaltyFreezeMillis);
    }

    private synchronized void setFreezeTime(long time) {
        freezeTime = time;
    }

    public synchronized boolean isFrozen() {
        return freezeTime > System.currentTimeMillis();
    }

    public int getScore() {
        return score;
    }
}
