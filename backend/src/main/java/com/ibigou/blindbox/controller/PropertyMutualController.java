package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 邻里互助 + 车位转让备案（/api/property/mutual）。
 *
 * 定位：物业只做撮合与备案，不抽佣、不做担保、不经手资金。
 * 只有「车位」必须备案——道闸白名单和月租账户在物业手里，不备案必然回到物业头上。
 */
@Slf4j
@RestController
@RequestMapping("/api/property/mutual")
@RequiredArgsConstructor
public class PropertyMutualController {

    private final PropertyMutualPostRepository postRepository;
    private final PropertyParkingTransferRepository transferRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final PropertyAdminRepository adminRepository;
    private final PropertyAdminSessionRepository sessionRepository;
    private final PropertyVehicleRepository vehicleRepository;
    private final PropertyBindRelationRepository bindRelationRepository;

    private static final Map<Integer, String> TYPE_NAMES = new LinkedHashMap<>();
    static {
        TYPE_NAMES.put(1, "车位出租");
        TYPE_NAMES.put(2, "车位求租");
        TYPE_NAMES.put(3, "找师傅");
        TYPE_NAMES.put(4, "二手");
        TYPE_NAMES.put(5, "拼车");
        TYPE_NAMES.put(6, "照看");
    }

    private Long validateOwner(String token) {
        Long ownerId = PropertyOwnerController.TOKEN_STORE.get(token == null ? "" : token);
        if (ownerId == null) throw new BizException("登录已过期");
        return ownerId;
    }

