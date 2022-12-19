package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlayerTest {

    Player player;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;

    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.score() >= 0);
    }

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put("PenaltyFreezeSeconds", "3");
        Config config = new Config(logger, properties);
        Env env = new Env(logger, config, ui, util);
        player = new Player(env, dealer, table, 0, false);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    @Test
    void point() {
        // calculate the expected score for later
        int expectedScore = player.score() + 1;

        // call the method we are testing
        player.point();

        // check that the score was increased correctly
        assertEquals(expectedScore, player.score());

        // check that ui.setScore was called with the player's id and the correct score
        verify(ui).setScore(eq(player.id), eq(expectedScore));
    }

    @Test
    void penalty() {
        // call the method we are testing
        player.penalty();

        // Check that the player was frozen
        assertTrue(player.isFrozen());
    }

    @Test
    void updateFreezeTime() {
        // Penalize the player
        player.penalty();

        // call the method we are testing
        player.updateFreezeTime();

        // Check that the display was updated to a value larger than 0 at least once
        verify(ui, Mockito.atLeastOnce()).setFreeze(eq(player.id), AdditionalMatchers.gt(0L));

        // Check that the display was set back to 0 when the freeze time is over
        verify(ui, Mockito.times(1)).setFreeze(eq(player.id), eq(0L));

        // Check that at the end the player was unfrozen
        assertFalse(player.isFrozen());
    }
}