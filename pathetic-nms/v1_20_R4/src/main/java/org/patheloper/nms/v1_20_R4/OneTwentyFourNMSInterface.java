package org.patheloper.nms.v1_20_R4;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.patheloper.api.snapshot.NMSInterface;

import java.lang.reflect.Field;

public class OneTwentyFourNMSInterface implements NMSInterface {

  private static final Field blockIDField;

  static {
    try {
      blockIDField = CraftChunk.class.getDeclaredField("emptyBlockIDs");
      blockIDField.setAccessible(true);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public ChunkSnapshot getSnapshot(World world, int chunkX, int chunkZ) {
    try {

      ServerLevel server = ((CraftWorld) world).getHandle();
      CraftChunk newCraftChunk = new CraftChunk(server, chunkX, chunkZ);

      server.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
      PalettedContainer<BlockState> dataDataPaletteBlock = (PalettedContainer<BlockState>) blockIDField.get(newCraftChunk);

      dataDataPaletteBlock.release();
      dataDataPaletteBlock.acquire();
      ChunkSnapshot chunkSnapshot = newCraftChunk.getChunkSnapshot();
      dataDataPaletteBlock.release();

      return chunkSnapshot;
    } catch (IllegalAccessException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public BlockState getBlockState(ChunkSnapshot snapshot, int x, int y, int z) {
    return snapshot.getBlockData(x, y, z).createBlockState();
  }
}
