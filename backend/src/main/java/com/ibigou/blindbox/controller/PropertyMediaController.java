package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业主端媒体上传（/api/property/media）。
 *
 * 用途：报修带图/视频、随手拍违停、业主群发言附图。
 * 存储：app.upload-dir/property/{ownerId}/{kind}-{ts}.{ext}，经 /uploads/** 静态映射访问。
 * 返回 { url, type: image|video, name, size }，前端把 url 塞进 media 的 JSON 数组即可。
 */
@Slf4j
@RestController
@RequestMapping("/api/property/media")
@RequiredArgsConstructor
public class PropertyMediaController {

    private static final String[] IMG_EXT = {".jpg", ".jpeg", ".png", ".webp", ".gif"};
    private static final String[] VIDEO_EXT = {".mp4", ".mov", ".m4v", ".webm"};
    private static final long MAX_IMG = 10L * 1024 * 1024;
    private static final long MAX_VIDEO = 40L * 1024 * 1024;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestHeader("X-Owner-Token") String token,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false, defaultValue = "misc") String kind) {
        Long ownerId = PropertyOwnerController.TOKEN_STORE.get(token == null ? "" : token);
        if (ownerId == null) throw new BizException("登录已过期");
        if (file == null || file.isEmpty()) throw new BizException("请选择要上传的文件");

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot) : "";
        boolean isImg = contains(IMG_EXT, ext);
        boolean isVideo = contains(VIDEO_EXT, ext);
        if (!isImg && !isVideo) throw new BizException("仅支持图片 jpg/png/webp 或视频 mp4/mov");
        long limit = isVideo ? MAX_VIDEO : MAX_IMG;
        if (file.getSize() > limit) {
            throw new BizException((isVideo ? "视频" : "图片") + "不能超过 " + (limit / 1024 / 1024) + "MB");
        }
        String safeKind = kind.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safeKind.isEmpty()) safeKind = "misc";

        try {
            Path dir = Paths.get(uploadDir, "property", String.valueOf(ownerId)).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = safeKind + "-" + System.currentTimeMillis() + ext;
            Path target = dir.resolve(filename);
            file.transferTo(target);

            String url = "/uploads/property/" + ownerId + "/" + filename;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("url", url);
            m.put("type", isVideo ? "video" : "image");
            m.put("name", file.getOriginalFilename());
            m.put("size", file.getSize());
            log.info("property media uploaded: owner={} url={} size={}", ownerId, url, file.getSize());
            return Result.ok(m);
        } catch (IOException e) {
            throw new BizException("上传失败：" + e.getMessage());
        }
    }

    private static boolean contains(String[] arr, String v) {
        for (String a : arr) if (a.equals(v)) return true;
        return false;
    }
}
