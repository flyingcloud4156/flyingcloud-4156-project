// dashboard.js

const state = {
    baseUrl: '',
    accessToken: '',
    currentLedgerId: null,
    ledgers: [],
    members: [],
    transactions: [],
    charts: {
        incomeExpense: null,
        cat: null
    },
    analytics: null,
    budgetStatus: [],
    categories: [],
    txList: [],
    budgetEditorInited: false,
    newLedgerCategories: []
};

function computeBaseUrl() {
    const loc = window.location;
    const proto = loc.protocol === 'file:' ? 'http:' : loc.protocol;
    const host = loc.hostname || 'localhost';
    return `${proto}//${host}:8081`;
}

function ensureAuthOrRedirect() {
    const token = localStorage.getItem('ledger_access_token');

    if (!token) {
        window.location.href = 'index.html';
        return false;
    }
    state.baseUrl = computeBaseUrl().replace(/\/$/, '');
    state.accessToken = token;
    return true;
}

function getBaseUrl() {
    return state.baseUrl;
}

function getHeaders() {
    const headers = { 'Content-Type': 'application/json' };
    if (state.accessToken) headers['X-Auth-Token'] = state.accessToken;
    return headers;
}

async function apiGet(path) {
    const res = await fetch(getBaseUrl() + path, {
        method: 'GET',
        headers: getHeaders()
    });
    if (res.status === 401) {
        localStorage.removeItem('ledger_access_token');
        window.location.href = 'index.html';
        return;
    }
    if (!res.ok) {
        throw new Error(`GET ${path} failed: ${res.status}`);
    }
    return res.json();
}

async function apiPost(path, body) {
    const res = await fetch(getBaseUrl() + path, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify(body)
    });
    if (res.status === 401) {
        localStorage.removeItem('ledger_access_token');
        window.location.href = 'index.html';
        return;
    }
    if (!res.ok) {
        const text = await res.text();
        throw new Error(`POST ${path} failed: ${res.status} ${text}`);
    }
    return res.json();
}

async function apiDelete(path) {
    const res = await fetch(getBaseUrl() + path, {
        method: 'DELETE',
        headers: getHeaders()
    });
    if (res.status === 401) {
        localStorage.removeItem('ledger_access_token');
        window.location.href = 'index.html';
        return;
    }
    if (!res.ok && res.status !== 204) {
        throw new Error(`DELETE ${path} failed: ${res.status}`);
    }
}

function formatTxnAt(raw) {
    if (!raw) return null;

    if (typeof raw === 'string') return raw;

    // Jackson LocalDateTime → [year, month, day, hour, minute, second?]
    if (Array.isArray(raw)) {
        const [year, month, day, hour, minute, second] = raw;
        const mm = String(month).padStart(2, '0');
        const dd = String(day).padStart(2, '0');
        const hh = String(hour ?? 0).padStart(2, '0');
        const mi = String(minute ?? 0).padStart(2, '0');
        const ss = String(second ?? 0).padStart(2, '0');
        return `${year}-${mm}-${dd}T${hh}:${mi}:${ss}`;
    }

    return String(raw);
}

// ---------- 初始化 ----------

document.addEventListener('DOMContentLoaded', () => {
    if (!ensureAuthOrRedirect()) return;

    // 左侧栏
    document.getElementById('btnSidebarNewLedger').addEventListener('click', openLedgerModal);
    document.getElementById('btnReloadLedger').addEventListener('click', () => {
        if (state.currentLedgerId) loadLedgerAll(state.currentLedgerId);
    });

    document.getElementById('ledgerSelect').addEventListener('change', (e) => {
        const id = e.target.value;
        state.currentLedgerId = id ? Number(id) : null;
        if (state.currentLedgerId) loadLedgerAll(state.currentLedgerId);
    });

    // 交易弹窗
    document.getElementById('btnOpenTxModal').addEventListener('click', async () => {
        if (!state.currentLedgerId) {
            alert('Please select a ledger first.');
            return;
        }
        // await loadCategoriesForCurrentLedger();
        openTxModal();
    });
    document.getElementById('btnCloseTxModal').addEventListener('click', closeTxModal);
    document.getElementById('btnSubmitTx').addEventListener('click', submitTransaction);

    // Split method 切换
    const splitMethodEl = document.getElementById('txSplitMethod');
    if (splitMethodEl) {
        splitMethodEl.addEventListener('change', () => {
            renderSplitsUI();
        });
    }

    // 交易过滤
    document.getElementById('btnLoadTx').addEventListener('click', () => {
        if (state.currentLedgerId) loadTransactions(state.currentLedgerId);
    });

    document.getElementById('btnClear').addEventListener('click', () => {
        const txFromInput = document.getElementById('txFrom');
        const txToInput = document.getElementById('txTo');
        const txTypeFilter = document.getElementById('txTypeFilter');
        const btnLoadTx = document.getElementById('btnLoadTx');
        const txRangeInfo = document.getElementById('txRangeInfo');

        txFromInput.value = '';
        txToInput.value = '';
        txTypeFilter.value = '';

        if (txRangeInfo) {
            txRangeInfo.textContent = '';
        }

        btnLoadTx.click();
    });

    // Ledger 弹窗
    document.getElementById('btnCreateLedger').addEventListener('click', submitLedger);
    document.getElementById('btnCloseLedgerModal').addEventListener('click', closeLedgerModal);

    // Members 弹窗
    document.getElementById('btnManageMembers').addEventListener('click', openMemberModal);
    document.getElementById('btnCloseMemberModal').addEventListener('click', closeMemberModal);
    document.getElementById('btnAddMember').addEventListener('click', addMember);

    // Settlement 弹窗
    document.getElementById('btnDeleteLedger').addEventListener('click', openSettlementModal);
    document.getElementById('btnCloseSettlementModal').addEventListener('click', closeSettlementModal);

    // 交易详情弹窗（只读）
    const btnCloseTxDetail = document.getElementById('btnCloseTxDetailModal');
    if (btnCloseTxDetail) {
        btnCloseTxDetail.addEventListener('click', closeTxDetailModal);
    }

    const btnOpenSettlement = document.getElementById('btnOpenSettlement');
    if (btnOpenSettlement) {
        btnOpenSettlement.addEventListener('click', showSettlementModal);
    }

    document
        .getElementById('newLedgerCategoryInput')
        .addEventListener('keydown', handleCategoryInputKeydown);

    // 登出
    document.getElementById('btnLogout').addEventListener('click', () => {
        localStorage.removeItem('ledger_access_token');
        window.location.href = 'index.html';
    });

    loadCurrentUser();
    loadLedgers();
    initBudgetEditor();
    initSettlementSection();

});

