
# 10-Minute Spoken Demo Script

*(Natural spoken English, service-oriented, aligned with terminal output)*

---

## Opening (0:00–0:45)

Hi everyone.
Today I'm showing you a collaborative financial service for groups who share money and responsibility.

Whether it's friends on a trip, roommates splitting bills, project teams, or even departments in a company—this service handles the complete financial workflow.

It creates shared financial spaces, applies flexible split rules, generates insights through analytics, produces settlement plans, and enforces financial guardrails like budgets.

Everything happens in a brand-new isolated ledger for this demo, so nothing affects existing data.

---

## Section 1 — Access (0:45–1:10)

I'll log in as Alice to get started.

---

## Section 2 — Finding Collaborators (1:10–1:45)

Real collaboration starts with people. I look up Bob and Charlie by their emails to bring them into our financial group.

`GET /api/v1/user-lookup?email=bob@gmail.com`
`GET /api/v1/user-lookup?email=charlie@gmail.com`

This turns email addresses into stable identities that can participate in shared finances.

---

## Section 3 — Creating the Shared Space (1:45–2:50)

Now I create the core object—the ledger.

`POST /api/v1/ledgers`

A ledger is a shared financial workspace with its own balance model, currency, and expense categories. Everything for this group lives here.

I verify it was created:

`GET /api/v1/ledgers/{ledger_id}`

---

## Section 4 — Managing Participation (2:50–4:00)

I add Bob and Charlie as active participants:

`POST /api/v1/ledgers/{ledger_id}/members`

Then verify the group composition:

`GET /api/v1/ledgers/{ledger_id}/members`

I show the group can change—remove Charlie temporarily, then add him back. The ledger maintains consistency through these changes.

---

## Section 5 — The Transaction Engine (4:00–6:10)

This is where the real financial work happens.

All activity goes into the ledger:

`POST /api/v1/ledgers/{ledger_id}/transactions`

First, an expense transaction where Alice pays for gas and tolls, split exactly: Alice covers $75, Bob $50, Charlie $25.

Then an income transaction—Alice gets a $100 refund, split across the group.

I add another expense later in the month for groceries, paid by Bob, to build trend data.

Finally, a small income transaction using percentage splits: 50% to Alice, 30% to Bob, 20% to Charlie. The service calculates the exact amounts automatically.

I show the transaction details and list the history to demonstrate how the service tracks everything consistently.

---

## Section 6 — Intelligence Layer (6:10–8:30)

This transforms raw activity into actionable insights.

First, analytics that summarize three months of activity:

`GET /api/v1/ledgers/{ledger_id}/analytics/overview?months=3`

It shows totals, trends, balances, and recommendations.

Then settlement planning—the service answers "who pays whom to settle up":

`GET /api/v1/ledgers/{ledger_id}/settlement-plan`

But real settlement considers practical constraints. I demonstrate different approaches:

`POST /api/v1/ledgers/{ledger_id}/settlement-plan`

With rounding rules for practical amounts, maximum transfer limits to break large payments into smaller ones, payment channel preferences between users, and optimization thresholds for minimum cost flows.

The service generates executable payment plans that work in the real world.

---

## Section 7 — Financial Controls (8:30–9:40)

I set a monthly budget for the ledger:

`POST /api/v1/ledgers/{ledger_id}/budgets`

Check the budget status:

`GET /api/v1/ledgers/{ledger_id}/budgets/status?year=2025&month=12`

Then create an expense that exceeds the limit. The service flags the violation and shows how it actively monitors spending against policies.

---

## Section 8 — Real-World Applications (9:40–10:40)

This same service model works across contexts.

For personal groups: roommates use one ledger for household expenses, settlement shows exactly who pays what.

For teams: project ledgers track shared costs, analytics reveal spending patterns, budgets enforce limits.

For organizations: departments become separate ledgers, complex cost allocations use split rules, settlement planning breaks large inter-department transfers into optimized smaller payments while respecting payment constraints.

The service scales naturally from small groups to complex enterprise finance.

---

## Closing (10:40–10:55)

What you've seen is a service that takes shared financial activity and produces structured insights, optimized settlements, and enforceable policies—all within collaborative, isolated workspaces.

Thank you.

