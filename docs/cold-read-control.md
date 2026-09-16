# Broker 冷读流控

最后验证日期：2026-09-08。协议依据 RocketMQ 5.5.0。

## 场景与入口

当消费组回溯历史数据引起磁盘读取压力时，可在 Broker 列表打开 `Cold-read control`，
查看该 Broker 的全局冷读计数、默认组阈值、管理员限额与自适应限额。
所选实例必须支持 Apache 运行时管理，Mock 模式禁用入口。
查询绑定所选实例登记的主节点 ID 0；找不到主节点时不会回退到从节点。

表格合并运行表、管理员配置和自适应配置，保留只有配置、尚无冷读计数的组。
自动生成的 `||adaptive` 键展示在相应组的自适应列，不允许作为人工修改目标。
未报告的字段显示 `Not reported`；所有计数和阈值均以字符串传递，避免丢失 64 位整数精度。

## 单组修改

点击 `Use group` 或手工填写合法消费组名。允许预先配置尚未读取冷数据的组。
选择设置正整数的字节阈值，或移除管理员覆盖；核对组名、Broker 和地址后勾选确认并提交。
修改组名、数值或操作会清除确认。保存期间表单与关闭操作锁定，页面不自动重试写入。
启用登录时，查询允许读者使用，修改需要管理员；前端权限提示不替代后端鉴权。

修改只作用于目标 Broker 上的一个组，不批量广播到集群，也不修改流控总开关。
移除管理员限额会重新采用自适应或默认阈值，不表示取消全部限流。
成功回执后，服务端回读同一 Broker 的配置键：

- 值符合预期：显示 `Broker configuration verified`。
- 值不符或回读失败：显示已接受但未核实，先刷新并检查再决定后续操作。
- 写入 RPC 本身失败：显示错误并记录失败审计；网络超时仍可能存在结果不确定性。

回读不是原子事务；并发管理员或 Broker 切换仍可能改变配置。审计记录目标、动作、
阈值、Broker 地址与核实结果，审计存储失败不将已完成写入变为可重试失败。

## 单位与生命周期

计数是清零周期内累积的字节数，不是每秒速率。官方实现启用流控时通常每 5 秒清零，
调度耗时可使实际周期发生偏移；运行组长时间无冷读后会从运行表移除。
全局阈值与组阈值共同参与流控，自适应策略可动态调整组限额。
仅凭某个采样计数不能断言客户端已经受限，禁用状态也不因保存组限额而改变。

组覆盖存储在 Broker 内存中，重启后需要重新配置；本功能不引入持久化或自动重放任务。
配置与运行表是不同采样，计数可能在采样期间清零。其他 RocketMQ 版本需要结合其实现解释。
依据官方 [ColdDataCgCtrService](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/coldctr/ColdDataCgCtrService.java)
和 [AdminBrokerProcessor](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/processor/AdminBrokerProcessor.java)。

## 接口、验证与回滚

- `GET /api/brokers/cold-read?instanceId=...&brokerName=...`：查询配置与运行表。
- `POST /api/brokers/cold-read/config`：提交 `instanceId`、`brokerName`、`group`、
  `action=SET|REMOVE`；SET 需要十进制字符串 `threshold`，REMOVE 不接受阈值。

后端验证：`mvn -B -ntp -Dtest=ColdReadTest,RuntimeAdminClientResolverTest,AuthInterceptorTest,OperationAuditServiceTest verify`。
前端验证：运行 `coldRead.test.ts`、`ColdReadDialog.test.tsx`、`BrokerCluster.test.tsx`，
执行修改文件 ESLint 和 `npm run build`。浏览器通过明确拦截的本地 API 检查确认流程。
真实集群 E2E、性能、压力、容量与混沌测试未执行。

无新增依赖、数据库或配置文件。无迁移，直接部署。
代码回滚移除接口与入口；配置回滚需恢复原管理员阈值，原先没有覆盖时执行移除。
操作前应记录原值，不能用重启 Broker 作为本功能的常规回滚方式。