// ---------- 用户信息 ----------

async function loadCurrentUser() {
    const el = document.getElementById('currentUser');
    try {
        const res = await apiGet('/api/v1/users/me');
        if (!res || !res.success) {
            el.textContent = 'Unknown user';
            return;
        }
        el.textContent = res.data.name || `User ${res.data.id}`;
    } catch (err) {
        console.error(err);
        el.textContent = 'Failed to load user';
    }
}

// ---------- Ledgers ----------

async function loadLedgers() {
    try {
        const res = await apiGet('/api/v1/ledgers/mine');
        if (!res || !res.success) throw new Error(res.message || 'load ledgers failed');

        // MyLedgersResponse.items: [{ ledger_id, name, ledger_type, base_currency, role }]
        const rawItems = (res.data && res.data.items) || [];
        state.ledgers = rawItems.map(ld => ({
            ledgerId: ld.ledger_id,
            name: ld.name,
            ledgerType: ld.ledger_type,
            baseCurrency: ld.base_currency,
            role: ld.role
        }));

        const select = document.getElementById('ledgerSelect');
        select.innerHTML = '';
        state.ledgers.forEach(ld => {
            const opt = document.createElement('option');
            opt.value = ld.ledgerId;
            opt.textContent = `${ld.name} (${ld.baseCurrency})`;
            select.appendChild(opt);
        });

        if (state.ledgers.length > 0) {
            state.currentLedgerId =
                state.currentLedgerId || state.ledgers[0].ledgerId;
            select.value = String(state.currentLedgerId);
            loadLedgerAll(state.currentLedgerId);
        } else {
            state.currentLedgerId = null;
            document.getElementById('ledgerMeta').textContent =
                'No ledgers yet. Use "Add new ledger" to create one.';
        }
    } catch (err) {
        console.error(err);
        document.getElementById('ledgerMeta').textContent =
            'Failed to load ledgers.';
    }
}

async function loadLedgerAll(ledgerId) {
    await Promise.all([
        loadLedgerMeta(ledgerId),
        loadMembers(ledgerId),
        loadTransactions(ledgerId)
    ]);
}

async function loadLedgerMeta(ledgerId) {
    try {
        const res = await apiGet(`/api/v1/ledgers/${ledgerId}`);
        if (!res || !res.success) throw new Error(res.message || 'load ledger failed');

        const d = res.data;
        console.log(d.categories)
        state.categories = (d.categories || []).map(c => ({
            id: c.id,
            name: c.name
        }));

        document.getElementById('ledgerMeta').textContent =
            `${d.ledger_type} • ${d.base_currency}` +
            (d.role ? ` • Role: ${d.role}` : '');

    } catch (err) {
        console.error(err);
        document.getElementById('ledgerMeta').textContent =
            'Failed to load ledger meta.';
    }
}


function renderCategorySelect() {
    const sel = document.getElementById('txCategorySelect');
    if (!sel) return;

    sel.innerHTML = '';

    // 默认选项
    const none = document.createElement('option');
    none.value = '';
    none.textContent = 'Uncategorized';
    sel.appendChild(none);

    // 加载 categories (id, name)
    state.categories.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c.id;
        opt.textContent = c.name;
        sel.appendChild(opt);
    });
}

async function submitLedger() {
    const msgEl = document.getElementById('ledgerModalMsg');
    msgEl.textContent = '';

    const name = document.getElementById('newLedgerName').value.trim();
    if (!name) {
        msgEl.textContent = 'Name is required.';
        return;
    }

    if (!state.newLedgerCategories || state.newLedgerCategories.length === 0) {
        msgEl.textContent = 'Please add at least one category.';
        return;
    }

    const body = {
        name: name,
        ledger_type: document.getElementById('newLedgerType').value,
        base_currency: document.getElementById('newLedgerCurrency').value.trim(),
        share_start_date: document.getElementById('newLedgerStartDate').value || null,
        categories: state.newLedgerCategories.map(catName => ({
            name: catName,
            kind: 'EXPENSE',
            is_active: true
        }))
    };

    try {
        const res = await apiPost('/api/v1/ledgers', body);
        if (!res || !res.success) throw new Error(res.message || 'create ledger failed');
        msgEl.textContent = 'Ledger created.';
        closeLedgerModal();
        loadLedgers();
    } catch (err) {
        console.error(err);
        msgEl.textContent = 'Create failed: ' + err.message;
    }
}


function handleCategoryInputKeydown(e) {
    if (e.key !== 'Enter') return;

    e.preventDefault();
    const input = e.target;
    const name = input.value.trim();
    if (!name) return;

    const exists = state.newLedgerCategories.some(
        c => c.toLowerCase() === name.toLowerCase()
    );
    if (exists) {
        input.value = '';
        return;
    }

    state.newLedgerCategories.push(name);
    input.value = '';
    renderCategoryChips();
}

function renderCategoryChips() {
    const container = document.getElementById('newLedgerCategoryChips');
    if (!container) return;
    container.innerHTML = '';

    state.newLedgerCategories.forEach((name, index) => {
        const chip = document.createElement('div');
        chip.className = 'category-chip';

        const label = document.createElement('span');
        label.className = 'category-chip-label';
        label.textContent = name;

        const btn = document.createElement('button');
        btn.className = 'category-chip-remove';
        btn.type = 'button';
        btn.textContent = '✕';
        btn.addEventListener('click', () => {
            state.newLedgerCategories.splice(index, 1);
            renderCategoryChips();
        });

        chip.appendChild(label);
        chip.appendChild(btn);
        container.appendChild(chip);
    });
}

