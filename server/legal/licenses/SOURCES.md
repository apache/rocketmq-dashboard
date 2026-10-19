# 第三方依赖上游许可证全文 — 来源清单

`server/pom.xml` 的 `binary-license-gate`（`server/scripts/legal.py jar <fat-jar>`）要求打进 fat jar
的每个三方依赖都能拿出上游许可证全文。绝大多数 Maven 制品的 jar 内部不含 LICENSE 文件，因此把这些
文本按依赖收集到本目录，门禁改从仓库读取。

- 每份文件都是**上游原文逐字收录**，不翻译、不截断、不加本仓库自己的注释；文件内不写来源信息，
  来源一律记在本表。
- 双许可组件把两份全文依次放进同一文件，中间用一行 `========== <许可名> ==========` 分隔；
  采用哪一份见「我们采用的许可」与「备注」。`logback.txt` 是这种形式（上游声明 + EPL-2.0 + LGPL-2.1）。
  `reflections.txt` **例外**：只收 tag 内逐字发布的 WTFPL 一份，理由见主表该行。
- 行尾统一 LF（无 CRLF），文件末尾保留且仅保留一个换行。
- 取用方式：`gh api repos/<owner>/<repo>/contents/<path>?ref=<tag>`（走 api.github.com；本机
  `raw.githubusercontent.com` 不可达）。全部 16 份都定位到了**对应版本的 tag**，无一退化到默认分支。
- 取用时间：2026-10-12。

## 主表

