package  com.deepu.create_crude.block;

import net.minecraft.world.level.block.Block;
import com.deepu.create_crude.CreateCrude;
import com.deepu.create_crude.block.entity.ReactorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ReactorBlock extends Block implements  EntityBlock {

    public ReactorBlock(Properties properties){
        super(properties);
    }
    @Nullable
    @Override 
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state){
        return new ReactorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ReactorBlockEntity reactorBE && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(reactorBE, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != CreateCrude.REACTOR_BE.get() || level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof ReactorBlockEntity reactor) {
                reactor.serverTick();
            }
        };
    }
}