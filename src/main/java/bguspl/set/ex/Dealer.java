package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
     * The list of card ids that are on the table.
     */
    private final List<Integer> tableCards;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    protected Thread dealerThread;

    protected final BlockingQueue<Integer> setChecks;

    private volatile boolean reshuffleState;

    private static final int SLEEP_TIME_MS = 100;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        tableCards = new ArrayList<>(env.config.tableSize);
        setChecks = new ArrayBlockingQueue<>(1, true);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        reshuffleState = true;
        createAndRunPlayerThreads();
        while (!shouldFinish()) {
            placeCardsOnTable();
            reshuffleState = false;
            timerLoop();
            reshuffleState = true;
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        terminatePlayers();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     *
     */
    private void createAndRunPlayerThreads() {
        for (Player player : players) {
            Thread playerThread = new Thread(player, "player " + player.getId());
            playerThread.start();
        }
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        boolean cardsRemoved = true;
        while (!terminate && (env.config.turnTimeoutMillis <= 0 || System.currentTimeMillis() < reshuffleTime)) {
            // if cards were removed make sure there are still sets available in the game/on the table
            if (cardsRemoved && (
                    (env.config.turnTimeoutMillis > 0 && env.util.findSets(Stream.concat(deck.stream(),
                            tableCards.stream()).collect(Collectors.toList()), 1).size() == 0)
                            || (env.config.turnTimeoutMillis <=0 && env.util.findSets(tableCards, 1).size() == 0))) {
                break;
            }
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            cardsRemoved = removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
    }

    private void terminatePlayers() {
        for (int i = players.length - 1; i >= 0 ; i--) {
            Player player = players[i];
            env.logger.log(Level.INFO, "Dealer calling terminate on player " + player.getId());
            player.terminate();
            try {
                env.logger.log(Level.INFO, "Dealer waiting for player " + player.getId() + " thread to terminate");
                player.playerThread.join();
            } catch (InterruptedException exception) {
                env.logger.log(Level.WARNING, "Dealer thread was interrupted while waiting for player " +player.getId() + " threads to terminate");
            }
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    protected boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private boolean removeCardsFromTable() {
        Integer playerId = setChecks.peek();
        boolean cardsRemoved = false;
        if (playerId != null) {
            env.logger.log(Level.INFO, "Waiting for player lock to check for set of player " +playerId);
            synchronized (players[playerId]) {
                env.logger.log(Level.INFO, "Checking player " + playerId + " set");
                List<Integer> cards = table.getCardsWithTokens(playerId);
                if (cards.size() == table.MAX_PLAYER_TOKENS) {
                    if (env.util.testSet(cards.stream().mapToInt(i -> i).toArray())) {
                        env.logger.log(Level.INFO, "Set of player " + playerId + " is valid");
                        for (Integer card : cards) {
                            table.removeCardById(card);
                            tableCards.remove(card);
                            cardsRemoved = true;
                        }
                        players[playerId].point();
                    } else {
                        env.logger.log(Level.INFO, "Set of player " + playerId + " is invalid");
                        players[playerId].penalty();
                    }
                } else {
                    env.logger.log(Level.WARNING, "Player " + playerId + " asked for a set check while not having 3 cards");
                }
                env.logger.log(Level.INFO, "Clearing set checks queue and notifying player " + playerId);
                setChecks.clear();
                players[playerId].notifyAll();
            }
        }
        return cardsRemoved;
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        if (deck.size() == 0)
            return; // deck is empty no cards to place
        int empty_slots = table.countEmptySlots();
        if (empty_slots > 0) {
            Collections.shuffle(deck);
            for (int i = 0; i < empty_slots && deck.size() >= 1; i++) {
                // Attempt to place the card from the top of the deck on the board
                int card = deck.get(0);
                if (table.placeCard(card) != -1) {
                    // If the card was placed on the board remove it from the deck
                    deck.remove(0);
                    tableCards.add(card);
                } else {
                    env.logger.log(Level.WARNING, "Dealer attempted to place a card on a full board");
                }
            }
            updateTimerDisplay(true);
            if (env.config.hints) {
                System.out.println("Dealer reshuffled");
                table.hints();
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            Thread.sleep(SLEEP_TIME_MS);
        } catch (InterruptedException e) {
            env.logger.log(Level.INFO, "Dealer thread was interrupted");
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
        if (env.config.turnTimeoutMillis > 0) {
            long time_remaining = reshuffleTime - System.currentTimeMillis();
            env.ui.setCountdown(time_remaining > 0 ? time_remaining : 0, time_remaining < env.config.turnTimeoutWarningMillis);
        } else if (env.config.turnTimeoutMillis == 0) {
            env.ui.setElapsed(System.currentTimeMillis() - reshuffleTime);
        }
    }

    public boolean isReshuffling() {
        return reshuffleState;
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        table.removeAllCards();
        deck.addAll(tableCards);
        tableCards.clear();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    protected void announceWinners() {
        List<Integer> winners = new ArrayList<Integer>(env.config.players);
        int maxScore = 0;
        for (Player player : players) {
            int playerScore = player.score();
            if (playerScore > maxScore) {
                maxScore = playerScore;
                winners.clear();
                winners.add(player.getId());
            } else if (playerScore == maxScore) {
                winners.add(player.getId());
            }
        }
        env.ui.announceWinner(winners.stream().mapToInt(i -> i).toArray());
    }
}