function openLedgerModal() {
    document.getElementById('ledgerModalMsg').textContent = '';
    document.getElementById('ledgerModal').classList.remove('hidden');
    const today = new Date().toISOString().slice(0, 10);  // yyyy-mm-dd
    document.getElementById('newLedgerStartDate').value = today;

    state.newLedgerCategories = [];
    document.getElementById('newLedgerCategoryInput').value = '';
    renderCategoryChips();
}

function closeLedgerModal() {
    document.getElementById('ledgerModal').classList.add('hidden');
    state.newLedgerCategories = [];
    renderCategoryChips();
}

// ---------- Settlement ----------
async function openSettlementModal() {
    if (!state.currentLedgerId) {
        alert('Please select a ledger first.');
        return;
    }

    const modal = document.getElementById('settlementModal');
    const msgEl = document.getElementById('settlementModalMsg');
    const listEl = document.getElementById('settlementList');
    const advancedToggle = document.getElementById('settlementAdvancedToggle');
    const advancedPanel = document.getElementById('settlementAdvancedPanel');
    const actionsEl = document.querySelector('.settlement-actions');

    // 每次打开：默认回到简单模式（GET），隐藏高级设置和按钮
    if (advancedToggle) advancedToggle.checked = false;
    if (advancedPanel) {
        advancedPanel.classList.add('hidden');
        advancedPanel.classList.add('collapsed');
    }
    if (actionsEl) actionsEl.classList.add('hidden');

    msgEl.textContent = 'Loading settlement plan...';
    listEl.innerHTML = '';
    modal.classList.remove('hidden');

    try {
        const res = await apiGet(
            `/api/v1/ledgers/${state.currentLedgerId}/settlement-plan`
        );
        if (!res || !res.success) {
            throw new Error(res.message || 'load settlement plan failed');
        }

        const d = res.data || {};
        const transfers = d.transfers || [];
        const currency = d.currency || 'USD';

        if (!transfers.length) {
            msgEl.textContent = 'Everyone is settled. No debts to clear.';
            return;
        }

        const count =
            d.transfer_count != null ? d.transfer_count : transfers.length;
        msgEl.textContent = `Currency: ${currency} • Transfers: ${count}`;

        // 用统一的渲染函数（兼容 snake_case / camelCase）
        renderSettlementPlan(d);
    } catch (err) {
        console.error(err);
        msgEl.textContent = 'Failed to load settlement plan: ' + err.message;
    }
}

function closeSettlementModal() {
    document.getElementById('settlementModal').classList.add('hidden');
}

// =========================
// Settlement UI 初始化
// =========================

function initSettlementSection() {
    const modal = document.getElementById('settlementModal');
    if (!modal) return;

    const btnClose = document.getElementById('btnCloseSettlementModal');
    const btnGenerate = document.getElementById('btnGenerateSettlement');
    const advancedToggle = document.getElementById('settlementAdvancedToggle');
    const advancedPanel = document.getElementById('settlementAdvancedPanel');
    const advancedHeader = advancedPanel
        ? advancedPanel.querySelector('.settlement-advanced-header')
        : null;
    const actionsEl = document.querySelector('.settlement-actions');

    // 打开 modal 的按钮（改成你真正的 id）
    const btnOpen = document.getElementById('btnSettlementModal');
    if (btnOpen) {
        btnOpen.addEventListener('click', openSettlementModal);
    }

    // 关闭
    if (btnClose) btnClose.addEventListener('click', closeSettlementModal);
    const backdrop = modal.querySelector('.overlay-backdrop');
    if (backdrop) backdrop.addEventListener('click', closeSettlementModal);

    // 初始：toggle 关，高级区隐藏，按钮隐藏
    if (advancedToggle) advancedToggle.checked = false;
    if (advancedPanel) {
        advancedPanel.classList.add('hidden');
        advancedPanel.classList.add('collapsed');
    }
    if (actionsEl) actionsEl.classList.add('hidden');

    // toggle 切换：控制高级区 + 按钮；关掉时重新回到默认 GET 方案
    if (advancedToggle && advancedPanel) {
        advancedToggle.addEventListener('change', () => {
            const useAdvanced = advancedToggle.checked;

            if (useAdvanced) {
                // 高级模式：显示高级设置 + 按钮
                advancedPanel.classList.remove('hidden');
                advancedPanel.classList.remove('collapsed');
                if (actionsEl) actionsEl.classList.remove('hidden');

                const msgEl = document.getElementById('settlementModalMsg');
                if (msgEl) {
                    msgEl.textContent =
                        'Configure advanced options, then click "Generate settlement plan".';
                }
            } else {
                // 回到简单模式：隐藏高级设置 + 按钮，并重新加载默认 GET 方案
                advancedPanel.classList.add('hidden');
                advancedPanel.classList.add('collapsed');
                if (actionsEl) actionsEl.classList.add('hidden');
                // 重新用 GET 拉一次（不会重新弹窗，只是刷新内容）
                openSettlementModal();
            }
        });
    }

    // 高级设置 header 折叠/展开
    if (advancedHeader && advancedPanel) {
        advancedHeader.addEventListener('click', () => {
            if (advancedPanel.classList.contains('hidden')) return;
            advancedPanel.classList.toggle('collapsed');
        });
    }

    // 高级方案按钮：POST
    if (btnGenerate) {
        btnGenerate.addEventListener('click', handleGenerateAdvancedSettlement);
    }
}

// =========================
// 高级方案：POST + config
// =========================

