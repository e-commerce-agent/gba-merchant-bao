-- Development only: 20 additional products, 40 SKUs, attributes and stock rows.
-- Preserve operator edits. Validate every identity before inserting business data.
-- R040 supplies the tenant's original charger/cable/earphone categories; V7 supplies stock.
DROP TEMPORARY TABLE IF EXISTS dev_showcase_category;
DROP TEMPORARY TABLE IF EXISTS dev_showcase_product;
DROP TEMPORARY TABLE IF EXISTS dev_showcase_sku;
DROP TEMPORARY TABLE IF EXISTS dev_showcase_attribute;
DROP TEMPORARY TABLE IF EXISTS dev_showcase_guard;

CREATE TEMPORARY TABLE dev_showcase_category (
  id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL, sort_no INT NOT NULL
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO dev_showcase_category VALUES
  (2500000000000801, '移动电源', 40),
  (2500000000000802, '电脑配件', 50),
  (2500000000000803, '输入设备', 60),
  (2500000000000804, '存储设备', 70),
  (2500000000000805, '智能配件', 80);

CREATE TEMPORARY TABLE dev_showcase_product (
  id BIGINT PRIMARY KEY, category_id BIGINT NOT NULL, product_code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL, brand VARCHAR(64) NOT NULL, description TEXT NOT NULL
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO dev_showcase_product VALUES
  (2500000000000901,2500000000000101,'P-DEMO-CHG-30W','30W 迷你快充充电器','GbaPower','轻巧单口 USB-C 充电器，支持 PD 快充，适合手机和平板日常补电。'),
  (2500000000000902,2500000000000101,'P-DEMO-CHG-100W','100W 三口氮化镓充电器','GbaPower','双 USB-C 加单 USB-A，多设备同时充电；总输出功率最高 100W。'),
  (2500000000000903,2500000000000101,'P-DEMO-CHG-140W','140W 桌面充电站','GbaPower','桌面多口供电，适合笔记本、手机和耳机集中充电，附可拆卸电源线。'),
  (2500000000000904,2500000000000101,'P-DEMO-WIRELESS-3IN1','三合一无线充电座','GbaPower','为兼容无线充电的手机、耳机和手表分别提供摆放位置；使用前确认设备兼容性。'),
  (2500000000000905,2500000000000102,'P-DEMO-CABLE-240W','240W 编织快充数据线','GbaPower','USB-C 双头编织线，带 E-Marker，充电功率取决于充电器与设备协商。'),
  (2500000000000906,2500000000000102,'P-DEMO-CABLE-A2C','USB-A 转 USB-C 数据线','GbaPower','适用于 USB-A 电源接口和 USB-C 设备，兼顾日常充电与文件传输。'),
  (2500000000000907,2500000000000103,'P-DEMO-HEADSET-ANC','头戴式降噪耳机','GbaSound','包耳式软垫设计，支持主动降噪及通透模式，适合通勤和桌面办公。'),
  (2500000000000908,2500000000000103,'P-DEMO-EARBUDS-OPEN','开放式蓝牙耳机','GbaSound','开放式佩戴，支持蓝牙连接与双麦通话，适合运动和长时间使用。'),
  (2500000000000909,2500000000000801,'P-DEMO-BANK-10K','10000mAh 便携移动电源','GbaPower','便携电量显示设计，支持 USB-C 输入输出，容量为演示标称值。'),
  (2500000000000910,2500000000000801,'P-DEMO-BANK-20K','20000mAh 笔记本移动电源','GbaPower','大容量双向快充，USB-C 单口最高 65W，适合支持 PD 充电的笔记本。'),
  (2500000000000911,2500000000000802,'P-DEMO-HUB-8IN1','八合一 USB-C 扩展坞','GbaLink','集成 HDMI、USB、读卡与 PD 接口；视频输出要求主机 USB-C 支持 DP Alt Mode。'),
  (2500000000000912,2500000000000802,'P-DEMO-HDMI-21','HDMI 2.1 高清连接线','GbaLink','用于显示器、电视与主机连接；实际分辨率和刷新率取决于两端设备。'),
  (2500000000000913,2500000000000802,'P-DEMO-STAND-LAPTOP','折叠笔记本支架','GbaDesk','铝合金折叠支架，多档高度调节，硅胶接触面保护机身。'),
  (2500000000000914,2500000000000803,'P-DEMO-KEYBOARD','轻薄无线键盘','GbaDesk','紧凑键位布局，支持蓝牙多设备切换，兼顾 Windows 与 macOS 常用快捷键。'),
  (2500000000000915,2500000000000803,'P-DEMO-MOUSE','三模静音鼠标','GbaDesk','支持蓝牙、2.4GHz 接收器和有线连接，配备静音按键与多档 DPI。'),
  (2500000000000916,2500000000000804,'P-DEMO-SSD','便携固态移动硬盘','GbaStorage','USB-C 移动固态存储，适合办公资料和影音备份；速度受主机接口影响。'),
  (2500000000000917,2500000000000804,'P-DEMO-FLASH-DUAL','双接口 USB 闪存盘','GbaStorage','同时提供 USB-A 与 USB-C 接口，方便电脑与兼容 OTG 手机交换文件。'),
  (2500000000000918,2500000000000804,'P-DEMO-TFCARD','高速存储卡','GbaStorage','microSD 存储卡，适合兼容的相机与掌机，使用前核对设备容量上限。'),
  (2500000000000919,2500000000000805,'P-DEMO-SOCKET','智能计量插座','GbaSmart','支持 2.4GHz Wi-Fi、定时开关与用电统计，采用国标插头接口。'),
  (2500000000000920,2500000000000805,'P-DEMO-TRACKER','蓝牙寻物标签','GbaSmart','通过配套演示应用进行近距离蓝牙寻物，不提供 GPS 或蜂窝网络定位。');

CREATE TEMPORARY TABLE dev_showcase_sku (
  id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL, sku_code VARCHAR(64) NOT NULL,
  title VARCHAR(128) NOT NULL, price DECIMAL(12,2) NOT NULL,
  attributes_json JSON NOT NULL, available_qty INT NOT NULL
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO dev_showcase_sku VALUES
  (2500000000001001,2500000000000901,'SKU-DEMO-CHG-30W-A','30W 迷你快充 白色',49.00,'{"功率":"30W","颜色":"白色"}',80),
  (2500000000001002,2500000000000901,'SKU-DEMO-CHG-30W-B','30W 迷你快充 黑色',49.00,'{"功率":"30W","颜色":"黑色"}',0),
  (2500000000001003,2500000000000902,'SKU-DEMO-CHG-100W-A','100W 三口快充 白色',169.00,'{"功率":"100W","颜色":"白色"}',45),
  (2500000000001004,2500000000000902,'SKU-DEMO-CHG-100W-B','100W 三口快充 黑色',169.00,'{"功率":"100W","颜色":"黑色"}',32),
  (2500000000001005,2500000000000903,'SKU-DEMO-CHG-140W-A','140W 桌面充电站 银色',259.00,'{"功率":"140W","颜色":"银色"}',28),
  (2500000000001006,2500000000000903,'SKU-DEMO-CHG-140W-B','140W 桌面充电站 深空灰',259.00,'{"功率":"140W","颜色":"深空灰"}',20),
  (2500000000001007,2500000000000904,'SKU-DEMO-WIRELESS-3IN1-A','三合一充电座 白色',139.00,'{"规格":"三合一","颜色":"白色"}',36),
  (2500000000001008,2500000000000904,'SKU-DEMO-WIRELESS-3IN1-B','三合一充电座 黑色',139.00,'{"规格":"三合一","颜色":"黑色"}',24),
  (2500000000001009,2500000000000905,'SKU-DEMO-CABLE-240W-A','240W 编织线 1米',39.90,'{"功率":"240W","长度":"1米"}',120),
  (2500000000001010,2500000000000905,'SKU-DEMO-CABLE-240W-B','240W 编织线 2米',49.90,'{"功率":"240W","长度":"2米"}',85),
  (2500000000001011,2500000000000906,'SKU-DEMO-CABLE-A2C-A','USB-A 转 USB-C 1米',15.90,'{"长度":"1米","颜色":"白色"}',150),
  (2500000000001012,2500000000000906,'SKU-DEMO-CABLE-A2C-B','USB-A 转 USB-C 2米',22.90,'{"长度":"2米","颜色":"白色"}',90),
  (2500000000001013,2500000000000907,'SKU-DEMO-HEADSET-ANC-A','头戴式降噪耳机 黑色',299.00,'{"颜色":"黑色","降噪":"主动降噪"}',30),
  (2500000000001014,2500000000000907,'SKU-DEMO-HEADSET-ANC-B','头戴式降噪耳机 米白色',299.00,'{"颜色":"米白色","降噪":"主动降噪"}',18),
  (2500000000001015,2500000000000908,'SKU-DEMO-EARBUDS-OPEN-A','开放式蓝牙耳机 曜石黑',239.00,'{"颜色":"曜石黑","佩戴":"开放式"}',42),
  (2500000000001016,2500000000000908,'SKU-DEMO-EARBUDS-OPEN-B','开放式蓝牙耳机 云雾白',239.00,'{"颜色":"云雾白","佩戴":"开放式"}',26),
  (2500000000001017,2500000000000909,'SKU-DEMO-BANK-10K-A','10000mAh 移动电源 白色',89.00,'{"容量":"10000mAh","颜色":"白色"}',60),
  (2500000000001018,2500000000000909,'SKU-DEMO-BANK-10K-B','10000mAh 移动电源 蓝色',89.00,'{"容量":"10000mAh","颜色":"蓝色"}',44),
  (2500000000001019,2500000000000910,'SKU-DEMO-BANK-20K-A','20000mAh 65W 移动电源 黑色',199.00,'{"容量":"20000mAh","功率":"65W","颜色":"黑色"}',35),
  (2500000000001020,2500000000000910,'SKU-DEMO-BANK-20K-B','20000mAh 65W 移动电源 银色',199.00,'{"容量":"20000mAh","功率":"65W","颜色":"银色"}',22),
  (2500000000001021,2500000000000911,'SKU-DEMO-HUB-8IN1-A','八合一扩展坞 深空灰',159.00,'{"接口":"八合一","颜色":"深空灰"}',40),
  (2500000000001022,2500000000000911,'SKU-DEMO-HUB-8IN1-B','八合一扩展坞 银色',159.00,'{"接口":"八合一","颜色":"银色"}',25),
  (2500000000001023,2500000000000912,'SKU-DEMO-HDMI-21-A','HDMI 2.1 连接线 1.5米',35.00,'{"版本":"HDMI 2.1","长度":"1.5米"}',80),
  (2500000000001024,2500000000000912,'SKU-DEMO-HDMI-21-B','HDMI 2.1 连接线 3米',49.00,'{"版本":"HDMI 2.1","长度":"3米"}',50),
  (2500000000001025,2500000000000913,'SKU-DEMO-STAND-LAPTOP-A','折叠笔记本支架 银色',79.00,'{"材质":"铝合金","颜色":"银色"}',55),
  (2500000000001026,2500000000000913,'SKU-DEMO-STAND-LAPTOP-B','折叠笔记本支架 深空灰',79.00,'{"材质":"铝合金","颜色":"深空灰"}',38),
  (2500000000001027,2500000000000914,'SKU-DEMO-KEYBOARD-A','轻薄无线键盘 白色',129.00,'{"布局":"紧凑配列","颜色":"白色"}',48),
  (2500000000001028,2500000000000914,'SKU-DEMO-KEYBOARD-B','轻薄无线键盘 深灰色',129.00,'{"布局":"紧凑配列","颜色":"深灰色"}',30),
  (2500000000001029,2500000000000915,'SKU-DEMO-MOUSE-A','三模静音鼠标 黑色',89.00,'{"连接":"三模","颜色":"黑色"}',72),
  (2500000000001030,2500000000000915,'SKU-DEMO-MOUSE-B','三模静音鼠标 白色',89.00,'{"连接":"三模","颜色":"白色"}',46),
  (2500000000001031,2500000000000916,'SKU-DEMO-SSD-A','便携固态移动硬盘 512GB',299.00,'{"容量":"512GB","接口":"USB-C"}',25),
  (2500000000001032,2500000000000916,'SKU-DEMO-SSD-B','便携固态移动硬盘 1TB',459.00,'{"容量":"1TB","接口":"USB-C"}',16),
  (2500000000001033,2500000000000917,'SKU-DEMO-FLASH-DUAL-A','双接口闪存盘 64GB',39.90,'{"容量":"64GB","接口":"USB-A/USB-C"}',100),
  (2500000000001034,2500000000000917,'SKU-DEMO-FLASH-DUAL-B','双接口闪存盘 128GB',59.90,'{"容量":"128GB","接口":"USB-A/USB-C"}',75),
  (2500000000001035,2500000000000918,'SKU-DEMO-TFCARD-A','高速存储卡 128GB',69.00,'{"容量":"128GB","等级":"U3 A2"}',80),
  (2500000000001036,2500000000000918,'SKU-DEMO-TFCARD-B','高速存储卡 256GB',119.00,'{"容量":"256GB","等级":"U3 A2"}',52),
  (2500000000001037,2500000000000919,'SKU-DEMO-SOCKET-A','智能计量插座 单只装',49.00,'{"套装":"单只装","网络":"2.4GHz Wi-Fi"}',65),
  (2500000000001038,2500000000000919,'SKU-DEMO-SOCKET-B','智能计量插座 两只装',89.00,'{"套装":"两只装","网络":"2.4GHz Wi-Fi"}',40),
  (2500000000001039,2500000000000920,'SKU-DEMO-TRACKER-A','蓝牙寻物标签 白色',39.00,'{"颜色":"白色","定位":"近距离蓝牙"}',90),
  (2500000000001040,2500000000000920,'SKU-DEMO-TRACKER-B','蓝牙寻物标签 黑色',39.00,'{"颜色":"黑色","定位":"近距离蓝牙"}',0);

CREATE TEMPORARY TABLE dev_showcase_attribute (
  id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL,
  attr_name VARCHAR(64) NOT NULL, attr_value VARCHAR(512) NOT NULL
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO dev_showcase_attribute VALUES
  (2500000000001101,2500000000000901,'接口类型','USB-C'),
  (2500000000001102,2500000000000901,'快充协议','PD3.0;PPS'),
  (2500000000001103,2500000000000902,'接口类型','2个 USB-C;1个 USB-A'),
  (2500000000001104,2500000000000902,'额定功率','100W'),
  (2500000000001105,2500000000000903,'快充协议','PD3.1'),
  (2500000000001106,2500000000000903,'额定功率','140W'),
  (2500000000001107,2500000000000904,'充电方式','无线充电'),
  (2500000000001108,2500000000000904,'适用设备','兼容无线充电的手机;耳机;手表'),
  (2500000000001109,2500000000000905,'接口类型','USB-C/USB-C'),
  (2500000000001110,2500000000000905,'传输速率','480Mbps'),
  (2500000000001111,2500000000000906,'接口类型','USB-A/USB-C'),
  (2500000000001112,2500000000000906,'传输速率','480Mbps'),
  (2500000000001113,2500000000000907,'降噪类型','主动降噪;通透模式'),
  (2500000000001114,2500000000000907,'蓝牙版本','5.3'),
  (2500000000001115,2500000000000908,'佩戴方式','开放式'),
  (2500000000001116,2500000000000908,'蓝牙版本','5.4'),
  (2500000000001117,2500000000000909,'标称容量','10000mAh'),
  (2500000000001118,2500000000000909,'输出功率','22.5W'),
  (2500000000001119,2500000000000910,'标称容量','20000mAh'),
  (2500000000001120,2500000000000910,'快充协议','PD;最高 65W'),
  (2500000000001121,2500000000000911,'接口类型','USB-C;USB-A;HDMI;SD;microSD'),
  (2500000000001122,2500000000000911,'视频要求','主机 USB-C 支持 DP Alt Mode'),
  (2500000000001123,2500000000000912,'接口类型','HDMI/HDMI'),
  (2500000000001124,2500000000000912,'接口版本','HDMI 2.1'),
  (2500000000001125,2500000000000913,'适用尺寸','11至16英寸笔记本'),
  (2500000000001126,2500000000000913,'材质','铝合金;硅胶接触垫'),
  (2500000000001127,2500000000000914,'连接方式','蓝牙'),
  (2500000000001128,2500000000000914,'兼容系统','Windows;macOS;Android;iPadOS'),
  (2500000000001129,2500000000000915,'连接方式','蓝牙;2.4GHz;USB有线'),
  (2500000000001130,2500000000000915,'传感器','光学;多档 DPI'),
  (2500000000001131,2500000000000916,'接口类型','USB-C;USB 3.2 Gen 2'),
  (2500000000001132,2500000000000916,'存储介质','固态闪存'),
  (2500000000001133,2500000000000917,'接口类型','USB-A;USB-C'),
  (2500000000001134,2500000000000917,'手机要求','支持 USB OTG'),
  (2500000000001135,2500000000000918,'存储卡类型','microSDXC'),
  (2500000000001136,2500000000000918,'速度等级','UHS-I U3 A2'),
  (2500000000001137,2500000000000919,'网络要求','2.4GHz Wi-Fi'),
  (2500000000001138,2500000000000919,'插头规格','国标'),
  (2500000000001139,2500000000000920,'连接方式','蓝牙'),
  (2500000000001140,2500000000000920,'定位范围','近距离寻物;无 GPS');

SET @dev_showcase_merchant_id = (SELECT id FROM merchant WHERE merchant_code='M_3C_DEMO');
CREATE TEMPORARY TABLE dev_showcase_guard (
  dev_showcase_missing_merchant INT NOT NULL DEFAULT 1,
  dev_showcase_missing_category INT NOT NULL DEFAULT 1,
  dev_showcase_category_identity_conflict INT NOT NULL DEFAULT 1,
  dev_showcase_product_identity_conflict INT NOT NULL DEFAULT 1,
  dev_showcase_sku_identity_conflict INT NOT NULL DEFAULT 1,
  dev_showcase_attribute_identity_conflict INT NOT NULL DEFAULT 1,
  dev_showcase_inventory_identity_conflict INT NOT NULL DEFAULT 1
);
-- Separate guards avoid reopening a MySQL temporary table in the same query.
INSERT INTO dev_showcase_guard (dev_showcase_missing_merchant)
SELECT CASE WHEN @dev_showcase_merchant_id IS NOT NULL THEN 1 ELSE NULL END;
INSERT INTO dev_showcase_guard (dev_showcase_missing_category)
SELECT CASE WHEN (SELECT COUNT(*) FROM product_category WHERE merchant_id=@dev_showcase_merchant_id
    AND id IN (2500000000000101,2500000000000102,2500000000000103))=3 THEN 1 ELSE NULL END;
INSERT INTO dev_showcase_guard (dev_showcase_category_identity_conflict)
SELECT CASE WHEN EXISTS (
    SELECT 1 FROM dev_showcase_category e JOIN product_category c
      ON c.id=e.id OR (c.merchant_id=@dev_showcase_merchant_id AND c.parent_id IS NULL AND c.name=e.name)
    WHERE c.id<>e.id OR c.merchant_id<>@dev_showcase_merchant_id OR c.parent_id IS NOT NULL OR c.name<>e.name
  ) THEN NULL ELSE 1 END;
INSERT INTO dev_showcase_guard (dev_showcase_product_identity_conflict)
SELECT CASE WHEN EXISTS (
    SELECT 1 FROM dev_showcase_product e JOIN product p
      ON p.id=e.id OR (p.merchant_id=@dev_showcase_merchant_id AND p.product_code=e.product_code)
    WHERE p.id<>e.id OR p.merchant_id<>@dev_showcase_merchant_id OR p.product_code<>e.product_code
      OR NOT (p.category_id <=> e.category_id)
  ) THEN NULL ELSE 1 END;
INSERT INTO dev_showcase_guard (dev_showcase_sku_identity_conflict)
SELECT CASE WHEN EXISTS (
    SELECT 1 FROM dev_showcase_sku e JOIN product_sku k
      ON k.id=e.id OR (k.merchant_id=@dev_showcase_merchant_id AND k.sku_code=e.sku_code)
    WHERE k.id<>e.id OR k.merchant_id<>@dev_showcase_merchant_id OR k.sku_code<>e.sku_code OR k.product_id<>e.product_id
  ) THEN NULL ELSE 1 END;
INSERT INTO dev_showcase_guard (dev_showcase_attribute_identity_conflict)
SELECT CASE WHEN EXISTS (
    SELECT 1 FROM dev_showcase_attribute e JOIN product_attribute a
      ON a.id=e.id OR (a.merchant_id=@dev_showcase_merchant_id AND a.product_id=e.product_id AND a.attr_name=e.attr_name)
    WHERE a.id<>e.id OR a.merchant_id<>@dev_showcase_merchant_id OR a.product_id<>e.product_id OR a.attr_name<>e.attr_name
  ) THEN NULL ELSE 1 END;
INSERT INTO dev_showcase_guard (dev_showcase_inventory_identity_conflict)
SELECT CASE WHEN EXISTS (
    SELECT 1 FROM dev_showcase_sku e JOIN inventory i
      ON i.id=e.id+200 OR (i.merchant_id=@dev_showcase_merchant_id AND i.sku_id=e.id)
    WHERE i.id<>e.id+200 OR i.merchant_id<>@dev_showcase_merchant_id OR i.sku_id<>e.id
  ) THEN NULL ELSE 1 END;

-- Only DML below this point. Manual execution uses one transaction as well.
START TRANSACTION;
INSERT INTO product_category (id,merchant_id,parent_id,name,sort_no,status,created_at,updated_at)
SELECT e.id,@dev_showcase_merchant_id,NULL,e.name,e.sort_no,'ACTIVE','2026-11-16 00:00:00.000','2026-11-16 00:00:00.000'
FROM dev_showcase_category e WHERE NOT EXISTS (SELECT 1 FROM product_category c WHERE c.id=e.id);

INSERT INTO product (id,merchant_id,category_id,product_code,name,brand,description,status,created_at,updated_at)
SELECT e.id,@dev_showcase_merchant_id,e.category_id,e.product_code,e.name,e.brand,e.description,
  'PUBLISHED','2026-11-16 00:00:00.000','2026-11-16 00:00:00.000'
FROM dev_showcase_product e WHERE NOT EXISTS (SELECT 1 FROM product p WHERE p.id=e.id);

INSERT INTO product_sku (id,merchant_id,product_id,sku_code,title,price,attributes_json,status,created_at,updated_at)
SELECT e.id,@dev_showcase_merchant_id,e.product_id,e.sku_code,e.title,e.price,e.attributes_json,
  'ACTIVE','2026-11-16 00:00:00.000','2026-11-16 00:00:00.000'
FROM dev_showcase_sku e WHERE NOT EXISTS (SELECT 1 FROM product_sku k WHERE k.id=e.id);

INSERT INTO product_attribute (id,merchant_id,product_id,attr_name,attr_value,searchable,created_at,updated_at)
SELECT e.id,@dev_showcase_merchant_id,e.product_id,e.attr_name,e.attr_value,1,'2026-11-16 00:00:00.000','2026-11-16 00:00:00.000'
FROM dev_showcase_attribute e WHERE NOT EXISTS (SELECT 1 FROM product_attribute a WHERE a.id=e.id);

-- Inventory ID 1201..1240 corresponds to SKU ID 1001..1040 (same stable prefix).
INSERT INTO inventory (id,merchant_id,sku_id,available_qty,reserved_qty,version)
SELECT e.id+200,@dev_showcase_merchant_id,e.id,e.available_qty,0,0
FROM dev_showcase_sku e WHERE NOT EXISTS (SELECT 1 FROM inventory i WHERE i.id=e.id+200);
COMMIT;

DROP TEMPORARY TABLE dev_showcase_guard;
DROP TEMPORARY TABLE dev_showcase_attribute;
DROP TEMPORARY TABLE dev_showcase_sku;
DROP TEMPORARY TABLE dev_showcase_product;
DROP TEMPORARY TABLE dev_showcase_category;
SET @dev_showcase_merchant_id = NULL;
