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
import java.util.UUID;
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

    static void ensureGroup(String name, String display, int weight, String permission) {
        if (name == null || name.isBlank()) return;
        LuckPerms lp = LuckPermsProvider.get();
        String g = name.trim().toLowerCase(Locale.ROOT);
        lp.getGroupManager().createAndLoadGroup(g).thenAccept(group -> {
            group.data().clear(n -> NodeType.DISPLAY_NAME.matches(n) || NodeType.WEIGHT.matches(n));
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

    static void setPassGroup(UUID uuid, String group, long expiryMs, String free, String premium) {
        LuckPerms lp = LuckPermsProvider.get();
        lp.getUserManager().modifyUser(uuid, user -> {
            user.data().clear(n -> isPassGroup(n, free, premium));
            if (group != null && !group.isBlank() && expiryMs > System.currentTimeMillis()) {
                long seconds = Math.max(1, (expiryMs - System.currentTimeMillis()) / 1000L);
                user.data().add(InheritanceNode.builder(group.trim().toLowerCase(Locale.ROOT)).expiry(seconds, TimeUnit.SECONDS).build());
            }
        });
    }

    static String describe(UUID uuid) {
        LuckPerms lp = LuckPermsProvider.get();
        User u = lp.getUserManager().loadUser(uuid).join();
        String groups = u.getInheritedGroups(QueryOptions.nonContextual()).stream().map(x -> x.getName()).collect(Collectors.joining(","));
        String temp = u.getNodes(NodeType.INHERITANCE).stream().filter(Node::hasExpiry)
                .map(n -> n.getGroupName() + "@" + n.getExpiry().getEpochSecond()).collect(Collectors.joining(","));
        return groups + " | temporaires : " + temp;
    }
}
