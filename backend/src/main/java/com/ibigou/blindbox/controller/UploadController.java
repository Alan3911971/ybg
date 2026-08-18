package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.repository.MerchantRepository;
import com.ibigou.blindbox.service.MerchantAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * 文件上传（V1.5 模式A：商家上传自家微信/支付宝收款码图片）。
 * 保存到 app.upload-dir 外部目录，通过 /uploads/** 静态映射访问。
 */
@RestController
@RequestMapping("/api/merchant/config")
@RequiredArgsConstructor
public class UploadController {

    private static final String[] ALLOWED_EXT = {".jpg", ".jpeg", ".png", ".webp"};

    private final MerchantAuthService authService;
    private final MerchantRepository merchantRepository;
    private final com.ibigou.blindbox.service.AuditLogService auditLogService;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    /** 上传收款码：type=wechat|alipay */
    @PostMapping("/upload-qr")
    public Result<String> uploadQr(@RequestHeader("X-Merchant-Token") String token,
                                   @RequestParam String type,
                                   @RequestParam("file") MultipartFile file) {
        if (!"wechat".equals(type) && !"alipay".equals(type)) {
            throw new BizException("type 只能为 wechat 或 alipay");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择收款码图片");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String ext = original.substring(original.lastIndexOf('.'));
        boolean allowed = false;
        for (String e : ALLOWED_EXT) {
            if (e.equals(ext)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new BizException("仅支持 jpg/png/webp 图片");
        }
        String merchantNo = authService.merchantNoByToken(token);
        try {
            Path dir = Paths.get(uploadDir, "merchant", merchantNo).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = type + "-" + System.currentTimeMillis() + ext;
            Path target = dir.resolve(filename);
            file.transferTo(target);
            String url = "/uploads/merchant/" + merchantNo + "/" + filename;
            Merchant m = merchantRepository.findById(merchantNo)
                    .orElseThrow(() -> new BizException("商家不存在"));
            if ("wechat".equals(type)) {
                m.setReceiveQrImgWechat(url);
            } else {
                m.setReceiveQrImgAlipay(url);
            }
            m.setReceiveMode(1); // 上传收款码即模式A
            m.setReceiveQrStatus(1); // P0：待平台审核
            m.setUpdateTime(LocalDateTime.now());
            merchantRepository.save(m);
            auditLogService.record(merchantNo, merchantNo, "upload_qr", null, null,
                    "上传收款码 type=" + type + " url=" + url, "NONE", null);
            return Result.ok(url);
        } catch (IOException e) {
            throw new BizException("上传失败: " + e.getMessage());
        }
    }
}
