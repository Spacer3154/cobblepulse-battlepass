package fr.cobblepulse.battlepass.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.DisplayNameNode;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.WeightNode;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Appels directs a l'API LuckPerms. Ne jamais referencer cette classe si LuckPerms est absent. */
final class LuckPermsHook {
    private LuckPermsHook() {}

    static Boolean check(ServerPlayer p, String node) {
        LuckPerms lp = LuckPermsProvider.get();
        User user = lp.getUserManager().getUser(p.getUUID());
        if (user == null) return null;
        Tristate t = user.getCachedData().getPermissionData().checkPermission(node);
        return t == Tristate.UNDEFINED ? null : t.asBoolean();
    }

    static Boolean exact(ServerPlayer p, String node) {
        LuckPerms lp = LuckPermsProvider.get();
        User user = lp.getUserManager().getUser(p.getUUID());
        if (user == null) return null;
        for (Node n : user.resolveInheritedNodes(QueryOptions.nonContextual())) {
            if (n.getKey().equalsIgnoreCase(node)) return n.getValue();
        }
        return false;
    }

    static void ensureGroup(String name, String display, int weight, String permission) {
        if (name == null || name.isBlank()) return;
        LuckPerms lp = LuckPermsProvider.get();
        String g = name.trim().toLowerCase(Locale.ROOT);
        lp.getGroupManager().createAndLoadGroup(g).thenAccept(group -> {
            // Le grade du passe ne doit jamais donner le premium lui-meme (sinon il survivrait a une nouvelle saison)
            group.data().clear(n -> NodeType.DISPLAY_NAME.matches(n) || NodeType.WEIGHT.matches(n)
                    || n.getKey().equalsIgnoreCase("cobblepulse.battlepass.premium"));
            group.data().add(DisplayNameNode.builder(display).build());
            group.data().add(WeightNode.builder(weight).build());
            if (permission != null) group.data().add(Node.builder(permission).build());
            lp.getGroupManager().saveGroup(group);
        });
    }

    private static boolean isPassGroup(Node n, String free, String premium) {
        if (!NodeType.INHERITANCE.matches(n)) return false;
        String g = NodeType.INHERITANCE.cast(n).getGroupName();
        return g.equalsIgnoreCase(free) || g.equalsIgnoreCase(premium);
    }

    /** Modifications serialisees par joueur : deux changements rapproches ne doivent pas s'entremeler. */
    private static final Map<UUID, CompletableFuture<Void>> CHAINS = new ConcurrentHashMap<>();

    static void setPassGroup(UUID uuid, String group, long expiryMs, String free, String premium) {
        LuckPerms lp = LuckPermsProvider.get();
        CompletableFuture<Void> next = CHAINS.compute(uuid, (k, prev) -> {
            CompletableFuture<Void> base = prev == null ? CompletableFuture.completedFuture(null) : prev.exceptionally(t -> null);
            return base.thenCompose(v -> lp.getUserManager().modifyUser(uuid, user -> {
                user.data().clear(n -> isPassGroup(n, free, premium));
                if (group != null && !group.isBlank() && expiryMs > System.currentTimeMillis()) {
                    long seconds = Math.max(1, (expiryMs - System.currentTimeMillis()) / 1000L);
                    user.data().add(InheritanceNode.builder(group.trim().toLowerCase(Locale.ROOT)).expiry(seconds, TimeUnit.SECONDS).build());
                }
            }));
        });
        next.whenComplete((v, t) -> CHAINS.remove(uuid, next));
    }

    /** Attend la fin des modifications en cours (tests). */
    static void await(UUID uuid) {
        CompletableFuture<Void> f = CHAINS.get(uuid);
        if (f != null) {
            try {
                f.get(10, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    static String describe(UUID uuid) {
        await(uuid);
        LuckPerms lp = LuckPermsProvider.get();
        User u = lp.getUserManager().loadUser(uuid).join();
        String groups = u.getInheritedGroups(QueryOptions.nonContextual()).stream().map(x -> x.getName()).collect(Collectors.joining(","));
        String temp = u.getNodes(NodeType.INHERITANCE).stream().filter(Node::hasExpiry)
                .map(n -> n.getGroupName() + "@" + n.getExpiry().getEpochSecond()).collect(Collectors.joining(","));
        return groups + " | temporaires : " + temp;
    }
}