| 文件名 | 组件坐标 | 声明许可 | 我们采用的许可 | 来源 URL | 取用时间 | 备注 |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| `jtokkit.txt` | `com.knuddels:jtokkit:1.1.0` | MIT | MIT | https://github.com/knuddelsgmbh/jtokkit/blob/4c98bebbbc285de0432abca6b2c8f55f2f51133a/LICENSE （tag `1.1.0`，文件 `LICENSE`） | 2026-10-12 | — |
| `mcp.txt` | `io.modelcontextprotocol.sdk:mcp:2.0.0`<br>`io.modelcontextprotocol.sdk:mcp-core:2.0.0`<br>`io.modelcontextprotocol.sdk:mcp-json-jackson3:2.0.0` | MIT | MIT | https://github.com/modelcontextprotocol/java-sdk/blob/f56d038409473210c59d6eddef09c4b5cd36042b/LICENSE （tag `v2.0.0`，文件 `LICENSE`） | 2026-10-12 | **三个制品共用一份文本**（同仓库同 tag 的根 `LICENSE`；`mcp-parent` 根 pom 版本已核对为 `2.0.0`，且该 tag 下确有 `mcp-core` / `mcp-json-jackson3` 模块目录） |
| `animal-sniffer-annotations.txt` | `org.codehaus.mojo:animal-sniffer-annotations:1.26` | MIT | MIT | https://github.com/mojohaus/animal-sniffer/blob/a2d9596a701cc8c7a4b1741d5f09e8c16316d442/LICENSE （tag `animal-sniffer-1.26`，文件 `LICENSE`） | 2026-10-12 | 上游版权声明为 `Copyright (c) 2009 codehaus.org.`（animal-sniffer 项目整体许可，覆盖 annotations 子模块） |
| `antlr4-runtime.txt` | `org.antlr:antlr4-runtime:4.13.1` | BSD-3-Clause | BSD-3-Clause | https://github.com/antlr/antlr4/blob/7ed420ff2c78d62883875c442d75f32e73bc86c8/LICENSE.txt （tag `4.13.1`，文件 `LICENSE.txt`） | 2026-10-12 | ⚠️ **marker 偏差（文本本身无问题）**：ANTLR 的第 3 条写作 `Neither name of copyright holders nor the names of its contributors`，不含字面串 `Neither the name of`，标准 BSD-3 marker 校验会漏判。取的是该 tag 原文，未改字 |
| `antlr-runtime.txt` | `org.antlr:antlr-runtime:3.5.3` | BSD | BSD-3-Clause（ANTLR 变体） | https://github.com/antlr/antlr3/blob/0519032bb167b37f206968d9037753ad655a2623/tool/LICENSE.txt （tag `3.5.3`，文件 `tool/LICENSE.txt`） | 2026-10-12 | ⚠️ **待确认**。tag `3.5.3` 的仓库根目录与 `runtime/Java/` **都没有** LICENSE 文件（已用 `git/trees?recursive=1` 全树扫描，只有 `gunit/LICENSE.txt`、`tool/LICENSE.txt`、`runtime/Ruby/ANTLR-LICENSE.txt` 等各子模块副本）。根 `pom.xml` 声明 `BSD licence` → `http://antlr.org/license.html`。取同 tag 下 `tool/LICENSE.txt`（`Copyright (c) 2013 Terence Parr`，与 3.5.3 发布年份一致）。第 3 条写作 `The name of the author may not be used…`，不含字面 `Neither the name of` |
| `protobuf-java.txt` | `com.google.protobuf:protobuf-java:4.34.2`<br>`com.google.protobuf:protobuf-java-util:4.34.2` | BSD-3-Clause | BSD-3-Clause | https://github.com/protocolbuffers/protobuf/blob/b2f8f21c9a7b2bcd0cb26d92c459b7f2c30cd635/LICENSE （tag `v34.2`，文件 `LICENSE`） | 2026-10-12 | **两个制品共用一份文本**。版本映射已核对：`protobuf-java 4.34.2` ↔ protobuf release `34.2`，tag `v34.2` 的 `java/pom.xml` 中 `<version>4.34.2</version>` |
| `bcprov-jdk18on.txt` | `org.bouncycastle:bcprov-jdk18on:1.78.1` | Bouncy Castle Licence（MIT 型） | MIT（即 Bouncy Castle Licence） | https://github.com/bcgit/bc-java/blob/ada71e4cf20c990e045a57ee1d42378d84050e03/LICENSE.html （tag `r1rv78v1`，文件 `LICENSE.html`） | 2026-10-12 | ⚠️ **待确认（marker 不匹配，非许可不匹配）**。tag `r1rv78v1`（= 版本 1.78.1，`gradle.properties` 中 `version=1.78.1` 已核对）下唯一法律文件是 `LICENSE.html`，为 **HTML 包裹的 MIT 全文**，本文件按原文逐字保留（含 `<html>`/`<p>` 标签），未剥标签。全仓库不含字面串 `Bouncy Castle Licence`；近似字样 `The Bouncy Castle License` 只作为 `core/src/main/java/org/bouncycastle/LICENSE.java` 的 Javadoc 标题出现。正文与 MIT 完全同构，含 `Permission is hereby granted, free of charge` |
| `curvesapi.txt` | `com.github.virtuald:curvesapi:1.06` | BSD | BSD-3-Clause | https://github.com/virtuald/curvesapi/blob/d5185b552e4362a84042e3af1feff16a0d2fc11b/license.txt （tag `1.06`，文件 `license.txt`，注意小写） | 2026-10-12 | 上游文件为 CRLF 行尾，按门禁要求转成 LF（仅换行符，正文字符未改）；BSD-3 marker 全中（含 `Neither the name of`） |
| `dom4j.txt` | `org.dom4j:dom4j:2.1.4` | Plexus（BSD 型） | Plexus（BSD 型） | https://github.com/dom4j/dom4j/blob/8db3742e13860c6767971867458073e8dd0fa1d1/LICENSE （tag `version-2.1.4`，文件 `LICENSE`） | 2026-10-12 | ⚠️ **marker 偏差（文本本身无问题）**：dom4j 用的是 Plexus 风格 BSD 变体，首句为 `Redistribution and use of this software and associated documentation`（非 `…in source and binary forms`），第 3 条为 `The name "DOM4J" must not be used…`（非 `Neither the name of`），两个标准 BSD marker 均不命中。取的是该 tag 原文 |
| `istack-commons-runtime.txt` | `com.sun.istack:istack-commons-runtime:4.1.2` | EDL 1.0（BSD 型） | EDL 1.0（= BSD-3-Clause） | https://github.com/eclipse-ee4j/jaxb-istack-commons/blob/343a28eabbd483e9ac0c5d7be32c1aa50ba52631/LICENSE.md （tag `4.1.2`，文件 `LICENSE.md`） | 2026-10-12 | ⚠️ **marker 偏差**：Eclipse Distribution License 1.0 的正文本身就是 BSD-3-Clause 文本（版权主体写作 `Eclipse Foundation, Inc.`），文件内不含字面串 `Eclipse Distribution License`，EDL marker 校验会漏判；BSD-3 marker（含 `Neither the name of`）全中 |
| `reactive-streams.txt` | `org.reactivestreams:reactive-streams:1.0.4` | MIT-0 / CC0 | MIT-0 | https://github.com/reactive-streams/reactive-streams/blob/944163a4b2477a2bebaaada86b0ba910b6302f2f/LICENSE （tag `v1.0.4`，文件 `LICENSE`） | 2026-10-12 | 上游 LICENSE 首行即 `MIT No Attribution`（= MIT-0），无 CC0 文本；MIT marker 命中 |
| `reflections.txt` | `org.reflections:reflections:0.9.11` | WTFPL 或 New BSD（双许可） | **WTFPL**（tag 内唯一逐字发布的一份） | https://github.com/ronmamo/reflections/blob/c95609a4e83e1f3fc71e4b2fc58b17352a16eef7/COPYING.txt （tag `0.9.11`，文件 `COPYING.txt`） | 2026-10-12 | ⚠️ **待 PMC/法务确认**。本件由 `org.apache.rocketmq:rocketmq-tools:5.5.0` 传递引入，我方代码不直接调用。双许可里 **New BSD 的全文在 0.9.11 tag 内并不存在**（该 tag 根目录只有 `COPYING.txt`；源码文件无许可头；0.9.12 也没有；0.10 起新增的 `LICENSE-2.0.txt` 经核对是 Apache-2.0，对 0.9.11 无效），所以可选的只有「tag 内逐字的 WTFPL」与「SPDX 的 BSD-3 规范模板」。**模板里 `Copyright (c) [year], [fullname]` 的占位符无处可填**（上游没发布过填好的版本），而带占位符的文本不构成对任何人的授权，因此收录 WTFPL 原文。WTFPL 未在 ASF 的许可分类表里点名，认定表已给它挂 `review` 字段 → 每次构建打 WARNING；更干净的出路是排除该传递依赖，或等 rocketmq-tools 换掉 reflections。Apache RocketMQ 自己的 `distribution/LICENSE-BIN` 里没有 reflections，查不到 ASF 先例。 |
| `ST4.txt` | `org.antlr:ST4:4.3.4` | BSD | BSD-3-Clause（ANTLR 变体） | https://github.com/antlr/stringtemplate4/blob/a2c4ef074e7c5018c7eb46372ed07f756c140404/LICENSE.txt （tag `ST4-4.3.4`，文件 `LICENSE.txt`） | 2026-10-12 | ⚠️ **marker 偏差**：与 `antlr-runtime.txt` 同一 ANTLR 变体措辞，第 3 条为 `The name of the author may not be used…`，不含字面 `Neither the name of`。取的是该 tag 原文（`Copyright (c) 2011-2022 Terence Parr`） |
| `webjars-locator-lite.txt` | `org.webjars:webjars-locator-lite:1.1.3` | MIT | MIT | https://github.com/webjars/webjars-locator-lite/blob/f1ccc59473da1853f7258943a6baa0710fa2e960/LICENSE.md （tag `webjars-locator-lite-1.1.3`，文件 `LICENSE.md`） | 2026-10-12 | — |
| `zstd-jni.txt` | `com.github.luben:zstd-jni:1.5.2-2` | BSD-2-Clause | BSD-2-Clause | https://github.com/luben/zstd-jni/blob/3d5ab915167f2dfe244d2d2192b66a9feac7c543/LICENSE （tag `v1.5.2-2`，文件 `LICENSE`） | 2026-10-12 | BSD-2 正确**不含** `Neither the name of`；含 `Redistribution and use in source and binary forms` |
| `logback.txt` | `ch.qos.logback:logback-classic:1.5.34`<br>`ch.qos.logback:logback-core:1.5.34` | EPL-2.0（另有 LGPL-2.1 可选） | **EPL-2.0** | 双许可声明：https://github.com/qos-ch/logback/blob/e62272ac152469aec1ede056c3c7d0d7314e7bfe/LICENSE.txt （tag `v_1.5.34`）<br>EPL-2.0 全文：https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.txt<br>LGPL-2.1 全文：https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt | 2026-10-12 | **两个制品共用一份文本**。文件内三段，依次为：`========== Logback dual-licence notice (upstream LICENSE.txt) ==========`（tag `v_1.5.34` 的 471 字节声明原文）、`========== EPL-2.0 (Eclipse Public License - v 2.0) ==========`、`========== LGPL-2.1 (GNU Lesser General Public License v2.1) ==========`。上游 tag 内**只有声明没有全文**（全树扫描 `git/trees?recursive=1` 只找到各模块同内容的 `LICENSE.txt`），故全文取自 Eclipse Foundation / FSF 官方发布地址（均非 `raw.githubusercontent.com`，本机可直连）。**选 EPL-2.0**：上游明确「双许可，由 licensee 任选其一」，EPL-2.0 对企业分发更友好（无 LGPL 的动态链接/可替换要求与 copyleft 传染性争议）；LGPL-2.1 一并收录仅为完整呈现上游双许可事实，**不采用** |

