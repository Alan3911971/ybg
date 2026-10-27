-- V22 补齐历史中奖记录的 customer_seq（"本店第 X 位顾客"）
--
-- 背景：
--   V21 给 announce_log 加了 customer_seq，并用正则从 content 里抽"本店第X位"。
--   但 2026-09-10 之后的后端话术改成了"恭喜您的盲盒开中了 7.50 折优惠券。"，
--   这句里没有序号，导致 19 条权威开奖记录（announce_id 168~234）customer_seq 为 NULL，
--   大屏底部信息条只剩"尾号 + 门店名"，缺"第X位顾客"。
--
-- 口径：
--   旧模板序号是逐条递增的，最大值 28 落在 announce_id=165。
--   这 19 条都在它之后，按时间顺序顺延为 29~47，与前面 1~28 衔接不断档。
--   只更新 customer_seq IS NULL 的行，重复执行不会覆盖线上真实写入的值。
--
-- 之后的新记录由 DrawService 直接用 box_prize_limit_stat 的参与人次写入，无需再补。

UPDATE announce_log SET customer_seq = 29 WHERE announce_id = 168 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 30 WHERE announce_id = 172 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 31 WHERE announce_id = 176 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 32 WHERE announce_id = 180 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 33 WHERE announce_id = 183 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 34 WHERE announce_id = 187 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 35 WHERE announce_id = 191 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 36 WHERE announce_id = 195 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 37 WHERE announce_id = 199 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 38 WHERE announce_id = 204 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 39 WHERE announce_id = 207 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 40 WHERE announce_id = 211 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 41 WHERE announce_id = 214 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 42 WHERE announce_id = 218 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 43 WHERE announce_id = 222 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 44 WHERE announce_id = 225 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 45 WHERE announce_id = 228 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 46 WHERE announce_id = 231 AND customer_seq IS NULL;
UPDATE announce_log SET customer_seq = 47 WHERE announce_id = 234 AND customer_seq IS NULL;
