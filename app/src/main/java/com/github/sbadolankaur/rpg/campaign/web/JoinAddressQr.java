package com.github.sbadolankaur.rpg.campaign.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;

record JoinAddressQr(String viewBox, String path) {
  static JoinAddressQr encode(String address) {
    try {
      var matrix = new QRCodeWriter().encode(address, BarcodeFormat.QR_CODE, 0, 0);
      StringBuilder path = new StringBuilder();
      for (int y = 0; y < matrix.getHeight(); y++) {
        for (int x = 0; x < matrix.getWidth(); x++) {
          if (matrix.get(x, y)) {
            path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
          }
        }
      }
      return new JoinAddressQr(
          "0 0 " + matrix.getWidth() + " " + matrix.getHeight(), path.toString());
    } catch (WriterException e) {
      throw new IllegalStateException("Unable to encode the join address", e);
    }
  }
}
