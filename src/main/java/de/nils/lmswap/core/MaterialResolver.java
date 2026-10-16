package de.nils.lmswap.core;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import fi.dy.masa.litematica.materials.MaterialCache;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Which items a block state stands for, the same way Litematica's material list counts them.
 * Injected so the batch logic can be unit tested without Litematica's MaterialCache (which needs a client world).
 */
public interface MaterialResolver
{
    /**
     * @return the material list entry (item) this state belongs to, or null if it needs several items (potted plants)
     *         or none; waterlogged states count as their dry state (decision 1)
     */
    @Nullable
    Item sourceItem(BlockState state);

    /**
     * @return all items with counts needed for the state (e.g. 2 slabs for a double slab, a water bucket for a
     *         waterlogged block), for material deltas
     */
    List<ItemAmount> materials(BlockState state);

    /** An item with an amount (ItemStack can not be created without loaded game data, e.g. in unit tests) */
    record ItemAmount(Item item, int count)
    {
    }

    /** Litematica's material list logic */
    MaterialResolver LITEMATICA = new MaterialResolver()
    {
        @Override
        @Nullable
        public Item sourceItem(BlockState state)
        {
            MaterialCache cache = MaterialCache.getInstance();

            if (state.isAir() || cache.requiresMultipleItems(state))
            {
                return null;
            }

            ItemStack stack = cache.getRequiredBuildItemForState(dryState(state));
            return stack.isEmpty() ? null : stack.getItem();
        }

        @Override
        public List<ItemAmount> materials(BlockState state)
        {
            List<ItemAmount> list = new ArrayList<>();

            if (state.isAir())
            {
                return list;
            }

            MaterialCache cache = MaterialCache.getInstance();
            BlockState dry = dryState(state);

            if (dry != state)
            {
                list.add(new ItemAmount(Items.WATER_BUCKET, 1));
            }

            if (cache.requiresMultipleItems(dry))
            {
                cache.getItems(dry).stream().filter(s -> s.isEmpty() == false).forEach(s -> list.add(new ItemAmount(s.getItem(), s.getCount())));
            }
            else
            {
                ItemStack stack = cache.getRequiredBuildItemForState(dry);

                if (stack.isEmpty() == false)
                {
                    list.add(new ItemAmount(stack.getItem(), stack.getCount()));
                }
            }

            return list;
        }
    };

    static BlockState dryState(BlockState state)
    {
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))
        {
            return state.setValue(BlockStateProperties.WATERLOGGED, false);
        }

        return state;
    }
}
