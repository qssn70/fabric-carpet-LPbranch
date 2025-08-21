package carpet.integrations;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;

// LuckPerms API（compileOnly）
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.model.user.User;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LuckPermsIntegration {
    private LuckPermsIntegration() {}

    public static void onFakePlayerSpawn(MinecraftServer server, ServerPlayerEntity player) {
        // 放到下一 tick，确保 player 完全注册进世界/玩家列表
        server.execute(() -> {
            LuckPerms lp = get();
            if (lp == null) return;

            // 1) 确保 User 已加载（重要：某些 LP 实现对未加载的用户会懒初始化慢一步）
            UUID uuid = player.getUuid();
            String name = player.getEntityName();
            CompletableFuture<User> loaded = lp.getUserManager().loadUser(uuid, name);
            loaded.thenAcceptAsync(user -> {
                // 2) 触发上下文构建/缓存填充
                ContextManager cm = lp.getContextManager();
                try {
                    // 取一次 QueryOptions，会促使 LP 在该玩家对象上建立/刷新缓存
                    QueryOptions qo = cm.getQueryOptions(player);
                    // 可选：如果返回为空/默认，可再延迟 1 tick 重试一次
                } catch (Throwable t) {
                    // 防御：任何 LP 版本差异/异常都不影响主流程
                }
            }, server); // 在主线程继续
        });
    }

    public static void onFakePlayerDespawn(MinecraftServer server, ServerPlayerEntity player) {
        server.execute(() -> {
            LuckPerms lp = get();
            if (lp == null) return;
            try {
                // 尝试让 LP 失效该玩家的上下文缓存（不同版本可能无显式 API，留空也可）
                lp.getContextManager().signalContextUpdate(player);
            } catch (Throwable ignored) {
            }
        });
    }

    // 运行期可选绑定：没有 LP 就返回 null
    private static LuckPerms get() {
        try {
            return LuckPermsProvider.get();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
