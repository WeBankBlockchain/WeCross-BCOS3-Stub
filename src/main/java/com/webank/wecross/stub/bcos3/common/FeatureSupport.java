package com.webank.wecross.stub.bcos3.common;

import org.fisco.bcos.sdk.v3.model.EnumNodeVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureSupport {

  private static final Logger logger = LoggerFactory.getLogger(FeatureSupport.class);

  /**
   * BCOS 3.2+ support get transaction/receipt proof
   *
   * @param version
   * @return
   */
  public static boolean isSupportGetTxProof(String version) {

    if (version == null || "".equals(version)) { // default
      return true;
    }

    try {
      EnumNodeVersion.Version v = EnumNodeVersion.getClassVersion(version);
      return isSupportGetTxProof(v);
    } catch (IllegalStateException e) {
      logger.info("version: {}, e: ", version, e);
      return true;
    }
  }

  /**
   * BCOS 3.2+ support get transaction/receipt proof
   *
   * @param version
   * @return
   */
  public static boolean isSupportGetTxProof(EnumNodeVersion.Version version) {
    return version.getMajor() == 3 && version.getMinor() >= 2;
  }
}
