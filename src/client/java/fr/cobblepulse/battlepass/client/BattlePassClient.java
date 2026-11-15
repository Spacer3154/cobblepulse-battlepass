package fr.cobblepulse.battlepass.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import fr.cobblepulse.battlepass.BattlePass;
import fr.cobblepulse.battlepass.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Cote client : ecran du passe, editeur graphique et touche d'ouverture. */
public final class BattlePassClient implements ClientModInitializer {
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblepulse_battlepass.open", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B, "key.categories.cobblepulse_battlepass"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) {
                if (mc.player != null && mc.screen == null) Ui.send("open");
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Data.TYPE, (payload, ctx) -> ctx.client().execute(() -> receive(payload.kind(), payload.json())));
        if ("true".equals(System.getProperty("cobblepulse_battlepass.clienttest"))) ClientSelfTest.install();
        BattlePass.LOG.info("[BattlePass] Client initialise");
    }

    static void receive(String kind, String json) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            BattlePass.LOG.warn("[BattlePass] Donnees illisibles ({}) : {}", kind, e.toString());
            return;
        }
        switch (kind) {
            case "pass_open" -> {
                Ui.clearCaches();
                mc.setScreen(new PassScreen(o));
            }
            case "pass", "pass_full" -> {
                if (mc.screen instanceof PassScreen s) s.update(o, kind.equals("pass_full"));
            }
            case "candidates" -> {
                if (mc.screen instanceof PassScreen s) s.showCandidates(o);
            }
            case "toast" -> {
                boolean ok = o.has("ok") && o.get("ok").getAsBoolean();
                String msg = o.has("message") ? o.get("message").getAsString() : "";
                if (mc.screen instanceof PassScreen s) s.toast(ok, msg);
                else if (mc.screen instanceof AdminScreen s) s.toast(ok, msg);
                else if (mc.player != null) mc.player.displayClientMessage(Component.literal(msg), true);
            }
            case "admin_open" -> mc.setScreen(new AdminScreen(o));
            case "admin_data" -> {
                if (mc.screen instanceof AdminScreen s) s.refreshData(o);
                else if (mc.screen instanceof EditScreen e && e.parent instanceof AdminScreen s) s.refreshData(o);
            }
            case "admin_result" -> {
                if (mc.screen instanceof AdminScreen s) s.onResult(o);
            }
            case "admin_player" -> {
                if (mc.screen instanceof AdminScreen s) s.onPlayer(o);
            }
            case "pokemon_check" -> {
                if (mc.screen instanceof RewardEditScreen s) s.onCheck(o);
            }
            default -> {
            }
        }
    }
}
