-- MySQL is authoritative; PROCESSING/FAILED documents never become answer evidence.
CREATE TABLE knowledge_document (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  chunk_count INT NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_knowledge_document_merchant (merchant_id,id),
  KEY idx_knowledge_document_page (merchant_id,created_at,id),
  CONSTRAINT fk_knowledge_document_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id),
  CONSTRAINT ck_knowledge_document_status CHECK (status IN ('PROCESSING','READY','FAILED')),
  CONSTRAINT ck_knowledge_document_chunks CHECK (chunk_count BETWEEN 1 AND 200),
  CONSTRAINT ck_knowledge_document_title CHECK (CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_chunk (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  document_id BIGINT NOT NULL,
  chunk_no INT NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_knowledge_chunk_no (merchant_id,document_id,chunk_no),
  CONSTRAINT fk_knowledge_chunk_document FOREIGN KEY (merchant_id,document_id) REFERENCES knowledge_document(merchant_id,id),
  CONSTRAINT ck_knowledge_chunk_no CHECK (chunk_no BETWEEN 1 AND 200),
  CONSTRAINT ck_knowledge_chunk_content CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 300)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Nullable for existing chat inserts; API mapping to [] is introduced with chat integration.
ALTER TABLE chat_message ADD COLUMN sources_json JSON NULL;
