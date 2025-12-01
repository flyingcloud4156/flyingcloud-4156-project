let accessToken = null;
let trendChart = null;
let catChart = null;

function $(id) { return document.getElementById(id); }

function fmt(n) {
  if (n === null || n === undefined) return "-";
  const x = typeof n === "string" ? Number(n) : n;
  if (Number.isNaN(x)) return String(n);
  return x.toFixed(2);
}

async function api(path, { method = "GET", body = null } = {}) {
  const base = $("baseUrl").value.replace(/\/$/, "");
  const headers = { "Content-Type": "application/json" };
  if (accessToken) headers["X-Auth-Token"] = accessToken;

  const res = await fetch(base + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : null
  });

  const json = await res.json().catch(() => null);
  if (!json) throw new Error(`HTTP ${res.status}`);
  if (!json.success) throw new Error(json.message || "Request failed");
  return json.data;
}

async function login() {
  $("loginStatus").textContent = "Logging in...";
  try {
    const data = await api("/api/v1/auth/login", {
      method: "POST",
      body: { email: $("email").value, password: $("password").value }
    });
    accessToken = data.access_token || data.accessToken;
    $("loginStatus").textContent = "OK";
    $("app").classList.remove("hidden");
    await loadLedgers();
  } catch (e) {
    $("loginStatus").textContent = "Login failed: " + e.message;
  }
}

async function loadLedgers() {
  const data = await api("/api/v1/ledgers/mine");
  const items = data.items || [];
  const sel = $("ledgerSelect");
  sel.innerHTML = "";
  for (const it of items) {
    const opt = document.createElement("option");
    opt.value = it.ledger_id ?? it.ledgerId;
    opt.textContent = `${it.name} (#${opt.value})`;
    sel.appendChild(opt);
  }
}

function renderTotals(o) {
  $("kIncome").textContent = fmt(o.total_income ?? o.totalIncome);
  $("kExpense").textContent = fmt(o.total_expense ?? o.totalExpense);
  $("kNet").textContent = fmt(o.net_balance ?? o.netBalance);

  const rs = o.range_start ?? o.rangeStart;
  const re = o.range_end ?? o.rangeEnd;
  $("rangeInfo").textContent = rs && re ? `Range: ${rs}  →  ${re} (end exclusive)` : "";
}

function renderRecs(o) {
  const recs = o.recommendations || [];
  const box = $("recs");
  box.innerHTML = "";
  for (const r of recs) {
    const d = document.createElement("div");
    d.className = "badge " + (r.severity === "WARNING" ? "warn" : "");
    d.textContent = `${r.code}: ${r.message}`;
    box.appendChild(d);
  }
}

function renderTrend(o) {
  const trend = o.trend || [];
  const labels = trend.map(x => x.period);
  const income = trend.map(x => Number(x.income ?? 0));
  const expense = trend.map(x => Number(x.expense ?? 0));

  const ctx = $("trendChart").getContext("2d");
  if (trendChart) trendChart.destroy();

  trendChart = new Chart(ctx, {
    type: "line",
    data: {
      labels,
      datasets: [
        { label: "Income", data: income, tension: 0.2 },
        { label: "Expense", data: expense, tension: 0.2 }
      ]
    },
    options: {
      responsive: true,
      plugins: { legend: { labels: { color: "#e9ecf1" } } },
      scales: {
        x: { ticks: { color: "#b8c0dd" } },
        y: { ticks: { color: "#b8c0dd" } }
      }
    }
  });
}

function renderCategory(o) {
  const rows = o.by_category || o.byCategory || [];
  const labels = rows.map(x => x.category_name ?? x.categoryName);
  const values = rows.map(x => Number(x.amount ?? 0));

  const ctx = $("catChart").getContext("2d");
  if (catChart) catChart.destroy();

  catChart = new Chart(ctx, {
    type: "pie",
    data: { labels, datasets: [{ label: "Expense", data: values }] },
    options: {
      responsive: true,
      plugins: { legend: { labels: { color: "#e9ecf1" } } }
    }
  });
}

function renderArAp(o) {
  const rows = o.arap || [];
  const tb = $("arapTable").querySelector("tbody");
  tb.innerHTML = "";

  for (const r of rows) {
    const ar = Number(r.ar ?? 0);
    const ap = Number(r.ap ?? 0);
    const net = ar - ap;

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${r.user_name ?? r.userName}</td>
      <td>${ar.toFixed(2)}</td>
      <td>${ap.toFixed(2)}</td>
      <td>${net.toFixed(2)}</td>
    `;
    tb.appendChild(tr);
  }
}

function renderMerchants(o) {
  const rows = o.top_merchants || o.topMerchants || [];
  const tb = $("merchantTable").querySelector("tbody");
  tb.innerHTML = "";
  for (const r of rows) {
    const tr = document.createElement("tr");
    tr.innerHTML = `<td>${r.label}</td><td>${fmt(r.amount)}</td>`;
    tb.appendChild(tr);
  }
}

async function loadAnalytics() {
  const ledgerId = $("ledgerSelect").value;
  const months = $("months").value || "3";
  const o = await api(`/api/v1/ledgers/${ledgerId}/analytics/overview?months=${encodeURIComponent(months)}`);

  renderTotals(o);
  renderRecs(o);
  renderTrend(o);
  renderCategory(o);
  renderArAp(o);
  renderMerchants(o);
}

$("btnLogin").addEventListener("click", login);
$("btnLoad").addEventListener("click", () => loadAnalytics().catch(e => alert(e.message)));
