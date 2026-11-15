package fr.cobblepulse.battlepass.net;

import fr.cobblepulse.battlepass.BattlePass;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Deux paquets generiques (JSON) entre l'ecran du passe / l'editeur et le serveur. */
public final class Payloads {
    private static final StreamCodec<ByteBuf, String> BIG_STRING = ByteBufCodecs.stringUtf8(1 << 22);

    private Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> makeType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BattlePass.MOD_ID, path));
    }

    /** Client -> serveur : action (open, claim, hunt_start, admin_save...) et ses arguments JSON. */
    public record Action(String action, String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE = makeType("action");
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Action::action, BIG_STRING, Action::json, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Serveur -> client : donnees (pass, admin, candidates, toast, open_admin...). */
    public record Data(String kind, String json) implements CustomPacketPayload {
        public static final Type<Data> TYPE = makeType("data");
        public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Data::kind, BIG_STRING, Data::json, Data::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
