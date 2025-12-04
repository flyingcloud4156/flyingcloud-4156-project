/**
 * Unit tests for frontend/dashboard.js using JSDOM.
 *
 * External dependencies are fully mocked:
 * - fetch: mocked to return controlled JSON
 * - Chart: mocked to avoid canvas/chart.js dependency
 *
 * Expected values come from independent rules:
 * - Formatting rules (toFixed(2), null -> "-")
 * - DOM update invariants (textContent changes, option counts)
 * - Chart lifecycle invariant (destroy old chart before new one)
 */

const fs = require("fs");
const path = require("path");
const { JSDOM } = require("jsdom");

function buildDom() {
  const html = `
  <!doctype html>
  <html>
    <body>
      <input id="baseUrl" value="http://localhost:8081/" />
      <input id="email" value="alice@test.com" />
      <input id="password" value="password" />
      <button id="btnLogin"></button>
      <div id="loginStatus"></div>

      <div id="app" class="hidden">
        <select id="ledgerSelect"></select>
        <input id="months" value="3" />
        <button id="btnLoad"></button>

        <div id="kIncome"></div>
        <div id="kExpense"></div>
        <div id="kNet"></div>
        <div id="rangeInfo"></div>
        <div id="recs"></div>

        <canvas id="trendChart"></canvas>
        <canvas id="catChart"></canvas>

        <table id="arapTable"><tbody></tbody></table>
        <table id="merchantTable"><tbody></tbody></table>
      </div>
    </body>
  </html>`;
  return new JSDOM(html, { runScripts: "dangerously", resources: "usable" });
}

function loadAppJs(window) {
  const appPath = path.join(__dirname, "..", "dashboard.js");
  const code = fs.readFileSync(appPath, "utf-8");
  window.eval(code);
}

describe("frontend/dashboard.js", () => {
  test("fmt: null/undefined -> '-', numeric string -> 2 decimals, NaN preserved", () => {
    const dom = buildDom();
    const { window } = dom;

    // Mock minimal dependencies required by dashboard.js bootstrap
    window.fetch = jest.fn();
    window.Chart = jest.fn();

    loadAppJs(window);

    expect(window.fmt(null)).toBe("-");
    expect(window.fmt(undefined)).toBe("-");
    expect(window.fmt("1")).toBe("1.00");
    expect(window.fmt(2)).toBe("2.00");
    expect(window.fmt("abc")).toBe("abc");
  });

  test("api: trims baseUrl trailing slash and includes X-Auth-Token after login", async () => {
    const dom = buildDom();
    const { window } = dom;

    // 1st call: login
    // 2nd call: /ledgers/mine
    window.fetch = jest
      .fn()
      .mockResolvedValueOnce({
        json: async () => ({ success: true, data: { access_token: "TOKEN_123" } })
      })
      .mockResolvedValueOnce({
        json: async () => ({
          success: true,
          data: { items: [{ ledger_id: 1, name: "Demo" }] }
        })
      });

    // Chart mock
    window.Chart = jest.fn(function () {
      return { destroy: jest.fn() };
    });

    loadAppJs(window);

    await window.login();

    // Ensure baseUrl trailing slash was trimmed when composing URL
    expect(window.fetch.mock.calls[0][0]).toBe("http://localhost:8081/api/v1/auth/login");

    // After login, second request should include X-Auth-Token
    const secondHeaders = window.fetch.mock.calls[1][1].headers;
    expect(secondHeaders["X-Auth-Token"]).toBe("TOKEN_123");

    // Ledger select should now have one option
    const sel = window.document.getElementById("ledgerSelect");
    expect(sel.children.length).toBe(1);
    expect(sel.children[0].textContent).toContain("Demo");
  });

  test("renderTotals: supports snake_case and camelCase keys", () => {
    const dom = buildDom();
    const { window } = dom;

    window.fetch = jest.fn();
    window.Chart = jest.fn();

    loadAppJs(window);

    window.renderTotals({
      total_income: "1.5",
      total_expense: "2",
      net_balance: "-0.5",
      range_start: "2025-09-01T00:00:00",
      range_end: "2025-12-01T00:00:00"
    });

    expect(window.document.getElementById("kIncome").textContent).toBe("1.50");
    expect(window.document.getElementById("kExpense").textContent).toBe("2.00");
    expect(window.document.getElementById("kNet").textContent).toBe("-0.50");
    expect(window.document.getElementById("rangeInfo").textContent).toContain("Range:");
  });

  test("renderTrend: destroys previous chart before creating a new one", () => {
    const dom = buildDom();
    const { window } = dom;

    window.fetch = jest.fn();

    const destroy1 = jest.fn();
    const destroy2 = jest.fn();
    window.Chart = jest
      .fn()
      .mockImplementationOnce(() => ({ destroy: destroy1 }))
      .mockImplementationOnce(() => ({ destroy: destroy2 }));

    // Canvas getContext stub (jsdom does not implement canvas)
    window.HTMLCanvasElement.prototype.getContext = () => ({});

    loadAppJs(window);

    window.renderTrend({ trend: [{ period: "2025-09", income: 0, expense: 10 }] });
    window.renderTrend({ trend: [{ period: "2025-10", income: 1, expense: 9 }] });

    expect(destroy1).toHaveBeenCalledTimes(1);
    expect(window.Chart).toHaveBeenCalledTimes(2);
  });

  test("renderArAp: computes net=ar-ap and renders rows", () => {
    const dom = buildDom();
    const { window } = dom;

    window.fetch = jest.fn();
    window.Chart = jest.fn();

    loadAppJs(window);

    window.renderArAp({
      arap: [
        { user_name: "Alice", ar: 10, ap: 3 },
        { userName: "Bob", ar: 2, ap: 8 }
      ]
    });

    const rows = window.document.querySelectorAll("#arapTable tbody tr");
    expect(rows.length).toBe(2);

    const firstCells = rows[0].querySelectorAll("td");
    expect(firstCells[0].textContent).toBe("Alice");
    expect(firstCells[3].textContent).toBe("7.00"); // 10 - 3

    const secondCells = rows[1].querySelectorAll("td");
    expect(secondCells[0].textContent).toBe("Bob");
    expect(secondCells[3].textContent).toBe("-6.00"); // 2 - 8
  });
});
