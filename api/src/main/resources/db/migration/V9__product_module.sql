-- Product module: category, product, SKU and structured attributes.
-- IDs are application-assigned snowflakes; times are UTC DATETIME(3) supplied explicitly by the application.
CREATE TABLE product_category (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  name VARCHAR(64) NOT NULL,
  sort_no INT NOT NULL DEFAULT 0,
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_category_merchant_id (merchant_id,id),
  UNIQUE KEY uk_product_category_name (merchant_id,parent_id,name),
  KEY idx_product_category_page (merchant_id,status,sort_no,id),
  CONSTRAINT fk_product_category_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id),
  CONSTRAINT fk_product_category_parent FOREIGN KEY (merchant_id,parent_id) REFERENCES product_category(merchant_id,id),
  CONSTRAINT ck_product_category_status CHECK (status IN ('ACTIVE','INACTIVE')),
  CONSTRAINT ck_product_category_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
  CONSTRAINT ck_product_category_sort CHECK (sort_no >= 0),
  CONSTRAINT ck_product_category_depth CHECK (parent_id IS NULL OR parent_id <> id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  category_id BIGINT NULL,
  product_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  name VARCHAR(128) NOT NULL,
  brand VARCHAR(64) NULL,
  description TEXT NULL,
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_merchant_id (merchant_id,id),
  UNIQUE KEY uk_product_code (merchant_id,product_code),
  KEY idx_product_status_page (merchant_id,status,updated_at,id),
  KEY idx_product_category_page (merchant_id,category_id,status,id),
  CONSTRAINT fk_product_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id),
  CONSTRAINT fk_product_category FOREIGN KEY (merchant_id,category_id) REFERENCES product_category(merchant_id,id),
  CONSTRAINT ck_product_code CHECK (CHAR_LENGTH(TRIM(product_code)) BETWEEN 1 AND 64),
  CONSTRAINT ck_product_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
  CONSTRAINT ck_product_status CHECK (status IN ('DRAFT','PUBLISHED','UNPUBLISHED','DISCONTINUED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_sku (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sku_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  title VARCHAR(128) NOT NULL,
  price DECIMAL(12,2) NOT NULL,
  attributes_json JSON NULL,
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_sku_merchant_id (merchant_id,id),
  UNIQUE KEY uk_product_sku_code (merchant_id,sku_code),
  KEY idx_product_sku_product_page (merchant_id,product_id,status,id),
  CONSTRAINT fk_product_sku_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id),
  CONSTRAINT fk_product_sku_product FOREIGN KEY (merchant_id,product_id) REFERENCES product(merchant_id,id),
  CONSTRAINT ck_product_sku_code CHECK (CHAR_LENGTH(TRIM(sku_code)) BETWEEN 1 AND 64),
  CONSTRAINT ck_product_sku_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
  CONSTRAINT ck_product_sku_price CHECK (price >= 0),
  CONSTRAINT ck_product_sku_status CHECK (status IN ('ACTIVE','DISCONTINUED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_attribute (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  attr_name VARCHAR(64) NOT NULL,
  attr_value VARCHAR(512) NOT NULL,
  searchable TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_attribute_name (merchant_id,product_id,attr_name),
  KEY idx_product_attribute_search (merchant_id,attr_name,attr_value,product_id),
  CONSTRAINT fk_product_attribute_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id),
  CONSTRAINT fk_product_attribute_product FOREIGN KEY (merchant_id,product_id) REFERENCES product(merchant_id,id),
  CONSTRAINT ck_product_attribute_key CHECK (CHAR_LENGTH(TRIM(attr_name)) > 0),
  CONSTRAINT ck_product_attribute_value CHECK (CHAR_LENGTH(TRIM(attr_value)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