## 校验口径与已知偏差

门禁脚本按许可类型校验标志性字样时，以下 6 份会因**上游措辞差异**而漏判，文本本身都是对应 tag
的原文，不要按 marker 失败去改文本：

| 文件 | 期望 marker | 实际 | 结论 |
| :-- | :-- | :-- | :-- |
| `antlr4-runtime.txt` | `Neither the name of` | `Neither name of copyright holders nor the names of its contributors` | ⚠️ marker 偏差 |
| `antlr-runtime.txt` | `Neither the name of` | `The name of the author may not be used to endorse…` | ⚠️ 待确认（tag 内无 Java runtime 专属 LICENSE） |
| `ST4.txt` | `Neither the name of` | `The name of the author may not be used to endorse…` | ⚠️ marker 偏差 |
| `dom4j.txt` | `Redistribution and use in source and binary forms` + `Neither the name of` | Plexus 变体，两条都不命中 | ⚠️ marker 偏差 |
| `istack-commons-runtime.txt` | `Eclipse Distribution License` | EDL 1.0 正文即 BSD-3 文本，无该标题行 | ⚠️ marker 偏差（BSD-3 marker 全中） |
| `bcprov-jdk18on.txt` | `Bouncy Castle Licence` | 上游为 HTML 包裹的 MIT 全文，无该字面串 | ⚠️ 待确认（MIT marker 命中） |

最终 16 份**全部是上游 tag 内的逐字原文**：`reflections.txt` 初版曾把 SPDX 的 BSD-3 规范模板
（带 `[year]` / `[fullname]` 占位符）作为第二份收进来，已删除，只留 tag `0.9.11` 的 `COPYING.txt`；
`antlr-runtime.txt` 取的是同一 tag 的 `tool/LICENSE.txt`（ANTLR 3 全项目同一份 BSD 文本，
Java runtime 目录下没有单独放）；`bcprov-jdk18on.txt` 是上游 `LICENSE.html` 的逐字内容
（Bouncy Castle 就以 HTML 形式发布许可，未做重排）。

其余 10 份（`jtokkit` / `mcp` / `animal-sniffer-annotations` / `protobuf-java` / `curvesapi` /
`reactive-streams` / `webjars-locator-lite` / `zstd-jni` / `logback` 的 EPL-2.0 段 /
`reflections` 的 WTFPL 全文）marker 全部命中。
