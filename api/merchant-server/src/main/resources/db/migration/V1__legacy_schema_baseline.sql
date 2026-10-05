/*
 Navicat Premium Data Transfer

 Source Server         : Linux-MySQL
 Source Server Type    : MySQL
 Source Server Version : 80034 (8.0.34)
 Source Host           : localhost:3306
 Source Schema         : sky_take_out

 Target Server Type    : MySQL
 Target Server Version : 80034 (8.0.34)
 File Encoding         : 65001

 Date: 22/09/2026 20:02:28
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for address_book
-- ----------------------------
DROP TABLE IF EXISTS `address_book`;
CREATE TABLE `address_book`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `consignee` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '收货人',
  `sex` varchar(2) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '性别',
  `phone` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '手机号',
  `province_code` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '省级区划编号',
  `province_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '省级名称',
  `city_code` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '市级区划编号',
  `city_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '市级名称',
  `district_code` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '区级区划编号',
  `district_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '区级名称',
  `detail` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '详细地址',
  `label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标签',
  `is_default` tinyint(1) NOT NULL DEFAULT 0 COMMENT '默认 0 否 1是',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '地址簿' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of address_book
-- ----------------------------
INSERT INTO `address_book` VALUES (3, 4, 'dianchan', '1', '18809091215', '11', '北京市', '1101', '市辖区', '110102', '西城区', '市中心', '1', 0);
INSERT INTO `address_book` VALUES (4, 4, '王一一', '0', '13800000099', '44', '广东省', '4403', '深圳市', '440306', '宝安区', '深南大道(南头古城地铁站D口步行147米)', '1', 1);

-- ----------------------------
-- Table structure for category
-- ----------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` int NULL DEFAULT NULL COMMENT '类型   1 菜品分类 2 套餐分类',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '分类名称',
  `sort` int NOT NULL DEFAULT 0 COMMENT '顺序',
  `status` int NULL DEFAULT NULL COMMENT '分类状态 0:禁用，1:启用',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint NULL DEFAULT NULL COMMENT '创建人',
  `update_user` bigint NULL DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_category_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '菜品及套餐分类' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of category
-- ----------------------------
INSERT INTO `category` VALUES (11, 1, '酒水饮料', 10, 1, '2022-06-09 22:09:18', '2022-06-09 22:09:18', 1, 1);
INSERT INTO `category` VALUES (12, 1, '传统主食', 9, 1, '2022-06-09 22:09:32', '2022-06-09 22:18:53', 1, 1);
INSERT INTO `category` VALUES (13, 2, '人气套餐', 12, 1, '2022-06-09 22:11:38', '2022-06-10 11:04:40', 1, 1);
INSERT INTO `category` VALUES (15, 2, '商务套餐', 13, 1, '2022-06-09 22:14:10', '2022-06-10 11:04:48', 1, 1);
INSERT INTO `category` VALUES (16, 1, '蜀味烤鱼', 5, 1, '2022-06-09 22:15:37', '2026-04-25 20:23:11', 1, 1);
INSERT INTO `category` VALUES (17, 1, '蜀味牛蛙', 4, 1, '2022-06-09 22:16:14', '2026-04-25 20:23:13', 1, 1);
INSERT INTO `category` VALUES (18, 1, '特色蒸菜', 6, 1, '2022-06-09 22:17:42', '2022-06-09 22:17:42', 1, 1);
INSERT INTO `category` VALUES (19, 1, '新鲜时蔬', 7, 1, '2022-06-09 22:18:12', '2022-06-09 22:18:28', 1, 1);
INSERT INTO `category` VALUES (20, 1, '水煮鱼', 8, 1, '2022-06-09 22:22:29', '2022-06-09 22:23:45', 1, 1);
INSERT INTO `category` VALUES (21, 1, '汤类', 11, 1, '2022-06-10 10:51:47', '2022-06-10 10:51:47', 1, 1);

-- ----------------------------
-- Table structure for dish
-- ----------------------------
DROP TABLE IF EXISTS `dish`;
CREATE TABLE `dish`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '菜品名称',
  `category_id` bigint NOT NULL COMMENT '菜品分类id',
  `price` decimal(10, 2) NULL DEFAULT NULL COMMENT '菜品价格',
  `image` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '图片',
  `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '描述信息',
  `status` int NULL DEFAULT 1 COMMENT '0 停售 1 起售',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint NULL DEFAULT NULL COMMENT '创建人',
  `update_user` bigint NULL DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_dish_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 76 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '菜品' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dish
-- ----------------------------
INSERT INTO `dish` VALUES (46, '王老吉', 11, 6.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/41bfcacf-7ad4-4927-8b26-df366553a94c.png', '', 1, '2022-06-09 22:40:47', '2022-06-09 22:40:47', 1, 1);
INSERT INTO `dish` VALUES (47, '北冰洋', 11, 4.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/4451d4be-89a2-4939-9c69-3a87151cb979.png', '还是小时候的味道', 1, '2022-06-10 09:18:49', '2022-06-10 09:18:49', 1, 1);
INSERT INTO `dish` VALUES (48, '雪花啤酒', 11, 4.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/bf8cbfc1-04d2-40e8-9826-061ee41ab87c.png', '', 1, '2022-06-10 09:22:54', '2022-06-10 09:22:54', 1, 1);
INSERT INTO `dish` VALUES (49, '米饭', 12, 2.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/76752350-2121-44d2-b477-10791c23a8ec.png', '精选五常大米', 1, '2022-06-10 09:30:17', '2022-06-10 09:30:17', 1, 1);
INSERT INTO `dish` VALUES (50, '馒头', 12, 1.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/475cc599-8661-4899-8f9e-121dd8ef7d02.png', '优质面粉', 1, '2022-06-10 09:34:28', '2022-06-10 09:34:28', 1, 1);
INSERT INTO `dish` VALUES (51, '老坛酸菜鱼', 20, 56.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/4a9cefba-6a74-467e-9fde-6e687ea725d7.png', '原料：汤，草鱼，酸菜', 1, '2022-06-10 09:40:51', '2022-06-10 09:40:51', 1, 1);
INSERT INTO `dish` VALUES (52, '经典酸菜鮰鱼', 20, 66.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/5260ff39-986c-4a97-8850-2ec8c7583efc.png', '原料：酸菜，江团，鮰鱼', 1, '2022-06-10 09:46:02', '2022-06-10 09:46:02', 1, 1);
INSERT INTO `dish` VALUES (53, '蜀味水煮草鱼', 20, 38.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/a6953d5a-4c18-4b30-9319-4926ee77261f.png', '原料：草鱼，汤', 1, '2022-06-10 09:48:37', '2022-06-10 09:48:37', 1, 1);
INSERT INTO `dish` VALUES (54, '清炒小油菜', 19, 18.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/3613d38e-5614-41c2-90ed-ff175bf50716.png', '原料：小油菜', 1, '2022-06-10 09:51:46', '2022-06-10 09:51:46', 1, 1);
INSERT INTO `dish` VALUES (55, '蒜蓉娃娃菜', 19, 18.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/4879ed66-3860-4b28-ba14-306ac025fdec.png', '原料：蒜，娃娃菜', 1, '2022-06-10 09:53:37', '2022-06-10 09:53:37', 1, 1);
INSERT INTO `dish` VALUES (56, '清炒西兰花', 19, 18.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/e9ec4ba4-4b22-4fc8-9be0-4946e6aeb937.png', '原料：西兰花', 1, '2022-06-10 09:55:44', '2022-06-10 09:55:44', 1, 1);
INSERT INTO `dish` VALUES (57, '炝炒圆白菜', 19, 18.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/22f59feb-0d44-430e-a6cd-6a49f27453ca.png', '原料：圆白菜', 1, '2022-06-10 09:58:35', '2022-06-10 09:58:35', 1, 1);
INSERT INTO `dish` VALUES (58, '清蒸鲈鱼', 18, 98.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/c18b5c67-3b71-466c-a75a-e63c6449f21c.png', '原料：鲈鱼', 1, '2022-06-10 10:12:28', '2022-06-10 10:12:28', 1, 1);
INSERT INTO `dish` VALUES (59, '东坡肘子', 18, 138.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/a80a4b8c-c93e-4f43-ac8a-856b0d5cc451.png', '原料：猪肘棒', 1, '2022-06-10 10:24:03', '2022-06-10 10:24:03', 1, 1);
INSERT INTO `dish` VALUES (60, '梅菜扣肉', 18, 58.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/6080b118-e30a-4577-aab4-45042e3f88be.png', '原料：猪肉，梅菜', 1, '2022-06-10 10:26:03', '2022-06-10 10:26:03', 1, 1);
INSERT INTO `dish` VALUES (61, '剁椒鱼头', 18, 66.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/13da832f-ef2c-484d-8370-5934a1045a06.png', '原料：鲢鱼，剁椒', 1, '2022-06-10 10:28:54', '2022-06-10 10:28:54', 1, 1);
INSERT INTO `dish` VALUES (62, '金汤酸菜牛蛙', 17, 88.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7694a5d8-7938-4e9d-8b9e-2075983a2e38.png', '原料：鲜活牛蛙，酸菜', 1, '2022-06-10 10:33:05', '2022-06-10 10:33:05', 1, 1);
INSERT INTO `dish` VALUES (63, '香锅牛蛙', 17, 88.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/f5ac8455-4793-450c-97ba-173795c34626.png', '配料：鲜活牛蛙，莲藕，青笋', 1, '2022-06-10 10:35:40', '2022-06-10 10:35:40', 1, 1);
INSERT INTO `dish` VALUES (64, '馋嘴牛蛙', 17, 88.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7a55b845-1f2b-41fa-9486-76d187ee9ee1.png', '配料：鲜活牛蛙，丝瓜，黄豆芽', 1, '2022-06-10 10:37:52', '2022-06-10 10:37:52', 1, 1);
INSERT INTO `dish` VALUES (65, '草鱼2斤', 16, 68.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/b544d3ba-a1ae-4d20-a860-81cb5dec9e03.png', '原料：草鱼，黄豆芽，莲藕', 1, '2022-06-10 10:41:08', '2022-06-10 10:41:08', 1, 1);
INSERT INTO `dish` VALUES (66, '江团鱼2斤', 16, 119.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/a101a1e9-8f8b-47b2-afa4-1abd47ea0a87.png', '配料：江团鱼，黄豆芽，莲藕', 1, '2022-06-10 10:42:42', '2022-06-10 10:42:42', 1, 1);
INSERT INTO `dish` VALUES (67, '鮰鱼2斤', 16, 72.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/8cfcc576-4b66-4a09-ac68-ad5b273c2590.png', '原料：鮰鱼，黄豆芽，莲藕', 1, '2022-06-10 10:43:56', '2022-06-10 10:43:56', 1, 1);
INSERT INTO `dish` VALUES (68, '鸡蛋汤', 21, 5.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/c09a0ee8-9d19-428d-81b9-746221824113.png', '配料：鸡蛋，紫菜', 1, '2022-06-10 10:54:25', '2026-05-04 19:34:42', 1, 1);
INSERT INTO `dish` VALUES (69, '平菇豆腐汤', 21, 6.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/16d0a3d6-2253-4cfc-9b49-bf7bd9eb2ad2.png', '配料：豆腐，平菇', 1, '2022-06-10 10:55:02', '2026-04-27 09:53:28', 1, 1);
INSERT INTO `dish` VALUES (74, '新增菜品3', 16, 285.00, 'https://tlias-web-java-ai.oss-cn-shenzhen.aliyuncs.com/aa88937c-b2a6-43dc-9fe2-064a9bec526b.jpg', '5321', 0, '2026-04-26 15:41:50', '2026-04-26 15:41:50', 1, 1);
INSERT INTO `dish` VALUES (75, '新增菜品4', 18, 285.00, 'https://tlias-web-java-ai.oss-cn-shenzhen.aliyuncs.com/aa88937c-b2a6-43dc-9fe2-064a9bec526b.jpg', '5321', 0, '2026-04-26 15:48:05', '2026-04-26 15:48:05', 1, 1);

-- ----------------------------
-- Table structure for dish_flavor
-- ----------------------------
DROP TABLE IF EXISTS `dish_flavor`;
CREATE TABLE `dish_flavor`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dish_id` bigint NOT NULL COMMENT '菜品',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '口味名称',
  `value` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '口味数据list',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 108 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '菜品口味关系表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dish_flavor
-- ----------------------------
INSERT INTO `dish_flavor` VALUES (40, 10, '甜味', '[\"无糖\",\"少糖\",\"半糖\",\"多糖\",\"全糖\"]');
INSERT INTO `dish_flavor` VALUES (41, 7, '忌口', '[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (42, 7, '温度', '[\"热饮\",\"常温\",\"去冰\",\"少冰\",\"多冰\"]');
INSERT INTO `dish_flavor` VALUES (45, 6, '忌口', '[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (46, 6, '辣度', '[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (47, 5, '辣度', '[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (48, 5, '甜味', '[\"无糖\",\"少糖\",\"半糖\",\"多糖\",\"全糖\"]');
INSERT INTO `dish_flavor` VALUES (49, 2, '甜味', '[\"无糖\",\"少糖\",\"半糖\",\"多糖\",\"全糖\"]');
INSERT INTO `dish_flavor` VALUES (50, 4, '甜味', '[\"无糖\",\"少糖\",\"半糖\",\"多糖\",\"全糖\"]');
INSERT INTO `dish_flavor` VALUES (51, 3, '甜味', '[\"无糖\",\"少糖\",\"半糖\",\"多糖\",\"全糖\"]');
INSERT INTO `dish_flavor` VALUES (52, 3, '忌口', '[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (86, 52, '忌口', '[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (87, 52, '辣度', '[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (88, 51, '忌口', '[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (89, 51, '辣度', '[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (92, 53, '忌口', '[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (93, 53, '辣度', '[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (94, 54, '忌口', '[\"不要葱\",\"不要蒜\",\"不要香菜\"]');
INSERT INTO `dish_flavor` VALUES (95, 56, '忌口', '[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (96, 57, '忌口', '[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (97, 60, '忌口', '[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (101, 66, '辣度', '[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (102, 67, '辣度', '[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (103, 65, '辣度', '[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (104, 75, '甜味', '[\"无糖\",\"少糖\",\"半糖\",\"多糖\",\"全糖\"]');
INSERT INTO `dish_flavor` VALUES (105, 75, '辣度', '[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');

-- ----------------------------
-- Table structure for employee
-- ----------------------------
DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '姓名',
  `username` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '用户名',
  `password` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '密码',
  `phone` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '手机号',
  `sex` varchar(2) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '性别',
  `id_number` varchar(18) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '身份证号',
  `status` int NOT NULL DEFAULT 1 COMMENT '状态 0:禁用，1:启用',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint NULL DEFAULT NULL COMMENT '创建人',
  `update_user` bigint NULL DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '员工信息' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee
-- ----------------------------
INSERT INTO `employee` VALUES (1, '管理员', 'admin', 'e10adc3949ba59abbe56e057f20f883e', '13812312312', '1', '110101199001010047', 1, '2022-02-15 15:51:20', '2022-02-17 09:16:20', 10, 1);
INSERT INTO `employee` VALUES (2, '普语汐', '纳喇丹', 'e10adc3949ba59abbe56e057f20f883e', '09186163273', '女', '9', 1, '2026-04-25 10:42:22', '2026-04-25 10:42:22', 10, 10);
INSERT INTO `employee` VALUES (3, '李四', 'lisi', 'e10adc3949ba59abbe56e057f20f883e', '13212345678', '1', '111222333444555666', 1, '2026-04-25 10:45:59', '2026-04-25 10:45:59', 10, 10);
INSERT INTO `employee` VALUES (7, '王五', 'wangwu', 'e10adc3949ba59abbe56e057f20f883e', '13312345678', '1', '222333444555666111', 1, '2026-04-25 11:35:22', '2026-04-26 10:29:34', 1, 1);

-- ----------------------------
-- Table structure for order_detail
-- ----------------------------
DROP TABLE IF EXISTS `order_detail`;
CREATE TABLE `order_detail`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '名字',
  `image` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '图片',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `dish_id` bigint NULL DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint NULL DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '口味',
  `number` int NOT NULL DEFAULT 1 COMMENT '数量',
  `amount` decimal(10, 2) NOT NULL COMMENT '金额',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 32 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '订单明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of order_detail
-- ----------------------------
INSERT INTO `order_detail` VALUES (5, '馋嘴牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7a55b845-1f2b-41fa-9486-76d187ee9ee1.png', 4, 64, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (6, '香锅牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/f5ac8455-4793-450c-97ba-173795c34626.png', 4, 63, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (7, '金汤酸菜牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7694a5d8-7938-4e9d-8b9e-2075983a2e38.png', 4, 62, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (8, '雪花啤酒', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/bf8cbfc1-04d2-40e8-9826-061ee41ab87c.png', 6, 48, NULL, NULL, 1, 4.00);
INSERT INTO `order_detail` VALUES (9, '北冰洋', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/4451d4be-89a2-4939-9c69-3a87151cb979.png', 6, 47, NULL, NULL, 1, 4.00);
INSERT INTO `order_detail` VALUES (10, '王老吉', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/41bfcacf-7ad4-4927-8b26-df366553a94c.png', 6, 46, NULL, NULL, 1, 6.00);
INSERT INTO `order_detail` VALUES (11, '馋嘴牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7a55b845-1f2b-41fa-9486-76d187ee9ee1.png', 7, 64, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (12, '香锅牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/f5ac8455-4793-450c-97ba-173795c34626.png', 7, 63, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (13, '金汤酸菜牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7694a5d8-7938-4e9d-8b9e-2075983a2e38.png', 7, 62, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (14, '馋嘴牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7a55b845-1f2b-41fa-9486-76d187ee9ee1.png', 8, 64, NULL, NULL, 2, 88.00);
INSERT INTO `order_detail` VALUES (15, '馋嘴牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7a55b845-1f2b-41fa-9486-76d187ee9ee1.png', 9, 64, NULL, NULL, 2, 88.00);
INSERT INTO `order_detail` VALUES (16, '香锅牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/f5ac8455-4793-450c-97ba-173795c34626.png', 10, 63, NULL, NULL, 2, 88.00);
INSERT INTO `order_detail` VALUES (17, '金汤酸菜牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7694a5d8-7938-4e9d-8b9e-2075983a2e38.png', 11, 62, NULL, NULL, 2, 88.00);
INSERT INTO `order_detail` VALUES (18, '金汤酸菜牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7694a5d8-7938-4e9d-8b9e-2075983a2e38.png', 12, 62, NULL, NULL, 2, 88.00);
INSERT INTO `order_detail` VALUES (19, '馋嘴牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7a55b845-1f2b-41fa-9486-76d187ee9ee1.png', 13, 64, NULL, NULL, 2, 88.00);
INSERT INTO `order_detail` VALUES (20, '香锅牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/f5ac8455-4793-450c-97ba-173795c34626.png', 14, 63, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (21, '金汤酸菜牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7694a5d8-7938-4e9d-8b9e-2075983a2e38.png', 14, 62, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (22, '香锅牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/f5ac8455-4793-450c-97ba-173795c34626.png', 15, 63, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (23, '金汤酸菜牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7694a5d8-7938-4e9d-8b9e-2075983a2e38.png', 15, 62, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (24, '剁椒鱼头', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/13da832f-ef2c-484d-8370-5934a1045a06.png', 16, 61, NULL, NULL, 1, 66.00);
INSERT INTO `order_detail` VALUES (25, '东坡肘子', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/a80a4b8c-c93e-4f43-ac8a-856b0d5cc451.png', 16, 59, NULL, NULL, 1, 138.00);
INSERT INTO `order_detail` VALUES (26, '香锅牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/f5ac8455-4793-450c-97ba-173795c34626.png', 17, 63, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (27, '金汤酸菜牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7694a5d8-7938-4e9d-8b9e-2075983a2e38.png', 17, 62, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (28, '香锅牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/f5ac8455-4793-450c-97ba-173795c34626.png', 18, 63, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (29, '金汤酸菜牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7694a5d8-7938-4e9d-8b9e-2075983a2e38.png', 18, 62, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (30, '香锅牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/f5ac8455-4793-450c-97ba-173795c34626.png', 19, 63, NULL, NULL, 1, 88.00);
INSERT INTO `order_detail` VALUES (31, '馋嘴牛蛙', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7a55b845-1f2b-41fa-9486-76d187ee9ee1.png', 19, 64, NULL, NULL, 1, 88.00);

-- ----------------------------
-- Table structure for orders
-- ----------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '订单号',
  `status` int NOT NULL DEFAULT 1 COMMENT '订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款',
  `user_id` bigint NOT NULL COMMENT '下单用户',
  `address_book_id` bigint NOT NULL COMMENT '地址id',
  `order_time` datetime NOT NULL COMMENT '下单时间',
  `checkout_time` datetime NULL DEFAULT NULL COMMENT '结账时间',
  `pay_method` int NOT NULL DEFAULT 1 COMMENT '支付方式 1微信,2支付宝',
  `pay_status` tinyint NOT NULL DEFAULT 0 COMMENT '支付状态 0未支付 1已支付 2退款',
  `amount` decimal(10, 2) NOT NULL COMMENT '实收金额',
  `remark` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '备注',
  `phone` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '手机号',
  `address` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '地址',
  `user_name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '用户名称',
  `consignee` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '收货人',
  `cancel_reason` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '订单取消原因',
  `rejection_reason` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '订单拒绝原因',
  `cancel_time` datetime NULL DEFAULT NULL COMMENT '订单取消时间',
  `estimated_delivery_time` datetime NULL DEFAULT NULL COMMENT '预计送达时间',
  `delivery_status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '配送状态  1立即送出  0选择具体时间',
  `delivery_time` datetime NULL DEFAULT NULL COMMENT '送达时间',
  `pack_amount` int NULL DEFAULT NULL COMMENT '打包费',
  `tableware_number` int NULL DEFAULT NULL COMMENT '餐具数量',
  `tableware_status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '餐具数量状态  1按餐量提供  0选择具体数量',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '订单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of orders
-- ----------------------------
INSERT INTO `orders` VALUES (4, '1778743155783', 6, 4, 3, '2026-05-14 15:19:16', NULL, 1, 0, 273.00, '', '18809091215', NULL, NULL, 'dianchan', '订单超时, 自动取消', NULL, '2026-05-14 18:35:01', '2026-05-14 16:19:00', 0, NULL, 3, 0, 0);
INSERT INTO `orders` VALUES (5, '1778743241978', 5, 4, 3, '2026-05-14 15:20:41', NULL, 1, 0, 23.00, '', '18809091215', NULL, NULL, 'dianchan', '订单超时, 自动取消', NULL, '2026-05-14 18:35:01', '2026-05-14 16:20:00', 0, NULL, 3, 0, 0);
INSERT INTO `orders` VALUES (6, '1778743332141', 6, 4, 3, '2026-05-14 15:22:12', NULL, 1, 0, 23.00, '', '18809091215', NULL, NULL, 'dianchan', '订单超时, 自动取消', NULL, '2026-05-14 18:35:01', '2026-05-14 16:22:00', 0, NULL, 3, 0, 0);
INSERT INTO `orders` VALUES (7, '1778750103195', 3, 4, 3, '2026-05-14 17:15:03', '2026-05-14 17:15:05', 1, 1, 273.00, '', '18809091215', NULL, NULL, 'dianchan', NULL, NULL, NULL, '2026-05-14 18:14:00', 0, NULL, 3, 0, 0);
INSERT INTO `orders` VALUES (8, '1778807373382', 5, 4, 3, '2026-05-15 09:09:33', '2026-05-15 09:09:35', 1, 1, 184.00, '', '18809091215', NULL, NULL, 'dianchan', NULL, NULL, NULL, '2026-05-15 10:09:00', 0, NULL, 2, 0, 0);
INSERT INTO `orders` VALUES (9, '1778807400933', 2, 4, 3, '2026-05-15 09:10:01', '2026-05-15 09:10:02', 1, 1, 184.00, '', '18809091215', NULL, NULL, 'dianchan', NULL, NULL, NULL, '2026-05-15 10:09:00', 0, NULL, 2, 0, 0);
INSERT INTO `orders` VALUES (10, '1778807485591', 3, 4, 3, '2026-05-15 09:11:26', '2026-05-15 09:11:27', 1, 1, 184.00, '', '18809091215', NULL, NULL, 'dianchan', NULL, NULL, NULL, '2026-05-15 10:11:00', 0, NULL, 2, 0, 0);
INSERT INTO `orders` VALUES (11, '1778807549984', 5, 4, 3, '2026-05-15 09:12:30', '2026-05-15 09:12:31', 1, 1, 184.00, '', '18809091215', NULL, NULL, 'dianchan', NULL, NULL, NULL, '2026-05-15 10:12:00', 0, NULL, 2, 0, 0);
INSERT INTO `orders` VALUES (12, '1778807630693', 2, 4, 3, '2026-05-15 09:13:51', '2026-05-15 09:13:52', 1, 1, 184.00, '', '18809091215', NULL, NULL, 'dianchan', NULL, NULL, NULL, '2026-05-15 10:13:00', 0, NULL, 2, 0, 0);
INSERT INTO `orders` VALUES (13, '1778808004362', 3, 4, 3, '2026-05-15 09:20:04', '2026-05-15 09:20:08', 1, 1, 184.00, '', '18809091215', NULL, NULL, 'dianchan', NULL, NULL, NULL, '2026-05-15 10:20:00', 0, NULL, 2, 0, 0);
INSERT INTO `orders` VALUES (14, '1778808133822', 6, 4, 3, '2026-05-15 09:22:14', '2026-05-15 09:22:17', 1, 1, 184.00, '', '18809091215', NULL, NULL, 'dianchan', NULL, '订单量较多，暂时无法接单', '2026-05-15 21:07:35', '2026-05-15 10:22:00', 0, NULL, 2, 0, 0);
INSERT INTO `orders` VALUES (15, '1778808261022', 5, 4, 3, '2026-05-15 09:24:21', '2026-05-15 09:24:22', 1, 1, 184.00, '', '18809091215', NULL, NULL, 'dianchan', NULL, NULL, NULL, '2026-05-15 10:24:00', 0, '2026-05-15 21:13:39', 2, 0, 0);
INSERT INTO `orders` VALUES (16, '1778808697145', 5, 4, 3, '2026-05-15 09:31:37', '2026-05-15 09:31:38', 1, 1, 212.00, '', '18809091215', NULL, NULL, 'dianchan', NULL, NULL, NULL, '2026-05-15 10:31:00', 0, NULL, 2, 0, 0);
INSERT INTO `orders` VALUES (17, '1778808855476', 6, 4, 3, '2026-05-15 09:34:15', '2026-05-15 09:34:18', 1, 2, 184.00, '', '18809091215', NULL, NULL, 'dianchan', '用户取消', NULL, '2026-05-15 20:42:25', '2026-05-15 10:34:00', 0, NULL, 2, 0, 0);
INSERT INTO `orders` VALUES (18, '1778849124194', 6, 4, 3, '2026-05-15 20:45:24', '2026-05-15 20:45:29', 1, 1, 184.00, '', '18809091215', NULL, NULL, 'dianchan', '菜品已销售完，暂时无法接单', NULL, '2026-05-15 21:09:49', '2026-05-15 21:45:00', 0, NULL, 2, 0, 0);
INSERT INTO `orders` VALUES (19, '1778892856989', 2, 4, 4, '2026-05-16 08:54:17', '2026-05-16 08:54:19', 1, 1, 184.00, '', '13800000099', NULL, NULL, '王一一', NULL, NULL, NULL, '2026-05-16 09:54:00', 0, NULL, 2, 0, 0);

-- ----------------------------
-- Table structure for setmeal
-- ----------------------------
DROP TABLE IF EXISTS `setmeal`;
CREATE TABLE `setmeal`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id` bigint NOT NULL COMMENT '菜品分类id',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL COMMENT '套餐名称',
  `price` decimal(10, 2) NOT NULL COMMENT '套餐价格',
  `status` int NULL DEFAULT 1 COMMENT '售卖状态 0:停售 1:起售',
  `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '描述信息',
  `image` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '图片',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint NULL DEFAULT NULL COMMENT '创建人',
  `update_user` bigint NULL DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_setmeal_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 36 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '套餐' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of setmeal
-- ----------------------------
INSERT INTO `setmeal` VALUES (33, 13, '招牌套餐', 68.00, 1, '超值招牌套餐，含招牌炒饭+例汤+凉菜', 'https://tlias-web-java-ai.oss-cn-shenzhen.aliyuncs.com/a6b411bc-2b30-4520-9a22-dfdafe997276.jpg', '2026-04-29 22:47:03', '2026-05-04 21:12:36', 1, 1);
INSERT INTO `setmeal` VALUES (34, 15, '招牌双人套餐', 88.00, 1, '超值双人套餐（已停售）', 'https://tlias-web-java-ai.oss-cn-shenzhen.aliyuncs.com/0f3a954f-7f71-489f-ace9-e777efaea997.jpg', '2026-04-29 22:47:17', '2026-05-04 21:12:32', 1, 1);
INSERT INTO `setmeal` VALUES (35, 13, '人气套餐', 88.00, 1, '热门菜品组合，超值享受', 'https://tlias-web-java-ai.oss-cn-shenzhen.aliyuncs.com/setmeal/renqi.jpg', '2026-05-03 23:06:33', '2026-05-04 21:12:48', 1, 1);

-- ----------------------------
-- Table structure for setmeal_dish
-- ----------------------------
DROP TABLE IF EXISTS `setmeal_dish`;
CREATE TABLE `setmeal_dish`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `setmeal_id` bigint NULL DEFAULT NULL COMMENT '套餐id',
  `dish_id` bigint NULL DEFAULT NULL COMMENT '菜品id',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '菜品名称 （冗余字段）',
  `price` decimal(10, 2) NULL DEFAULT NULL COMMENT '菜品单价（冗余字段）',
  `copies` int NULL DEFAULT NULL COMMENT '菜品份数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 78 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '套餐菜品关系' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of setmeal_dish
-- ----------------------------
INSERT INTO `setmeal_dish` VALUES (54, 32, 1, '烤鱼', 68.00, 1);
INSERT INTO `setmeal_dish` VALUES (55, 32, 2, '米饭', 2.00, 2);
INSERT INTO `setmeal_dish` VALUES (56, 32, 8, '可乐', 5.00, 2);
INSERT INTO `setmeal_dish` VALUES (69, 35, 16, '蜀味烤鱼', 68.00, 1);
INSERT INTO `setmeal_dish` VALUES (70, 35, 17, '蜀味牛蛙', 48.00, 1);
INSERT INTO `setmeal_dish` VALUES (71, 35, 18, '特色蒸菜', 28.00, 1);
INSERT INTO `setmeal_dish` VALUES (72, 34, 5, '招牌炒饭', 32.00, 2);
INSERT INTO `setmeal_dish` VALUES (73, 34, 6, '例汤', 10.00, 2);
INSERT INTO `setmeal_dish` VALUES (74, 34, 7, '凉拌黄瓜', 12.00, 1);
INSERT INTO `setmeal_dish` VALUES (75, 33, 3, '招牌炒饭', 32.00, 1);
INSERT INTO `setmeal_dish` VALUES (76, 33, 4, '例汤', 10.00, 1);
INSERT INTO `setmeal_dish` VALUES (77, 33, 9, '凉拌黄瓜', 12.00, 1);

-- ----------------------------
-- Table structure for shopping_cart
-- ----------------------------
DROP TABLE IF EXISTS `shopping_cart`;
CREATE TABLE `shopping_cart`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '商品名称',
  `image` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '图片',
  `user_id` bigint NOT NULL COMMENT '主键',
  `dish_id` bigint NULL DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint NULL DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '口味',
  `number` int NOT NULL DEFAULT 1 COMMENT '数量',
  `amount` decimal(10, 2) NOT NULL COMMENT '金额',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 52 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '购物车' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of shopping_cart
-- ----------------------------

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid` varchar(45) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '微信用户唯一标识',
  `name` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '姓名',
  `phone` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '手机号',
  `sex` varchar(2) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '性别',
  `id_number` varchar(18) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '身份证号',
  `avatar` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL DEFAULT NULL COMMENT '头像',
  `create_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 70 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_bin COMMENT = '用户信息' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES (4, 'o_Dyr7R-5oWipxpP0H5LlT1CeZgE', NULL, NULL, NULL, NULL, NULL, '2026-05-03 22:15:31');
INSERT INTO `user` VALUES (5, 'o_4月1日_001', '张三', '13800000001', '男', NULL, 'https://example.com/avatar1.png', '2026-04-01 09:30:00');
INSERT INTO `user` VALUES (6, 'o_4月1日_002', '李四', '13800000002', '女', NULL, 'https://example.com/avatar2.png', '2026-04-01 14:20:00');
INSERT INTO `user` VALUES (7, 'o_4月2日_001', '王五', '13800000003', '男', NULL, 'https://example.com/avatar3.png', '2026-04-02 10:15:00');
INSERT INTO `user` VALUES (8, 'o_4月2日_002', '赵六', '13800000004', '女', NULL, 'https://example.com/avatar4.png', '2026-04-02 16:45:00');
INSERT INTO `user` VALUES (9, 'o_4月2日_003', '小明', '13800000005', '男', NULL, 'https://example.com/avatar5.png', '2026-04-02 19:00:00');
INSERT INTO `user` VALUES (10, 'o_4月3日_001', '小红', '13800000006', '女', NULL, 'https://example.com/avatar6.png', '2026-04-03 08:00:00');
INSERT INTO `user` VALUES (11, 'o_4月3日_002', '小华', '13800000007', '男', NULL, 'https://example.com/avatar7.png', '2026-04-03 12:30:00');
INSERT INTO `user` VALUES (12, 'o_4月4日_001', '小刚', '13800000008', '男', NULL, 'https://example.com/avatar8.png', '2026-04-04 11:00:00');
INSERT INTO `user` VALUES (13, 'o_4月4日_002', '小丽', '13800000009', '女', NULL, 'https://example.com/avatar9.png', '2026-04-04 15:30:00');
INSERT INTO `user` VALUES (14, 'o_4月4日_003', '小强', '13800000010', '男', NULL, 'https://example.com/avatar10.png', '2026-04-04 18:20:00');
INSERT INTO `user` VALUES (15, 'o_4月5日_001', '小芳', '13800000011', '女', NULL, 'https://example.com/avatar11.png', '2026-04-05 09:00:00');
INSERT INTO `user` VALUES (16, 'o_4月6日_001', '老张', '13800000012', '男', NULL, 'https://example.com/avatar12.png', '2026-04-06 14:00:00');
INSERT INTO `user` VALUES (17, 'o_4月6日_002', '老王', '13800000013', '男', NULL, 'https://example.com/avatar13.png', '2026-04-06 17:30:00');
INSERT INTO `user` VALUES (18, 'o_4月7日_001', '小李', '13800000014', '女', NULL, 'https://example.com/avatar14.png', '2026-04-07 10:00:00');
INSERT INTO `user` VALUES (19, 'o_4月8日_001', '小周', '13800000015', '男', NULL, 'https://example.com/avatar15.png', '2026-04-08 13:20:00');
INSERT INTO `user` VALUES (20, 'o_4月8日_002', '小吴', '13800000016', '女', NULL, 'https://example.com/avatar16.png', '2026-04-08 16:10:00');
INSERT INTO `user` VALUES (21, 'o_4月8日_003', '小郑', '13800000017', '男', NULL, 'https://example.com/avatar17.png', '2026-04-08 20:00:00');
INSERT INTO `user` VALUES (22, 'o_4月9日_001', '小黄', '13800000018', '女', NULL, 'https://example.com/avatar18.png', '2026-04-09 08:30:00');
INSERT INTO `user` VALUES (23, 'o_4月10日_001', '小陈', '13800000019', '男', NULL, 'https://example.com/avatar19.png', '2026-04-10 11:45:00');
INSERT INTO `user` VALUES (24, 'o_4月10日_002', '小林', '13800000020', '女', NULL, 'https://example.com/avatar20.png', '2026-04-10 15:00:00');
INSERT INTO `user` VALUES (25, 'o_4月10日_003', '小郭', '13800000021', '男', NULL, 'https://example.com/avatar21.png', '2026-04-10 18:30:00');
INSERT INTO `user` VALUES (26, 'o_5月1日_001', '刘一', '13800000022', '男', NULL, 'https://example.com/avatar22.png', '2026-05-01 09:00:00');
INSERT INTO `user` VALUES (27, 'o_5月1日_002', '陈二', '13800000023', '女', NULL, 'https://example.com/avatar23.png', '2026-05-01 12:00:00');
INSERT INTO `user` VALUES (28, 'o_5月1日_003', '张三丰', '13800000024', '男', NULL, 'https://example.com/avatar24.png', '2026-05-01 16:30:00');
INSERT INTO `user` VALUES (29, 'o_5月1日_004', '李慕白', '13800000025', '男', NULL, 'https://example.com/avatar25.png', '2026-05-01 19:45:00');
INSERT INTO `user` VALUES (30, 'o_5月2日_001', '王祖贤', '13800000026', '女', NULL, 'https://example.com/avatar26.png', '2026-05-02 10:00:00');
INSERT INTO `user` VALUES (31, 'o_5月2日_002', '张曼玉', '13800000027', '女', NULL, 'https://example.com/avatar27.png', '2026-05-02 14:00:00');
INSERT INTO `user` VALUES (32, 'o_5月2日_003', '林青霞', '13800000028', '女', NULL, 'https://example.com/avatar28.png', '2026-05-02 17:00:00');
INSERT INTO `user` VALUES (33, 'o_5月3日_001', '周润发', '13800000029', '男', NULL, 'https://example.com/avatar29.png', '2026-05-03 08:30:00');
INSERT INTO `user` VALUES (34, 'o_5月3日_002', '刘德华', '13800000030', '男', NULL, 'https://example.com/avatar30.png', '2026-05-03 11:00:00');
INSERT INTO `user` VALUES (35, 'o_5月3日_003', '梁朝伟', '13800000031', '男', NULL, 'https://example.com/avatar31.png', '2026-05-03 15:30:00');
INSERT INTO `user` VALUES (36, 'o_5月3日_004', '汤唯', '13800000032', '女', NULL, 'https://example.com/avatar32.png', '2026-05-03 20:00:00');
INSERT INTO `user` VALUES (37, 'o_5月4日_001', '巩俐', '13800000033', '女', NULL, 'https://example.com/avatar33.png', '2026-05-04 09:00:00');
INSERT INTO `user` VALUES (38, 'o_5月4日_002', '章子怡', '13800000034', '女', NULL, 'https://example.com/avatar34.png', '2026-05-04 13:00:00');
INSERT INTO `user` VALUES (39, 'o_5月5日_001', '周杰伦', '13800000035', '男', NULL, 'https://example.com/avatar35.png', '2026-05-05 10:30:00');
INSERT INTO `user` VALUES (40, 'o_5月5日_002', '蔡依林', '13800000036', '女', NULL, 'https://example.com/avatar36.png', '2026-05-05 14:30:00');
INSERT INTO `user` VALUES (41, 'o_5月5日_003', '王力宏', '13800000037', '男', NULL, 'https://example.com/avatar37.png', '2026-05-05 18:00:00');
INSERT INTO `user` VALUES (42, 'o_5月6日_001', '林俊杰', '13800000038', '男', NULL, 'https://example.com/avatar38.png', '2026-05-06 08:00:00');
INSERT INTO `user` VALUES (43, 'o_5月6日_002', '邓紫棋', '13800000039', '女', NULL, 'https://example.com/avatar39.png', '2026-05-06 12:00:00');
INSERT INTO `user` VALUES (44, 'o_5月6日_003', '陈奕迅', '13800000040', '男', NULL, 'https://example.com/avatar40.png', '2026-05-06 16:30:00');
INSERT INTO `user` VALUES (45, 'o_5月6日_004', '孙燕姿', '13800000041', '女', NULL, 'https://example.com/avatar41.png', '2026-05-06 19:00:00');
INSERT INTO `user` VALUES (46, 'o_5月7日_001', '五月天', '13800000042', '男', NULL, 'https://example.com/avatar42.png', '2026-05-07 09:30:00');
INSERT INTO `user` VALUES (47, 'o_5月7日_002', 'SHE组合', '13800000043', '女', NULL, 'https://example.com/avatar43.png', '2026-05-07 13:00:00');
INSERT INTO `user` VALUES (48, 'o_5月8日_001', '李白', '13800000044', '男', NULL, 'https://example.com/avatar44.png', '2026-05-08 10:00:00');
INSERT INTO `user` VALUES (49, 'o_5月8日_002', '杜甫', '13800000045', '男', NULL, 'https://example.com/avatar45.png', '2026-05-08 14:00:00');
INSERT INTO `user` VALUES (50, 'o_5月8日_003', '王维', '13800000046', '男', NULL, 'https://example.com/avatar46.png', '2026-05-08 17:00:00');
INSERT INTO `user` VALUES (51, 'o_5月9日_001', '白居易', '13800000047', '男', NULL, 'https://example.com/avatar47.png', '2026-05-09 08:00:00');
INSERT INTO `user` VALUES (52, 'o_5月9日_002', '苏轼', '13800000048', '男', NULL, 'https://example.com/avatar48.png', '2026-05-09 11:00:00');
INSERT INTO `user` VALUES (53, 'o_5月10日_001', '李清照', '13800000049', '女', NULL, 'https://example.com/avatar49.png', '2026-05-10 09:30:00');
INSERT INTO `user` VALUES (54, 'o_5月10日_002', '辛弃疾', '13800000050', '男', NULL, 'https://example.com/avatar50.png', '2026-05-10 15:00:00');
INSERT INTO `user` VALUES (55, 'o_5月10日_003', '陆游', '13800000051', '男', NULL, 'https://example.com/avatar51.png', '2026-05-10 18:30:00');
INSERT INTO `user` VALUES (56, 'o_5月11日_001', '杨万里', '13800000052', '男', NULL, 'https://example.com/avatar52.png', '2026-05-11 10:00:00');
INSERT INTO `user` VALUES (57, 'o_5月11日_002', '范成大', '13800000053', '男', NULL, 'https://example.com/avatar53.png', '2026-05-11 13:30:00');
INSERT INTO `user` VALUES (58, 'o_5月12日_001', '陶渊明', '13800000054', '男', NULL, 'https://example.com/avatar54.png', '2026-05-12 08:30:00');
INSERT INTO `user` VALUES (59, 'o_5月12日_002', '谢灵运', '13800000055', '男', NULL, 'https://example.com/avatar55.png', '2026-05-12 12:00:00');
INSERT INTO `user` VALUES (60, 'o_5月12日_003', '孟浩然', '13800000056', '男', NULL, 'https://example.com/avatar56.png', '2026-05-12 16:00:00');
INSERT INTO `user` VALUES (61, 'o_5月13日_001', '王昌龄', '13800000057', '男', NULL, 'https://example.com/avatar57.png', '2026-05-13 09:00:00');
INSERT INTO `user` VALUES (62, 'o_5月13日_002', '高适', '13800000058', '男', NULL, 'https://example.com/avatar58.png', '2026-05-13 14:30:00');
INSERT INTO `user` VALUES (63, 'o_5月14日_001', '岑参', '13800000059', '男', NULL, 'https://example.com/avatar59.png', '2026-05-14 11:00:00');
INSERT INTO `user` VALUES (64, 'o_5月14日_002', '李商隐', '13800000060', '男', NULL, 'https://example.com/avatar60.png', '2026-05-14 15:30:00');
INSERT INTO `user` VALUES (65, 'o_5月14日_003', '杜牧', '13800000061', '男', NULL, 'https://example.com/avatar61.png', '2026-05-14 19:00:00');
INSERT INTO `user` VALUES (66, 'o_5月15日_001', '温庭筠', '13800000062', '男', NULL, 'https://example.com/avatar62.png', '2026-05-15 08:00:00');
INSERT INTO `user` VALUES (67, 'o_5月15日_002', '韦应物', '13800000063', '男', NULL, 'https://example.com/avatar63.png', '2026-05-15 13:00:00');
INSERT INTO `user` VALUES (68, 'o_5月15日_003', '刘禹锡', '13800000064', '男', NULL, 'https://example.com/avatar64.png', '2026-05-15 17:30:00');
INSERT INTO `user` VALUES (69, 'o_5月15日_004', '柳宗元', '13800000065', '男', NULL, 'https://example.com/avatar65.png', '2026-05-15 20:30:00');

SET FOREIGN_KEY_CHECKS = 1;
