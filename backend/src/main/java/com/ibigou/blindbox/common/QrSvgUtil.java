package com.ibigou.blindbox.common;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** 二维码 → SVG 字符串（零外部服务，纯 zxing 矩阵输出） */
public final class QrSvgUtil {

    private QrSvgUtil() {}

    public static String toSvg(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            int w = matrix.getWidth(), h = matrix.getHeight();
            StringBuilder sb = new StringBuilder();
            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(w)
              .append("\" height=\"").append(h).append("\" viewBox=\"0 0 ").append(w).append(' ').append(h).append("\">")
              .append("<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>")
              .append("<path fill=\"#000\" d=\"");
            // 每行合并连续黑点为一个横线段，显著减小体积
            for (int y = 0; y < h; y++) {
                int x = 0;
                while (x < w) {
                    if (!matrix.get(x, y)) { x++; continue; }
                    int x2 = x;
                    while (x2 < w && matrix.get(x2, y)) { x2++; }
                    sb.append('M').append(x).append(' ').append(y).append('h').append(x2 - x);
                    x = x2;
                }
            }
            return sb.append("\"/>").append("</svg>").toString();
        } catch (Exception e) {
            throw new RuntimeException("二维码生成失败: " + e.getMessage(), e);
        }
    }
}