async function handleGenerateAdvancedSettlement() {
    const ledgerId = state.currentLedgerId;
    const msgEl = document.getElementById('settlementModalMsg');
    const listEl = document.getElementById('settlementList');
    const advancedToggle = document.getElementById('settlementAdvancedToggle');

    if (!ledgerId) {
        if (msgEl) msgEl.textContent = 'Please select a ledger first.';
        return;
    }

    if (!advancedToggle || !advancedToggle.checked) {
        if (msgEl) {
            msgEl.textContent =
                'Turn on "Use advanced options" to generate with custom settings.';
        }
        return;
    }

    if (msgEl) msgEl.textContent = 'Generating advanced settlement plan...';
    if (listEl) listEl.innerHTML = '';

    try {
        const url = `${getBaseUrl()}/api/v1/ledgers/${ledgerId}/settlement-plan`;
        const config = buildSettlementConfigFromForm();

        const res = await fetch(url, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify(config),
        });
        if (!res.ok) {
            const errText = await res.text();
            throw new Error(`Server error (${res.status}): ${errText}`);
        }
        const json = await res.json();
        const plan = json.data || json;

        renderSettlementPlan(plan);

        if (!plan || !plan.transfers || plan.transfers.length === 0) {
            if (msgEl) {
                msgEl.textContent =
                    'No transfers needed under current advanced settings.';
            }
        } else if (msgEl) {
            msgEl.textContent = 'Advanced settlement plan generated.';
        }
    } catch (err) {
        console.error(err);
        if (msgEl) {
            msgEl.textContent =
                'Failed to generate advanced settlement plan: ' + err.message;
        }
    }
}


function buildSettlementConfigFromForm() {
    const rounding = document.getElementById('settlementRoundingStrategy')?.value;
    const maxTransferStr = document.getElementById('settlementMaxTransfer')?.value;
    const forceMinCostFlow =
        document.getElementById('settlementForceMinCostFlow')?.checked || false;
    const thresholdStr =
        document.getElementById('settlementMinCostFlowThreshold')?.value;

    return {
        rounding_strategy: rounding || 'ROUND_HALF_UP',
        max_transfer_amount: maxTransferStr ? Number(maxTransferStr) : null,
        force_min_cost_flow: forceMinCostFlow,
        min_cost_flow_threshold: thresholdStr ? Number(thresholdStr) : null,
        payment_channels: null,
        currency_rates: null,
    };

}


// =========================
// 渲染 SettlementPlanResponse
// =========================

function renderSettlementPlan(plan) {
    const listEl = document.getElementById('settlementList');
    if (!listEl) return;

    listEl.innerHTML = '';
    if (!plan || !Array.isArray(plan.transfers)) return;

    const transfers = plan.transfers || [];
    const currency = plan.currency || 'USD';

    transfers.forEach((t) => {
        const item = document.createElement('div');
        item.className = 'settlement-item';

        // 同时兼容 snake_case 和 camelCase
        const fromName =
            t.from_user_name ??
            t.fromUserName ??
            ('User ' + (t.from_user_id ?? t.fromUserId));
        const toName =
            t.to_user_name ??
            t.toUserName ??
            ('User ' + (t.to_user_id ?? t.toUserId));

        const rawAmount = t.amount;
        const amountNum = Number(rawAmount);
        const amountStr = Number.isNaN(amountNum)
            ? String(rawAmount)
            : amountNum.toFixed(2);

        item.innerHTML = `
      <span class="settlement-from">${fromName}</span>
      <span class="settlement-arrow">→</span>
      <span class="settlement-to">${toName}</span>
      <span class="settlement-amount">${amountStr} ${currency}</span>
    `;

        listEl.appendChild(item);
    });
}
// ---------- Members ----------

async function loadMembers(ledgerId) {
    try {
        const res = await apiGet(`/api/v1/ledgers/${ledgerId}/members`);
        if (!res || !res.success) throw new Error(res.message || 'load members failed');

        // ListLedgerMembersResponse.items: [{ user_id, name, role }]
        const rawItems = (res.data && res.data.items) || [];
        state.members = rawItems.map(m => ({
            userId: m.user_id,
            name: m.name,
            role: m.role
        }));

        renderMemberList();
    } catch (err) {
        console.error(err);
    }
}

function renderMemberList() {
    const list = document.getElementById('memberList');
    list.innerHTML = '';
    state.members.forEach(m => {
        const row = document.createElement('div');
        row.className = 'member-item';

        const left = document.createElement('div');
        left.className = 'member-name';
        left.innerHTML = `<span>${m.name + (' [User ' + m.userId + ']')}</span>
                      <span class="member-role">${m.role}</span>`;

        const btn = document.createElement('button');
        btn.textContent = '✕';
        btn.className = 'btn-icon';
        btn.addEventListener('click', () => removeMember(m.userId));

        row.appendChild(left);
        row.appendChild(btn);
        list.appendChild(row);
    });
}

function openMemberModal() {
    if (!state.currentLedgerId) return;
    document.getElementById('memberModalMsg').textContent = '';
    document.getElementById('memberModal').classList.remove('hidden');
}

function closeMemberModal() {
    document.getElementById('memberModal').classList.add('hidden');
}

async function addMember() {
    const email = document.getElementById('memberEmail').value.trim();
    const msgEl = document.getElementById('memberModalMsg');
    msgEl.textContent = '';
    if (!email) {
        msgEl.textContent = 'Email is required.';
        return;
    }
    try {
        const lookup = await apiGet(`/api/v1/user-lookup?email=${encodeURIComponent(email)}`);
        if (!lookup || !lookup.success || !lookup.data.user_id) {
            msgEl.textContent = 'User not found.';
            return;
        }
        const userId = lookup.data.user_id;

        const body = { user_id: userId, role: 'EDITOR' };
        const res = await apiPost(`/api/v1/ledgers/${state.currentLedgerId}/members`, body);
        if (!res || !res.success) throw new Error(res.message || 'add member failed');
        msgEl.textContent = 'Member added.';
        document.getElementById('memberEmail').value = '';
        loadMembers(state.currentLedgerId);
    } catch (err) {
        console.error(err);
        msgEl.textContent = 'Failed: ' + err.message;
    }
}

async function removeMember(userId) {
    if (!confirm('Remove this member from the ledger?')) return;
    try {
        await apiDelete(`/api/v1/ledgers/${state.currentLedgerId}/members/${userId}`);
        loadMembers(state.currentLedgerId);
    } catch (err) {
        alert('Remove failed: ' + err.message);
    }
}

