SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS rocketmq DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE rocketmq;
SOURCE /docker-entrypoint-initdb.d/schema.sql;
