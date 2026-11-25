package online.farmsteadminecraft.bombrun.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Strike Remote. All the aiming happens on the client ({@code client/remote/}); the item itself only refuses
 * to break blocks, so a left click is free to confirm the target.
 */
public class StrikeRemoteItem extends Item {
    public StrikeRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.farmstead_bombrun.strike_remote.tip.aim").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.farmstead_bombrun.strike_remote.tip.fire").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.farmstead_bombrun.strike_remote.tip.scroll").withStyle(ChatFormatting.DARK_GRAY));
    }
}
