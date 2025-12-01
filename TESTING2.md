先说结论：
你这个 `ledger_big_seed.sql` **完全够支撑一个「看得出效果」的可视化界面**了，尤其是最近 3 个月的趋势、按分类、按成员 AR/AP 都能画出比较好看的图。

下面我帮你拆一下「数据量是否够」+「前端应该怎么输参数来测试可视化」。

---

## 1. 这个 seed 有多少“可视化信号”？

### 1.1 Ledger 1：Road Trip Demo（id = 1）

**成员**：用户 1,2,3,4
**时间范围**：2025-09 ~ 2025-11（三个月，刚好适配 `months=3`）
**交易数**：10 笔，全是 EXPENSE，全部 4 人均分

按月总支出（我帮你算好了）：

* 2025-09：
  `120 + 200 + 320 + 80 = 720`
* 2025-10：
  `140 + 180 + 120 = 440`
* 2025-11：
  `160 + 220 + 100 = 480`
* **3 个月合计**：`720 + 440 + 480 = 1,640`

按分类合计（跨 3 个月）：

* Gas (id=1)：`120 + 140 + 100 = 360`
* Food (id=2)：`200 + 180 + 220 = 600`
* Lodging (id=3)：`320`
* Entertainment (id=4)：`80 + 120 = 200`
* Supplies (id=5)：`160`
* **总计**：`360 + 600 + 320 + 200 + 160 = 1,640`（对上）

AR/AP（debt_edges）：

* 每笔 EXPENSE 生成 3 条边（payer 做 creditor）
* 10 笔交易 → 30 条自动边
* 外加两条手工 AR/AP（id=31,32），让网路不那么对称

这些足够支撑：

* 月度趋势折线图（9,10,11 月）
* 分类占比饼图 / 条形图
* 每个成员净 AR/AP 统计
* settlement-plan 的最小转账方案

---

### 1.2 Ledger 2：Apartment Demo（id = 2）

**成员**：用户 4,5,6,7
**时间范围**：2025-09 ~ 2025-11
**交易数**：10 笔，全是房租/水电/菜/聚餐类 EXPENSE

按月总支出：

* 2025-09：`1800 + 210 + 150 + 120 = 2,280`
* 2025-10：`1800 + 220 + 165 + 130 = 2,315`
* 2025-11：`1800 + 180 = 1,980`
* **总计**：`2,280 + 2,315 + 1,980 = 6,575`

按分类（跨 3 个月）：

* Rent (6)：`1800 * 3 = 5,400`
* Utilities (8)：`210 + 220 = 430`
* Groceries (7)：`150 + 165 + 180 = 495`
* Dining (10)：`120 + 130 = 250`
* Internet (9)：目前没有记录
* **总计**：`5400 + 430 + 495 + 250 = 6,575`（也对上）

AR/AP：

* 每笔 4 人分摊 → 一笔 3 条边
* 10 笔 → 30 条
* 再加一条手工 AR/AP（id=231）：`5 -> 6, 18 美元`

对于“合租房账本”可视化很友好：

* 房租 vs 其他开支的对比
* 每月总支出趋势（基本稳定但有轻微波动）
* 室友之间谁长期垫款多、谁长期“占便宜”

---

## 2. 前端可视化：应该怎么“输入参数”来玩？

下面我直接按“你可能有的几个图”来讲，用 **具体参数 + 你应该看到的大致数值** 帮你验证。

### 2.1 总览 / 趋势图（Ledger 1）

**接口**：`GET /api/v1/ledgers/1/analytics/overview?months=3`

* ledgerId：`1`（Road Trip Demo）
* months：`3`（默认就是 3 的话可以不写）

**期望前端看到：**

* `total_income ≈ 0`（因为 seed 里没有 INCOME）
* `total_expense ≈ 1640.00`
* `net_balance ≈ -1640.00`
* `trend` 数组里有 3 条：

  | period  | income | expense |
    | ------- | ------ | ------- |
  | 2025-09 | 0      | ~720    |
  | 2025-10 | 0      | ~440    |
  | 2025-11 | 0      | ~480    |

你可以在前端：

* X 轴：period（2025-09, 2025-10, 2025-11）
* Y 轴：金额
* 两条线：Income（全 0）、Expense（720, 440, 480）

> ⚠️ 因为目前没有 INCOME，所以你那个推荐规则 “支出 > 收入” 会**一定触发**，这是正常的。
> 想测“不触发”的情况，你可以之后手动插几笔 INCOME。

---

### 2.2 按分类饼图 / 柱状图（Ledger 1）

**同一个接口**：`GET /api/v1/ledgers/1/analytics/overview?months=3`

前端可以对 `by_category` 画图，期望大致是：

* Food：600
* Gas：360
* Lodging：320
* Entertainment：200
* Supplies：160

你可以：

* 饼图：每个 category 份额
* 柱状图：X 为 CategoryName，Y 为 amount

**手动 sanity check：**

* 最大块应该是 Food（差不多占 600 / 1640 ≈ 36%）
* 最小块是 Supplies（160 / 1640 ≈ 9.7%）