    private PropertyAdmin validateAdmin(String token) {
        if (token == null || token.isBlank()) throw new BizException("未登录");
        PropertyAdminSession session = sessionRepository.findByTokenAndExpireTimeAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BizException("登录已过期，请重新登录"));
        return adminRepository.findById(session.getAdminId())
                .orElseThrow(() -> new BizException("管理员不存在"));
    }

    /** admin 侧接口 companyId 可省略：缺省取登录管理员所属公司（与既有后台页面一致） */
    private Long resolveCompany(String token, Long companyId) {
        if (companyId != null) return companyId;
        return validateAdmin(token).getCompanyId();
    }

    /** 未指定类型时按标题/正文自动归类 */
    static int guessType(String title, String content) {
        String t = (title == null ? "" : title) + " " + (content == null ? "" : content);
        if (t.contains("求租") || t.contains("想租") || t.contains("找个车位")) return 2;
        if (t.contains("车位") || t.contains("出租")) return 1;
        if (t.contains("修") || t.contains("师傅") || t.contains("水电") || t.contains("疏通")) return 3;
        if (t.contains("拼车") || t.contains("顺路") || t.contains("捎")) return 5;
        if (t.contains("照看") || t.contains("接孩子") || t.contains("代收")) return 6;
        if (t.contains("转") || t.contains("出") || t.contains("九成新") || t.contains("闲置")) return 4;
        return 4;
    }


    /** 业主侧 companyId 可省略：缺省取该业主第一个有效绑定 */
    private Long ownerCompany(Long ownerId, Long companyId) {
        if (companyId != null) return companyId;
        return bindRelationRepository.findFirstByOwnerIdAndStatusOrderByBindTimeAsc(ownerId, 1)
                .map(PropertyBindRelation::getCompanyId)
                .orElseThrow(() -> new BizException("请先在「我的」绑定物业公司"));
    }
    // ================== 业主端 ==================

    @GetMapping("/owner/posts")
    public Result<List<Map<String, Object>>> ownerPosts(@RequestHeader(value = "X-Owner-Token", required = false) String token,
                                                        @RequestParam(required = false) Long companyId,
                                                        @RequestParam(required = false) Integer postType,
                                                        @RequestParam(required = false) Integer status) {
        if (companyId == null) companyId = ownerCompany(validateOwner(token), null);
        List<PropertyMutualPost> list;
        if (postType != null && status != null) {
            list = postRepository.findByCompanyIdAndPostTypeAndStatusOrderByCreateTimeDesc(companyId, postType, status);
        } else if (status != null) {
            list = postRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(companyId, status);
        } else {
            list = postRepository.findByCompanyIdOrderByCreateTimeDesc(companyId);
        }
        return Result.ok(list.stream().map(this::toMap).collect(Collectors.toList()));
    }

    @GetMapping("/owner/mine")
    public Result<List<Map<String, Object>>> myPosts(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwner(token);
        return Result.ok(postRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId)
                .stream().map(this::toMap).collect(Collectors.toList()));
    }

    /**
     * 发挂牌。车位类会在成交时自动带出备案单。
     */
    @PostMapping("/owner/posts")
    public Result<Map<String, Object>> createPost(@RequestHeader("X-Owner-Token") String token,
                                                  @RequestParam(required = false) Long companyId,
                                                  @RequestParam(required = false) Integer postType,
                                                  @RequestParam String title,
                                                  @RequestParam(required = false) String content,
                                                  @RequestParam(required = false) String priceDesc,
                                                  @RequestParam(required = false) String contact,
                                                  @RequestParam(required = false) String roomNo,
                                                  @RequestParam(required = false) Long communityId) {
        Long ownerId = validateOwner(token);
        companyId = ownerCompany(ownerId, companyId);
        long todayCount = postRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId).stream()
                .filter(p -> p.getCreateTime() != null && p.getCreateTime().isAfter(LocalDateTime.now().minusDays(1)))
                .count();
        if (todayCount >= 3) throw new BizException("24 小时内最多发布 3 条，避免刷屏");

        PropertyMutualPost p = new PropertyMutualPost();
        p.setCompanyId(companyId);
        p.setCommunityId(communityId);
        p.setPostType(postType != null ? postType : guessType(title, content));
        p.setTitle(title);
        p.setContent(content);
        p.setOwnerId(ownerId);
        p.setRoomNo(roomNo);
        p.setContact(contact);
        p.setPriceDesc(priceDesc);
        p.setStatus(0);
        p.setResponseCount(0);
        p.setExpireTime(LocalDateTime.now().plusDays(30));
        postRepository.save(p);
        return Result.ok(toMap(p));
    }

    /** 有人问 → 计数 */
    @PostMapping("/owner/posts/{postId}/respond")
    public Result<Void> respond(@RequestHeader("X-Owner-Token") String token, @PathVariable Long postId) {
        validateOwner(token);
        PropertyMutualPost p = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("挂牌不存在"));
        p.setResponseCount((p.getResponseCount() == null ? 0 : p.getResponseCount()) + 1);
        postRepository.save(p);
        return Result.ok();
    }

    /** 成交：车位类自动生成备案单（三步起点） */
    @PostMapping("/owner/posts/{postId}/deal")
    public Result<Map<String, Object>> deal(@RequestHeader("X-Owner-Token") String token,
                                            @PathVariable Long postId,
                                            @RequestParam(required = false) Long dealOwnerId,
                                            @RequestParam(required = false) String carPlate,
                                            @RequestParam(required = false) String spaceNo,
                                            @RequestParam(required = false) BigDecimal feeMonth,
                                            @RequestParam(required = false) String toRoomNo) {
        Long ownerId = validateOwner(token);
        PropertyMutualPost p = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("挂牌不存在"));
        if (p.getStatus() != null && p.getStatus() != 0) throw new BizException("该挂牌已结束");

        p.setStatus(1);
        p.setDealOwnerId(dealOwnerId);
        p.setDealTime(LocalDateTime.now());
        postRepository.save(p);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("post", toMap(p));

        boolean isParking = p.getPostType() != null && (p.getPostType() == 1 || p.getPostType() == 2);
        if (isParking) {
            if (carPlate == null || carPlate.isBlank()) {
                out.put("transferRequired", true);
                out.put("tip", "车位类必须备案：请补车牌，物业改月租账户并放白名单后道闸才进得去");
                return Result.ok(out);
            }
            PropertyParkingTransfer t = new PropertyParkingTransfer();
            t.setCompanyId(p.getCompanyId());
            t.setPostId(p.getPostId());
            t.setCarPlate(carPlate);
            t.setSpaceNo(spaceNo);
            t.setFeeMonth(feeMonth);
            if (p.getPostType() == 1) {
                t.setFromOwnerId(p.getOwnerId());
                t.setFromRoomNo(p.getRoomNo());
                t.setToOwnerId(dealOwnerId);
                t.setToRoomNo(toRoomNo);
            } else {
                t.setToOwnerId(p.getOwnerId());
                t.setToRoomNo(p.getRoomNo());
                t.setFromOwnerId(dealOwnerId);
                t.setFromRoomNo(toRoomNo);
            }
            t.setStepConfirm(1);
            t.setConfirmTime(LocalDateTime.now());
            t.setStatus(0);
            transferRepository.save(t);
            out.put("transferId", t.getTransferId());
            out.put("tip", "已生成备案单，下一步：物业改月租账户 → 放白名单");
        }
        return Result.ok(out);
    }

    @GetMapping("/owner/transfers")
    public Result<List<Map<String, Object>>> myTransfers(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwner(token);
        List<PropertyParkingTransfer> all = transferRepository.findAll().stream()
                .filter(t -> ownerId.equals(t.getFromOwnerId()) || ownerId.equals(t.getToOwnerId()))
                .sorted(Comparator.comparing(PropertyParkingTransfer::getCreateTime).reversed())
                .collect(Collectors.toList());
        return Result.ok(all.stream().map(this::transferMap).collect(Collectors.toList()));
    }

    // ================== 后台 ==================

    @GetMapping("/admin/summary")
    public Result<Map<String, Object>> summary(@RequestHeader("X-Property-Token") String token,
                                               @RequestParam(required = false) Long companyId) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        long onShelf = postRepository.countByCompanyIdAndStatus(companyId, 0);
        long pendingTransfer = transferRepository.countByCompanyIdAndStatus(companyId, 0);
        long doneDeal = postRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(companyId, 1).size();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("onShelf", onShelf);
        m.put("pendingTransfer", pendingTransfer);
        m.put("dealtThisMonth", doneDeal);
        m.put("types", TYPE_NAMES);
        return Result.ok(m);
    }

    @GetMapping("/admin/posts")
    public Result<List<Map<String, Object>>> adminPosts(@RequestHeader("X-Property-Token") String token,
                                                        @RequestParam(required = false) Long companyId,
                                                        @RequestParam(required = false) Integer postType,
                                                        @RequestParam(required = false) Integer status) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        List<PropertyMutualPost> list;
        if (postType != null && status != null) {
            list = postRepository.findByCompanyIdAndPostTypeAndStatusOrderByCreateTimeDesc(companyId, postType, status);
        } else if (status != null) {
            list = postRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(companyId, status);
        } else {
            list = postRepository.findByCompanyIdOrderByCreateTimeDesc(companyId);
        }
        return Result.ok(list.stream().map(this::toMap).collect(Collectors.toList()));
    }

    @PostMapping("/admin/posts/{postId}/offline")
    public Result<Void> offline(@RequestHeader("X-Property-Token") String token,
                                @PathVariable Long postId,
                                @RequestParam(required = false) String reason) {
        validateAdmin(token);
        PropertyMutualPost p = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("挂牌不存在"));
        p.setStatus(2);
        if (reason != null) p.setContent((p.getContent() == null ? "" : p.getContent())
                + "\n[物业下架] " + reason);
        postRepository.save(p);
        return Result.ok();
    }

    @GetMapping("/admin/transfers")
    public Result<List<Map<String, Object>>> adminTransfers(@RequestHeader("X-Property-Token") String token,
                                                            @RequestParam(required = false) Long companyId) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        return Result.ok(transferRepository.findByCompanyIdOrderByCreateTimeDesc(companyId)
                .stream().map(this::transferMap).collect(Collectors.toList()));
    }

    /**
     * 推进备案三步：step = confirm / account / whitelist。
     * 必须按序：② 要在 ① 之后，③ 要在 ② 之后（缺一步道闸就进不去）。
     */
    @PostMapping("/admin/transfers/{transferId}/step")
    public Result<Map<String, Object>> step(@RequestHeader("X-Property-Token") String token,
                                            @PathVariable Long transferId,
                                            @RequestParam String step,
                                            @RequestParam(required = false) String plate) {
        PropertyAdmin admin = validateAdmin(token);
        PropertyParkingTransfer t = transferRepository.findById(transferId)
                .orElseThrow(() -> new BizException("备案单不存在"));
        if (t.getStatus() != null && t.getStatus() == 2) throw new BizException("该单已取消");
        LocalDateTime now = LocalDateTime.now();
        t.setOperatorId(admin.getAdminId());

        switch (step) {
            case "confirm":
                t.setStepConfirm(1);
                t.setConfirmTime(now);
                break;
            case "account":
                if (!Integer.valueOf(1).equals(t.getStepConfirm())) throw new BizException("请先完成①双方确认");
                t.setStepAccount(1);
                t.setAccountTime(now);
                break;
            case "whitelist":
                if (!Integer.valueOf(1).equals(t.getStepAccount())) throw new BizException("请先完成②月租账户变更");
                t.setStepWhitelist(1);
                t.setWhitelistTime(now);
                // 同步车辆有效期（白名单生效的前提）
                if (plate != null && !plate.isBlank()) {
                    t.setCarPlate(plate);
                }
                List<PropertyVehicle> vs = vehicleRepository.findByPlateNoOrderByStatusDesc(t.getCarPlate());
                for (PropertyVehicle v : vs) {
                    v.setStatus(1);
                    vehicleRepository.save(v);
                }
                break;
            case "cancel":
                t.setStatus(2);
                transferRepository.save(t);
                return Result.ok(transferMap(t));
            default:
                throw new BizException("未知步骤：" + step);
        }

        if (Integer.valueOf(1).equals(t.getStepConfirm())
                && Integer.valueOf(1).equals(t.getStepAccount())
                && Integer.valueOf(1).equals(t.getStepWhitelist())) {
            t.setStatus(1);
        }
        transferRepository.save(t);
        return Result.ok(transferMap(t));
    }

    /** 后台代业主发布 */
    @PostMapping("/admin/posts")
    public Result<Map<String, Object>> adminCreatePost(@RequestHeader("X-Property-Token") String token,
                                                       @RequestParam(required = false) Long companyId,
                                                       @RequestParam Long ownerId,
                                                       @RequestParam Integer postType,
                                                       @RequestParam String title,
                                                       @RequestParam(required = false) String content,
                                                       @RequestParam(required = false) String priceDesc,
                                                       @RequestParam(required = false) String roomNo) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        PropertyMutualPost p = new PropertyMutualPost();
        p.setCompanyId(companyId);
        p.setPostType(postType);
        p.setTitle(title);
        p.setContent(content);
        p.setOwnerId(ownerId);
        p.setRoomNo(roomNo);
        p.setPriceDesc(priceDesc);
        p.setStatus(0);
        p.setExpireTime(LocalDateTime.now().plusDays(30));
        postRepository.save(p);
        return Result.ok(toMap(p));
    }

    // ================== 映射 ==================

    private Map<String, Object> toMap(PropertyMutualPost p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("postId", p.getPostId());
        m.put("postType", p.getPostType());
        m.put("typeName", TYPE_NAMES.getOrDefault(p.getPostType(), "其它"));
        m.put("title", p.getTitle());
        m.put("content", p.getContent());
        m.put("ownerId", p.getOwnerId());
        m.put("roomNo", p.getRoomNo());
        m.put("contact", p.getContact());
        m.put("priceDesc", p.getPriceDesc());
        m.put("status", p.getStatus());
        m.put("responseCount", p.getResponseCount());
        m.put("dealTime", p.getDealTime());
        m.put("createTime", p.getCreateTime());
        m.put("expireTime", p.getExpireTime());
        return m;
    }

    private Map<String, Object> transferMap(PropertyParkingTransfer t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("transferId", t.getTransferId());
        m.put("postId", t.getPostId());
        m.put("carPlate", t.getCarPlate());
        m.put("spaceNo", t.getSpaceNo());
        m.put("fromOwnerId", t.getFromOwnerId());
        m.put("fromRoomNo", t.getFromRoomNo());
        m.put("toOwnerId", t.getToOwnerId());
        m.put("toRoomNo", t.getToRoomNo());
        m.put("stepConfirm", t.getStepConfirm());
        m.put("stepAccount", t.getStepAccount());
        m.put("stepWhitelist", t.getStepWhitelist());
        m.put("status", t.getStatus());
        m.put("feeMonth", t.getFeeMonth());
        m.put("confirmTime", t.getConfirmTime());
        m.put("accountTime", t.getAccountTime());
        m.put("whitelistTime", t.getWhitelistTime());
        m.put("createTime", t.getCreateTime());
        int done = (Integer.valueOf(1).equals(t.getStepConfirm()) ? 1 : 0)
                + (Integer.valueOf(1).equals(t.getStepAccount()) ? 1 : 0)
                + (Integer.valueOf(1).equals(t.getStepWhitelist()) ? 1 : 0);
        m.put("stepDone", done);
        return m;
    }
}