// ---------- Transactions ----------
async function loadTransactions(ledgerId) {
    const params = new URLSearchParams();
    const from = document.getElementById('txFrom').value;
    const to = document.getElementById('txTo').value;
    const type = document.getElementById('txTypeFilter').value;

    params.set('page', '1');
    params.set('size', '200');
    if (from) params.set('from', from + 'T00:00:00');
    if (to) params.set('to', to + 'T23:59:59');
    if (type) params.set('type', type);

    try {
        const res = await apiGet(
            `/api/v1/ledgers/${ledgerId}/transactions?` + params.toString()
        );
        if (!res || !res.success) throw new Error(res.message || 'load tx failed');

        const rawItems = (res.data && res.data.items) || [];

        // 按你现在真实返回的字段来 map，其他字段先用 || 保证 undefined 也不会炸
        state.transactions = rawItems.map(tx => ({
            raw: tx,                                    // 以后如果要看原始内容还可以用
            transactionId: tx.transaction_id,
            txnAt: formatTxnAt(tx.txn_at),
            type: tx.type,
            currency: tx.currency,
            amountTotal: Number(tx.amount_total),
            payerId: tx.payer_id,
            categoryId: tx.category_id || null,        // 以后后端加了 category 也不用再改前端
            createdBy: tx.created_by,
            note: tx.note || '',
        }));

        state.txList = state.transactions;

        renderTransactions();
        await refreshCharts();
    } catch (err) {
        console.error(err);
    }
}


function renderTransactions() {
    const tbody = document.querySelector('#txTable tbody');
    const empty = document.getElementById('txEmpty');
    tbody.innerHTML = '';

    if (!state.transactions.length) {
        empty.classList.remove('hidden');
        return;
    }
    empty.classList.add('hidden');

    state.transactions.forEach(tx => {
        const tr = document.createElement('tr');
        const dt = tx.txnAt ? tx.txnAt.substring(0, 10) : '';
        const amountStr = Number(tx.amountTotal).toFixed(2);

        tr.innerHTML = `
            <td>${dt}</td>
            <td>${tx.type}</td>
            <td>${tx.note || ''}</td>
            <td class="right">${amountStr} ${tx.currency}</td>
            <td class="right">
                <button class="secondary btn-view-tx" data-id="${tx.transactionId}">View</button>
                <button class="btn-icon btn-delete-tx" data-id="${tx.transactionId}">✕</button>
            </td>
        `;
        tbody.appendChild(tr);
    });

    attachTxRowEvents();
}



async function deleteTransaction(txId) {
    if (!confirm('Delete this transaction?')) return;
    try {
        await apiDelete(`/api/v1/ledgers/${state.currentLedgerId}/transactions/${txId}`);
        loadTransactions(state.currentLedgerId);
    } catch (err) {
        alert('Delete failed: ' + err.message);
    }
}

// ---------- Transaction Modals ----------

function openTxModal() {
    if (!state.currentLedgerId) {
        alert('Please select a ledger first.');
        return;
    }
    document.getElementById('txModalMsg').textContent = '';
    document.getElementById('txAmount').value = '';
    document.getElementById('txNote').value = '';
    document.getElementById('txType').value = 'EXPENSE';
    document.getElementById('txCurrency').value = 'USD';

    const now = new Date();
    const iso = now.toISOString().slice(0, 16);
    document.getElementById('txDatetime').value = iso;

    // payer 下拉
    const payerSelect = document.getElementById('txPayerSelect');
    payerSelect.innerHTML = '';
    state.members.forEach(m => {
        const opt = document.createElement('option');
        opt.value = m.userId;
        opt.textContent = m.name || ('User ' + m.userId);
        payerSelect.appendChild(opt);
    });

    // split method 默认 EQUAL
    const splitMethodEl = document.getElementById('txSplitMethod');
    if (splitMethodEl) {
        splitMethodEl.value = 'EQUAL';
    }
    renderSplitsUI();
    renderCategorySelect();

    document.getElementById('txModal').classList.remove('hidden');
}

function closeTxModal() {
    document.getElementById('txModal').classList.add('hidden');
}

async function submitTransaction() {
    const msgEl = document.getElementById('txModalMsg');
    msgEl.textContent = '';

    const amount = Number(document.getElementById('txAmount').value || '0');
    if (!amount || amount <= 0) {
        msgEl.textContent = 'Amount must be > 0.';
        return;
    }

    const body = {
        type: document.getElementById('txType').value,
        amount_total: amount,
        currency: document.getElementById('txCurrency').value.trim() || 'USD',
        txn_at: document.getElementById('txDatetime').value,
        payer_id: Number(document.getElementById('txPayerSelect').value),
        note: document.getElementById('txNote').value.trim(),
        category_id: document.getElementById('txCategorySelect').value.trim(),
        rounding_strategy: 'ROUND_HALF_UP',
        tail_allocation: 'PAYER',
        splits: []
    };

    const catVal = document.getElementById('txCategorySelect').value;
    if (catVal) {
        body.category_id = Number(catVal);
    }

    // 使用新的 split payload 生成逻辑
    body.splits = buildSplitsPayload(amount);

    try {
        const res = await apiPost(`/api/v1/ledgers/${state.currentLedgerId}/transactions`, body);
        if (!res || !res.success) throw new Error(res.message || 'create tx failed');
        msgEl.textContent = 'Created.';
        closeTxModal();
        loadTransactions(state.currentLedgerId);
    } catch (err) {
        console.error(err);
        msgEl.textContent = 'Failed: ' + err.message;
    }
}
function attachTxRowEvents() {
    document.querySelectorAll('.btn-view-tx').forEach(btn => {
        btn.addEventListener('click', () => {
            const id = Number(btn.dataset.id);
            openTxDetailFromServer(id);
        });
    });

    document.querySelectorAll('.btn-delete-tx').forEach(btn => {
        btn.addEventListener('click', () => {
            const id = Number(btn.dataset.id);
            deleteTransaction(id);
        });
    });
}

// ---------- Splits UI & Payload ----------

