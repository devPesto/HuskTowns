package net.william278.husktowns.manager;

import net.william278.husktowns.HuskTowns;
import net.william278.husktowns.audit.Action;
import net.william278.husktowns.claim.*;
import net.william278.husktowns.map.ClaimMap;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class AdminManager {
    private final HuskTowns plugin;

    protected AdminManager(@NotNull HuskTowns plugin) {
        this.plugin = plugin;
    }

    public void createAdminClaim(@NotNull OnlineUser user, @NotNull World world, @NotNull Chunk chunk, boolean showMap) {
        final Optional<TownClaim> existingClaim = plugin.getClaimAt(chunk, world);
        if (existingClaim.isPresent()) {
            plugin.getLocales().getLocale("error_chunk_claimed_by")
                    .ifPresent(user::sendMessage);
            return;
        }

        final Optional<ClaimWorld> claimWorld = plugin.getClaimWorld(world);
        if (claimWorld.isEmpty()) {
            plugin.getLocales().getLocale("error_world_not_claimable")
                    .ifPresent(user::sendMessage);
            return;
        }

        plugin.runAsync(() -> {
            final TownClaim claim = new TownClaim(plugin.getAdminTown(), Claim.at(chunk));
            claimWorld.get().addAdminClaim(claim.claim());
            plugin.getDatabase().updateClaimWorld(claimWorld.get());
            plugin.getLocales().getLocale("admin_claim_created",
                            Integer.toString(chunk.getX()), Integer.toString(chunk.getZ()))
                    .ifPresent(user::sendMessage);
            plugin.highlightClaim(user, claim);
            if (showMap) {
                user.sendMessage(ClaimMap.builder(plugin)
                        .center(user.getChunk()).world(user.getWorld())
                        .build()
                        .toComponent(user));
            }
        });
    }

    public void deleteClaim(@NotNull OnlineUser user, @NotNull World world, @NotNull Chunk chunk, boolean showMap) {
        final Optional<TownClaim> existingClaim = plugin.getClaimAt(chunk, world);
        if (existingClaim.isEmpty()) {
            plugin.getLocales().getLocale("error_chunk_not_claimed")
                    .ifPresent(user::sendMessage);
            return;
        }

        final Optional<ClaimWorld> claimWorld = plugin.getClaimWorld(world);
        assert claimWorld.isPresent();

        plugin.runAsync(() -> {
            if (existingClaim.get().isAdminClaim(plugin)) {
                claimWorld.get().removeAdminClaim(chunk);
            } else {
                claimWorld.get().removeClaim(existingClaim.get().town(), chunk);
                existingClaim.get().town().setClaimCount(existingClaim.get().town().getClaimCount() - 1);
                plugin.getManager().updateTown(user, existingClaim.get().town());
            }
            plugin.getDatabase().updateClaimWorld(claimWorld.get());
            plugin.getLocales().getLocale("claim_deleted", Integer.toString(chunk.getX()),
                    Integer.toString(chunk.getZ())).ifPresent(user::sendMessage);
            if (showMap) {
                user.sendMessage(ClaimMap.builder(plugin)
                        .center(user.getChunk()).world(user.getWorld())
                        .build()
                        .toComponent(user));
            }
        });
    }

    public void deleteTown(@NotNull OnlineUser user, @NotNull String townName) {
        plugin.getTowns().stream()
                .filter(town -> town.getName().equalsIgnoreCase(townName))
                .findFirst()
                .ifPresentOrElse(town -> plugin.getManager().towns().deleteTownData(user, town),
                        () -> plugin.getLocales().getLocale("error_town_not_found", townName)
                                .ifPresent(user::sendMessage));
    }

    public void assumeTownOwnership(@NotNull OnlineUser user, @NotNull String townName) {
        plugin.getTowns().stream()
                .filter(town -> town.getName().equalsIgnoreCase(townName))
                .findFirst()
                .ifPresentOrElse(town -> {
                    final Optional<Member> existingMembership = plugin.getUserTown(user);
                    if (existingMembership.isPresent()) {
                        plugin.getLocales().getLocale("error_already_in_town")
                                .ifPresent(user::sendMessage);
                        return;
                    }

                    plugin.runAsync(() -> {
                        town.getMembers().put(town.getMayor(), plugin.getRoles().getDefaultRole().getWeight());
                        town.getMembers().put(user.getUuid(), plugin.getRoles().getMayorRole().getWeight());
                        town.getLog().log(Action.of(user, Action.Type.ADMIN_ASSUME_OWNERSHIP, user.getUsername()));
                        plugin.getManager().updateTown(user, town);

                        plugin.getLocales().getLocale("town_assumed_ownership", town.getName())
                                .ifPresent(user::sendMessage);
                    });
                }, () -> plugin.getLocales().getLocale("error_town_not_found", townName)
                        .ifPresent(user::sendMessage));
    }
}