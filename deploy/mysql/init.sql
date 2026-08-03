CREATE DATABASE IF NOT EXISTS rocketmq_studio DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE rocketmq_studio;
SOURCE /docker-entrypoint-initdb.d/schema.sql;
