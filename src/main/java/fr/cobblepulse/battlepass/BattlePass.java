package fr.cobblepulse.battlepass;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BattlePass implements ModInitializer {
    public static final String MOD_ID = "cobblepulse_battlepass";
    public static final Logger LOG = LoggerFactory.getLogger("CobblePulse-BattlePass");

    @Override
    public void onInitialize() {
        LOG.info("[BattlePass] squelette");
    }
}