function renderSplitsUI() {
    const container = document.getElementById('splitList');
    if (!container) return;

    const methodEl = document.getElementById('txSplitMethod');
    const method = methodEl ? methodEl.value : 'EQUAL';

    container.innerHTML = '';

    (state.members || []).forEach(member => {
        const row = document.createElement('div');
        row.className = 'split-row';
        row.dataset.userId = member.userId; // 从 state.members 映射来的 userId

        const nameEl = document.createElement('div');
        nameEl.className = 'split-name';
        nameEl.textContent = member.name || ('User ' + member.userId);

        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.className = 'split-include';
        checkbox.checked = true;

        if (method === 'EQUAL') {
            const methodElCell = document.createElement('div');
            methodElCell.className = 'split-method-cell';
            methodElCell.textContent = 'EQUAL';
            row.appendChild(nameEl);
            row.appendChild(methodElCell);
            row.appendChild(checkbox);
        } else if (method === 'EXACT') {
            const input = document.createElement('input');
            input.type = 'number';
            input.step = '0.01';
            input.className = 'split-exact-amount';
            input.placeholder = 'Amount';
            row.appendChild(nameEl);
            row.appendChild(input);
            row.appendChild(checkbox);
        } else if (method === 'PERCENT') {
            const input = document.createElement('input');
            input.type = 'number';
            input.step = '0.01';
            input.className = 'split-percent';
            input.placeholder = '%';
            row.appendChild(nameEl);
            row.appendChild(input);
            row.appendChild(checkbox);
        } else if (method === 'WEIGHT') {
            const input = document.createElement('input');
            input.type = 'number';
            input.step = '0.01';
            input.className = 'split-weight';
            input.placeholder = 'Weight';
            row.appendChild(nameEl);
            row.appendChild(input);
            row.appendChild(checkbox);
        }

        container.appendChild(row);
    });
}

/**
 * 生成 SplitItem 列表
 * 后端预期结构（根据你原来的 equal 代码）：
 *   { user_id, split_method, share_value, included }
 *
 * 对于不同 split_method：
 *   EQUAL   → share_value = 0（后端按人数平均）
 *   EXACT   → share_value = 金额
 *   PERCENT → share_value = 百分比
 *   WEIGHT  → share_value = 权重
 */
function buildSplitsPayload(totalAmount) {
    const methodEl = document.getElementById('txSplitMethod');
    const method = methodEl ? methodEl.value : 'EQUAL';

    const rows = document.querySelectorAll('#splitList .split-row');
    const splits = [];

    if (!rows || rows.length === 0) {
        return splits;
    }

    const includedRows = Array.from(rows).filter(row =>
        row.querySelector('.split-include')?.checked
    );

    if (includedRows.length === 0) {
        return splits;
    }

    if (method === 'EQUAL') {
        includedRows.forEach(row => {
            const userId = Number(row.dataset.userId);
            splits.push({
                user_id: userId,
                split_method: 'EQUAL',
                share_value: 0,
                included: true
            });
        });
    } else if (method === 'EXACT') {
        includedRows.forEach(row => {
            const userId = Number(row.dataset.userId);
            const input = row.querySelector('.split-exact-amount');
            const val = Number(input.value || 0);
            if (!val) return;

            splits.push({
                user_id: userId,
                split_method: 'EXACT',
                share_value: Number(val.toFixed(2)),
                included: true
            });
        });
    } else if (method === 'PERCENT') {
        includedRows.forEach(row => {
            const userId = Number(row.dataset.userId);
            const input = row.querySelector('.split-percent');
            const percent = Number(input.value || 0);
            if (!percent) return;

            splits.push({
                user_id: userId,
                split_method: 'PERCENT',
                share_value: Number(percent.toFixed(2)),
                included: true
            });
        });
    } else if (method === 'WEIGHT') {
        includedRows.forEach(row => {
            const userId = Number(row.dataset.userId);
            const input = row.querySelector('.split-weight');
            const weight = Number(input.value || 0);
            if (!weight) return;

            splits.push({
                user_id: userId,
                split_method: 'WEIGHT',
                share_value: Number(weight.toFixed(2)),
                included: true
            });
        });
    }

    return splits;
}

// ---------- Transaction detail (readonly) ----------
async function openTxDetailFromServer(txId) {
    if (!state.currentLedgerId) return;

    try {
        const res = await apiGet(
            `/api/v1/ledgers/${state.currentLedgerId}/transactions/${txId}`
        );
        if (!res || !res.success) {
            throw new Error(res?.message || 'load tx detail failed');
        }

        const d = res.data || {};

        // 把后端 TransactionResponse（snake_case）转换成前端 detail 结构
        const detailTx = {
            transactionId: d.transaction_id,
            ledgerId: d.ledger_id,
            type: d.type,
            currency: d.currency,
            amountTotal: Number(d.amount_total || 0),
            txnAt: formatTxnAt(d.txn_at),
            payerId: d.payer_id,
            categoryId: d.category_id,
            categoryName: (state.categories.find(c => c.id === d.category_id)?.name) || 'Uncategorized',
            note: d.note || '',
            roundingStrategy: d.rounding_strategy,
            tailAllocation: d.tail_allocation,
            splits: d.splits || [],
            edgesPreview: d.edges_preview || []
        };

        openTxDetailModal(detailTx);
    } catch (err) {
        console.error(err);
        alert('Failed to load transaction details: ' + err.message);
    }
}

