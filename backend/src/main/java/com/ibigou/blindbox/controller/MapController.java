package com.ibigou.blindbox.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * 地图静态图代理（高德地图）
 * 避免前端暴露API key
 */
@RestController
@RequestMapping("/api/map")
public class MapController {

    @Value("${amap.key:}")
    private String amapKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 获取静态地图图片
     * GET /api/map/static?lat=31.95&lng=118.82&zoom=15&size=400*200
     */
    @GetMapping("/static")
    public ResponseEntity<byte[]> getStaticMap(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "15") int zoom,
            @RequestParam(defaultValue = "400*200") String size) {

        // 没有配置key时返回占位图（1x1透明PNG）
        if (amapKey == null || amapKey.isEmpty()) {
            byte[] placeholder = new byte[]{
                (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,
                0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,
                0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,
                0x08,0x06,0x00,0x00,0x00,0x1F,0x15,(byte)0xC4,
                (byte)0x89,0x00,0x00,0x00,0x0A,0x49,0x44,0x41,
                0x54,0x78,(byte)0x9C,0x63,0x00,0x01,0x00,0x00,
                0x05,0x00,0x01,0x0D,0x0A,0x2D,(byte)0xB4,0x00,
                0x00,0x00,0x00,0x49,0x45,0x4E,0x44,(byte)0xAE,
                0x42,0x60,(byte)0x82
            };
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.set("X-Map-Status", "no-key");
            return new ResponseEntity<>(placeholder, headers, HttpStatus.OK);
        }

        try {
            String url = String.format(
                "https://restapi.amap.com/v3/staticmap?location=%.6f,%.6f&zoom=%d&size=%s&markers=mid,,A:%.6f,%.6f&key=%s",
                lng, lat, zoom, size, lng, lat, amapKey);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, byte[].class);

            HttpHeaders respHeaders = new HttpHeaders();
            respHeaders.setContentType(MediaType.IMAGE_PNG);
            respHeaders.setCacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(3600)));
            return new ResponseEntity<>(response.getBody(), respHeaders, HttpStatus.OK);

        } catch (Exception e) {
            // 高德API失败时返回占位图
            byte[] placeholder = new byte[]{
                (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,
                0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,
                0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,
                0x08,0x06,0x00,0x00,0x00,0x1F,0x15,(byte)0xC4,
                (byte)0x89,0x00,0x00,0x00,0x0A,0x49,0x44,0x41,
                0x54,0x78,(byte)0x9C,0x63,0x00,0x01,0x00,0x00,
                0x05,0x00,0x01,0x0D,0x0A,0x2D,(byte)0xB4,0x00,
                0x00,0x00,0x00,0x49,0x45,0x4E,0x44,(byte)0xAE,
                0x42,0x60,(byte)0x82
            };
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.set("X-Map-Status", "error:" + e.getMessage());
            return new ResponseEntity<>(placeholder, headers, HttpStatus.OK);
        }
    }
}
