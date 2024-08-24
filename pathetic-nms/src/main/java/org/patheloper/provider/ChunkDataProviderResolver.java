package org.patheloper.provider;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.patheloper.api.snapshot.ChunkDataProvider;
import org.patheloper.provider.paper.PaperChunkDataProvider;

@Getter
@Slf4j
public class ChunkDataProviderResolver {

  private final ChunkDataProvider chunkDataProvider;

  public ChunkDataProviderResolver(int major, int minor) {
    String version = "1." + major + "." + minor;

    if (isPaper()) {
      chunkDataProvider = new PaperChunkDataProvider();
    } else {
      throw new IllegalStateException("Unsupported server version: " + version);
    }

    log.info("Detected version v{}, using {}", version, chunkDataProvider.getClass().getSimpleName());
  }

  private boolean isPaper() {
    try {
      Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
