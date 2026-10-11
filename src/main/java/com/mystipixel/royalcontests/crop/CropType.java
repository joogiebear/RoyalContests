package com.mystipixel.royalcontests.crop;

import org.bukkit.Material;

import java.util.Set;

/**
 * One contest crop, as defined in {@code crops.yml}.
 *
 * @param id          the key in crops.yml, used in events, rewards ({@code %crop%}) and stats
 * @param blocks      the blocks that are this crop when broken
 * @param drops       the items that count in {@code drops} scoring
 * @param collection  the id substituted for {@code %collection%} in {@code placeholder} scoring
 * @param points      points per block in {@code blocks} scoring
 * @param requireRipe only fully grown blocks count (for anything that has an age)
 * @param naturalOnly blocks a player placed never count
 * @param stacked     breaking one also breaks the same crop stacked above it (sugar cane, cactus),
 *                    and those count too
 */
public record CropType(String id, String displayName, Material icon, Set<Material> blocks,
                       Set<Material> drops, String collection, int points, boolean requireRipe,
                       boolean naturalOnly, boolean stacked) {
}