function openTxDetailModal(tx) {
    const modal = document.getElementById('txDetailModal');
    if (!modal) return;

    document.getElementById('txDetailType').textContent = tx.type;
    document.getElementById('txDetailAmount').textContent =
        `${Number(tx.amountTotal).toFixed(2)} ${tx.currency}`;
    document.getElementById('txDetailCurrency').textContent = tx.currency;
    document.getElementById('txDetailDatetime').textContent = tx.txnAt || '';
    document.getElementById('txDetailPayer').textContent =
        tx.payerName || (tx.payerId ? `User ${tx.payerId}` : '-');
    document.getElementById('txDetailCategory').textContent =
        tx.categoryName || 'Uncategorized';
    document.getElementById('txDetailNote').textContent = tx.note || '';

    const splitsContainer = document.getElementById('txDetailSplits');
    splitsContainer.innerHTML = '';

    (tx.splits || []).forEach(s => {
        const wrapper = document.createElement('div');
        wrapper.className = 'split-row-extended';

        const userName =
            s.user_name ||
            s.userName ||
            (s.user_id ? `User ${s.user_id}` : 'Member');

        const amount = s.computed_amount != null
            ? Number(s.computed_amount).toFixed(2)
            : (s.amount != null ? Number(s.amount).toFixed(2) : '0');

        const method = s.split_method || s.method || 'EXACT';
        const value = s.share_value;

        // 主行：User – Amount USD
        const main = document.createElement('div');
        main.className = 'split-main';
        main.textContent = `${userName} – ${amount} ${tx.currency}`;

        // 副行：EXACT • value: 25
        const sub = document.createElement('div');
        sub.className = 'split-sub muted';

        const valueLabel =
            method === 'PERCENT' ? `${value}%`
                : method === 'SHARE' ? `${value} shares`
                    : value; // EXACT 用数字

        sub.textContent = `${method} • value: ${valueLabel}`;

        wrapper.appendChild(main);
        wrapper.appendChild(sub);
        splitsContainer.appendChild(wrapper);
    });


    modal.classList.remove('hidden');
}

function closeTxDetailModal() {
    const modal = document.getElementById('txDetailModal');
    if (!modal) return;
    modal.classList.add('hidden');
}

// ---------- Charts & Alert ----------

async function loadAnalyticsOverview() {
    if (!state.currentLedgerId) return;

    const res = await apiGet(`/api/v1/ledgers/${state.currentLedgerId}/analytics/overview?months=3`);
    if (!res || !res.success) {
        throw new Error(res?.message || 'load analytics failed');
    }

    const d = res.data || {};

    state.analytics = {
        currency: d.currency || 'USD',
        rangeStart: d.range_start ? formatTxnAt(d.range_start) : null,
        rangeEnd: d.range_end ? formatTxnAt(d.range_end) : null,
        totalIncome: d.total_income != null ? Number(d.total_income) : 0,
        totalExpense: d.total_expense != null ? Number(d.total_expense) : 0,
        netBalance: d.net_balance != null ? Number(d.net_balance) : 0,
        trend: (d.trend || []).map(p => ({
            period: p.period,
            income: p.income != null ? Number(p.income) : 0,
            expense: p.expense != null ? Number(p.expense) : 0
        })),
        byCategory: (d.by_category || []).map(c => ({
            categoryId: c.category_id,
            categoryName: c.category_name,
            amount: c.amount != null ? Number(c.amount) : 0,
            ratio: c.ratio != null ? Number(c.ratio) : 0
        })),
        arap: (d.arap || []).map(u => ({
            userId: u.user_id,
            userName: u.user_name,
            ar: u.ar != null ? Number(u.ar) : 0,
            ap: u.ap != null ? Number(u.ap) : 0
        })),
        topMerchants: (d.top_merchants || []).map(m => ({
            label: m.label,
            amount: m.amount != null ? Number(m.amount) : 0
        })),
        recommendations: d.recommendations || []
    };
}
function getCurrentYearMonthFromFilter() {
    let year, month;
    const fromStr = document.getElementById('txFrom')?.value;

    if (fromStr) {
        const d = new Date(fromStr);
        if (!isNaN(d.getTime())) {
            year = d.getFullYear();
            month = d.getMonth() + 1;
        }
    }

    if (!year || !month) {
        const now = new Date();
        year = now.getFullYear();
        month = now.getMonth() + 1;
    }

    // 顺便存到 state 里，别处也可以用
    state.currentBudgetYear = year;
    state.currentBudgetMonth = month;

    return { year, month };
}

async function loadBudgetStatusForCurrentMonth() {
    if (!state.currentLedgerId) return;

    const { year, month } = getCurrentYearMonthFromFilter();

    const res = await apiGet(
        `/api/v1/ledgers/${state.currentLedgerId}/budgets/status?year=${year}&month=${month}`
    );
    if (!res || !res.success) {
        throw new Error(res?.message || 'load budget status failed');
    }

    const items = (res.data && res.data.items) || [];
    state.budgetStatus = items.map(it => ({
        budgetId: it.budget_id,
        categoryId: it.category_id,
        categoryName: it.category_name,
        limitAmount: it.limit_amount != null ? Number(it.limit_amount) : 0,
        spentAmount: it.spent_amount != null ? Number(it.spent_amount) : 0,
        ratio: it.ratio != null ? Number(it.ratio) : 0,
        status: it.status
    }));
}

async function setLedgerBudgetForCurrentMonth(limitAmount) {
    if (!state.currentLedgerId) {
        throw new Error('No current ledger');
    }

    const { year, month } = getCurrentYearMonthFromFilter();

    const body = {
        // ledger_id 在 path 里，不用放 body
        category_id: null,      // null = 整个 ledger 的 budget
        year: year,
        month: month,
        limit_amount: limitAmount
    };

    const res = await apiPost(
        `/api/v1/ledgers/${state.currentLedgerId}/budgets`,
        body
    );

    if (!res || !res.success) {
        throw new Error(res?.message || 'set budget failed');
    }
}

function initBudgetEditor() {
    const editBtn = document.getElementById('editBudgetBtn');
    const form = document.getElementById('budgetForm');
    const cancelBtn = document.getElementById('cancelBudgetBtn');
    const input = document.getElementById('budgetInput');
    const budgetText = document.getElementById('budgetText');

    if (!editBtn || !form || !cancelBtn || !input || !budgetText) return;

    editBtn.addEventListener('click', () => {
        // 取当前 ledger 级别的 budget（categoryId === null）
        const ledgerBudget = (state.budgetStatus || []).find(
            b => b.categoryId == null
        );
        if (ledgerBudget) {
            input.value = ledgerBudget.limitAmount.toFixed(2);
        } else {
            input.value = '';
        }

        budgetText.classList.add('hidden');
        editBtn.classList.add('hidden');
        form.classList.remove('hidden');
        input.focus();
    });

    cancelBtn.addEventListener('click', () => {
        form.classList.add('hidden');
        budgetText.classList.remove('hidden');
        editBtn.classList.remove('hidden');
    });

    form.addEventListener('submit', async e => {
        e.preventDefault();
        const val = Number(input.value);
        if (isNaN(val) || val <= 0) {
            alert('Please enter a positive budget amount.');
            return;
        }

        try {
            await setLedgerBudgetForCurrentMonth(val);
            // 重新加载数据并刷新图表 + banner
            await refreshCharts();
        } catch (err) {
            console.error('Failed to set budget:', err);
            alert(err.message || 'Set budget failed');
        } finally {
            form.classList.add('hidden');
            budgetText.classList.remove('hidden');
            editBtn.classList.remove('hidden');
        }
    });
}

