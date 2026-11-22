package com.github.sbadolankaur.rpg.platform;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InstanceMac {
  private final SecretKeySpec key;

  public InstanceMac(@Value("${app.instance-key}") String encoded) {
    if (encoded.length() != 64 || !encoded.matches("[0-9a-fA-F]{64}")) {
      throw new IllegalArgumentException("APP_INSTANCE_KEY must contain 64 hex digits");
    }
    key = new SecretKeySpec(HexFormat.of().parseHex(encoded), "HmacSHA256");
  }

  public byte[] sign(String scope, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(key);
      mac.update(scope.getBytes(StandardCharsets.UTF_8));
      mac.update((byte) 0);
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is unavailable", e);
    }
  }
}
