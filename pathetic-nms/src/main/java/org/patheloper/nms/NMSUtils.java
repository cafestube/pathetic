package org.patheloper.nms;

import org.patheloper.api.snapshot.NMSInterface;
import org.patheloper.nms.v1_20_R4.OneTwentyFourNMSInterface;

public class NMSUtils {

  private final NMSInterface nmsInterface;

  public NMSUtils(int major, int minor) {
    switch (major) {
      case 20:
        if (minor == 5 || minor == 6) {
          nmsInterface = new OneTwentyFourNMSInterface();
          break;
        }
        throw new IllegalArgumentException("Unsupported version: " + major + "." + minor);
      default:
        throw new IllegalArgumentException("Unsupported version: " + major + "." + minor);
    }
  }

  public NMSInterface getNmsInterface() {
    return this.nmsInterface;
  }
}
