package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DealerTest {

    Dealer dealer;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Player player1;
    @Mock
    private Player player2;
    @Mock
    private Logger logger;

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put("HumanPlayers", 1);
        properties.put("ComputerPlayers", 0);
        Config config = new Config(logger, properties);
        Env env = new Env(logger, config, ui, util);
        Player[] players = new Player[2];
        players[0] = player1;
        players[1] = player2;
        when(player1.getId()).thenReturn(0);
        when(player2.getId()).thenReturn(1);
        dealer = new Dealer(env, table, players);
    }

    @Test
    void shouldFinish_WithSets() {
        // findSets will return a list with one item
        when(util.findSets(any(), eq(1))).thenReturn(Collections.singletonList(new int[0]));
        assertFalse(dealer.shouldFinish());
    }

    @Test
    void shouldFinish_NoSets() {
        // findSets will return an empty list
        when(util.findSets(any(), eq(1))).thenReturn(new ArrayList<>(0));
        assertTrue(dealer.shouldFinish());
    }

    @Test
    void announceWinner() {
        when(player1.score()).thenReturn(1);
        when(player2.score()).thenReturn(2);
        dealer.announceWinners();
        int[] shouldWin = new int[1];
        shouldWin[0] = 1;
        verify(ui).announceWinner(eq(shouldWin));
    }
}
