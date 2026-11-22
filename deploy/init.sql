-- 智慧自习室座位预约与占座检测系统 · 初始化脚本
-- 由 MySQL 容器在首次启动时执行（docker-entrypoint-initdb.d）

CREATE DATABASE IF NOT EXISTS seat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE seat;

CREATE TABLE IF NOT EXISTS room (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  floor INT NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS seat (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  code VARCHAR(32) NOT NULL,
  feature VARCHAR(128),
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  KEY idx_room (room_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reservation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  seat_id BIGINT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
  checkin_code VARCHAR(512),
  away_until DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- 并发预约的最后一道防线：同一座位同一时段只能有一条预约
  UNIQUE KEY uk_seat_start (seat_id, start_time),
  KEY idx_status_end (status, end_time)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS checkin_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reservation_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  device_id VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_res (reservation_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS violation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reservation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  type VARCHAR(32) NOT NULL,
  score INT NOT NULL DEFAULT -5,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- 同一预约的同一类违规只扣一次分（幂等）
  UNIQUE KEY uk_res_type (reservation_id, type)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS wait_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  seat_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  notified TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_seat (seat_id, notified)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_credit (
  user_id BIGINT PRIMARY KEY,
  score INT NOT NULL DEFAULT 100
) ENGINE=InnoDB;

-- 示例数据：两个自习室，各 20 个座位
INSERT INTO room (id, name, floor) VALUES (1, '第一自习室', 2), (2, '第二自习室', 3);

INSERT INTO seat (room_id, code, feature, status)
SELECT 1, CONCAT('A-', LPAD(n, 2, '0')),
       CASE WHEN n % 5 = 0 THEN '靠窗,有插座' WHEN n % 3 = 0 THEN '靠窗' ELSE '靠入口' END,
       'AVAILABLE'
FROM (SELECT ROW_NUMBER() OVER () AS n FROM information_schema.columns LIMIT 20) x;

INSERT INTO seat (room_id, code, feature, status)
SELECT 2, CONCAT('B-', LPAD(n, 2, '0')),
       CASE WHEN n % 4 = 0 THEN '有插座' ELSE '靠窗' END,
       'AVAILABLE'
FROM (SELECT ROW_NUMBER() OVER () AS n FROM information_schema.columns LIMIT 20) y;

INSERT INTO user_credit (user_id, score) VALUES (1001, 100), (1002, 90), (1003, 55)
ON DUPLICATE KEY UPDATE score = VALUES(score);