---

### 2.3 AR/AP 列表 / 条形图（Ledger 1）

**接口**同上，前端从 `arap` 数组里画：

你可以做：

* 表格：每行是成员：`user_name`、`ar`（别人欠 TA）、`ap`（TA 欠别人）
* 条形图：对每个 user 画一条「净值 = ar - ap」

为了更好看：

* 输入：`ledger=Road Trip Demo`，`月份=最近 3 个月（2025-09~11）`
* 期望效果：总有人是净 creditor（AR > AP），总有人是净 debtor（AP > AR），不会全是 0

> ✅ 因为你有一堆 debt_edges + 不对称的手工 AR/AP，图上肯定有起伏。

---

### 2.4 Ledger 2：房租账本的对比图

**接口**：`GET /api/v1/ledgers/2/analytics/overview?months=3`

期望趋势图：

| period  | expense 大概值 |
| ------- | ----------- |
| 2025-09 | 2280        |
| 2025-10 | 2315        |
| 2025-11 | 1980        |

前端可以玩几个视图：

1. **整体趋势图**

   * X：2025-09, 2025-10, 2025-11
   * Y：total_expense
   * 会看到房租账本每月支出很高且相对稳定。

2. **分类条形 / 堆叠图**

   * Rent：5400（巨大的主角）
   * Utilities：430
   * Groceries：495
   * Dining：250
     ⇒ 可以堆叠成 “房租 vs 其他开支” 的图形。

3. **室友 AR/AP 图**

   * X：室友（Diana, Evan, Fay, Gina）
   * Y：净 AR-AP
   * 因为房租/水电/菜都是平摊但 Payer 不一样，所以每个人净值会不同：

   大致直觉（不算具体）：

   * 4 号（房族）经常付房租，**AR 会偏大**
   * 5/6/7 也轮流付 utilities / groceries / dining，**AR 也会有但相对小**

---

### 2.5 Settlement Plan（如果你前端接了 `/settlement-plan`）

**接口**：
`GET /api/v1/ledgers/1/settlement-plan`
`GET /api/v1/ledgers/2/settlement-plan`

前端可以：

* 输入：选择 Ledger（Road Trip / Apartment）
* 点击：`View Settlement Plan`
* 展示：

  | from_user | to_user | amount |
    | --------- | ------- | ------ |
  | Bob       | Alice   | ...    |
  | Charlie   | Diana   | ...    |
  | ...       | ...     | ...    |

这个是后端已经有的算法（heap 贪心），你的 seed 里有足够的 debt_edges 支撑「有点复杂但又不至于看不懂」的计划表。

---

## 3. 数据量够不够？要不要再加？

**就“功能验证 + 可视化看上去有东西”来说：**
✅ 完全够用，而且结构很合理（2 个场景型账本 + 3 个月连续数据 + 多分类 + 不对称 AR/AP）。

如果你之后还想**更全面测试逻辑**，可以考虑额外加几类数据（不一定现在就加）：

1. **INCOME 交易**

   * 至少给每个 ledger 插 1–2 笔 INCOME
   * 这样你在总览里就能看到 `total_income` 不为 0，
   * 且有时候 `total_income > total_expense`，用来验证推荐逻辑**不会总是触发**。

2. **不同 split_method**（现在全是 EXACT）

   * 造一笔 PERCENT、一笔 WEIGHT，再来光顾一下 EQUAL（让 computed_amount 算一下）
   * 可以测试你的 rounding & tail_allocation 逻辑和前端展示。

3. **一些 is_private = 1 的交易**

   * 测一下「非创建者看不到」的情况（不过这主要影响 Transaction list，不太影响 analytics 汇总，除非你在 SQL 里也做了 visibility 过滤）。

---

## 4. 小总结：前端测试时可以这么玩

给你一个“直接照着点”的版本：

1. 打开前端，登录一个 seed 里的用户（比如 `alice.seed@example.com` / `Passw0rd!`）。
2. 选择账本：**Road Trip Demo**

   * 统计区间：最近 3 个月（2025-09 ~ 2025-11）
   * 看仪表盘：

      * 总支出 ≈ 1640
      * 趋势图三点：720 / 440 / 480
      * 分类饼图：Food > Gas > Lodging > Entertainment > Supplies
3. 切到 AR/AP 视图：

   * 看每人 AR/AP 不同，有正有负。
4. 换账本：**Apartment Demo**

   * 统计区间：同样最近 3 个月
   * 总支出 ≈ 6575
   * 趋势：2280 / 2315 / 1980
   * 分类图：Rent 一家独大，其他类仅占小部分。
5. 如果你接了 settlement 接口，再点

   * `查看清算方案`：可以看到极简的转账清单。

这样走一圈，你能非常直观地确认：

* 后端统计 OK
* Analytics API 返回结构 OK
* 前端图表绑定字段 OK
* 交互（日期/ledger 切换）也 OK

如果你愿意，下一步我们还可以专门设计一套「手把手点击文档」，把每个图、每个按钮要输入的参数都列出来。