async function refreshCharts() {
    try {
        await Promise.all([
            loadAnalyticsOverview(),
            loadBudgetStatusForCurrentMonth()
        ]);
    } catch (err) {
        console.error('Failed to load analytics/budgets:', err);
    }

    const analytics = state.analytics;
    const budgetItems = state.budgetStatus || [];

    const ctx1 = document.getElementById('incomeExpenseChart');
    const ctx2 = document.getElementById('catChart');
    const alertText = document.getElementById('alertText');
    const budgetValueEl = document.getElementById('budgetValue');

    // 1. 收入/支出趋势折线图
    let labels = [];
    let incomeData = [];
    let expenseData = [];
    let totalIncome = 0;
    let totalExpense = 0;

    if (analytics && Array.isArray(analytics.trend)) {
        labels = analytics.trend.map(p => p.period);
        incomeData = analytics.trend.map(p => {
            const v = p.income || 0;
            totalIncome += v;
            return v;
        });
        expenseData = analytics.trend.map(p => {
            const v = p.expense || 0;
            totalExpense += v;
            return v;
        });

        if (analytics.totalIncome != null) {
            totalIncome = analytics.totalIncome;
        }
        if (analytics.totalExpense != null) {
            totalExpense = analytics.totalExpense;
        }
    }

    if (state.charts.incomeExpense) state.charts.incomeExpense.destroy();
    state.charts.incomeExpense = new Chart(ctx1, {
        type: 'line',
        data: {
            labels,
            datasets: [
                { label: 'Income', data: incomeData },
                { label: 'Expense', data: expenseData }
            ]
        },
        options: {
            responsive: true,
            plugins: {
                legend: { labels: { color: '#e9ecf1' } }
            },
            scales: {
                x: { ticks: { color: '#b8c0dd' }, grid: { display: false } },
                y: { ticks: { color: '#b8c0dd' }, grid: { color: '#1f2a52' } }
            }
        }
    });

    // 2. 类别饼图
    let catLabels = [];
    let catValues = [];

    if (analytics && Array.isArray(analytics.byCategory)) {
        catLabels = analytics.byCategory.map(c => c.categoryName || 'Other');
        catValues = analytics.byCategory.map(c => c.amount != null ? c.amount : 0);
    }

    if (state.charts.cat) state.charts.cat.destroy();
    state.charts.cat = new Chart(ctx2, {
        type: 'pie',
        data: {
            labels: catLabels,
            datasets: [{ data: catValues }]
        },
        options: {
            plugins: {
                legend: { labels: { color: '#e9ecf1' } }
            }
        }
    });

    // 3. Alert 提示
    let alertMsg = 'No alerts. Your expenses do not exceed income in this period.';

    const exceeded = budgetItems.filter(b => b.status === 'EXCEEDED');
    const nearLimit = budgetItems.filter(b => b.status === 'NEAR_LIMIT');

    if (exceeded.length > 0) {
        const names = exceeded.map(b => b.categoryName || 'Unnamed').join(', ');
        alertMsg = `BUDGET_EXCEEDED: Categories over budget: ${names}.`;
    } else if (nearLimit.length > 0) {
        const names = nearLimit.map(b => b.categoryName || 'Unnamed').join(', ');
        alertMsg = `BUDGET_NEAR_LIMIT: Categories near limit: ${names}.`;
    } else if (totalExpense > totalIncome && totalExpense > 0) {
        alertMsg =
            `SPEND_TOO_HIGH: Expenses (${totalExpense.toFixed(2)}) `
            + `are greater than income (${totalIncome.toFixed(2)}).`;
    }

    alertText.textContent = alertMsg;

    // 4. Budget banner
    if (budgetItems.length > 0) {
        // ledger 级预算（categoryId == null）
        const ledgerBudget = budgetItems.find(b => b.categoryId == null);

        if (ledgerBudget && ledgerBudget.limitAmount > 0) {
            const used = ledgerBudget.spentAmount || totalExpense || 0;
            const limit = ledgerBudget.limitAmount;
            const ratio = ledgerBudget.ratio || (limit > 0 ? used / limit : 0);
            const pct = (ratio * 100).toFixed(1);

            // 展示： 920.00 / 1000.00 (92.0% used, NEAR_LIMIT)
            budgetValueEl.textContent =
                `${used.toFixed(2)} / ${limit.toFixed(2)} \n `
                + `(${pct}% used [Expense/Budget])`;
        } else {
            // 没有 ledger 级预算，就做个 summary
            const summary = budgetItems.slice(0, 3).map(b => {
                const used = b.spentAmount.toFixed(2);
                const limit = b.limitAmount.toFixed(2);
                const pct = b.limitAmount > 0
                    ? ((b.spentAmount / b.limitAmount) * 100).toFixed(1)
                    : '0.0';
                return `${b.categoryName || 'Ledger'}: ${used}/${limit} (${pct}% used, ${b.status})`;
            }).join(' | ');

            const more = budgetItems.length > 3
                ? ` (+${budgetItems.length - 3} more)`
                : '';

            budgetValueEl.textContent = summary + more;
        }
    } else {
        budgetValueEl.textContent =
            totalExpense
                ? `${totalExpense.toFixed(2)} total expense (no budgets set)`
                : 'No expense yet';
    }



}
