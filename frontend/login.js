// login.js

function getBaseUrl() {
    return document.getElementById('baseUrl').value.trim().replace(/\/$/, '');
}

async function callApi(path, body) {
    const res = await fetch(getBaseUrl() + path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    const data = await res.json().catch(() => ({}));
    return { ok: res.ok, data };
}

async function handleLogin() {
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value.trim();
    const statusEl = document.getElementById('loginStatus');
    statusEl.textContent = '';

    if (!email || !password) {
        statusEl.textContent = 'Email and password are required.';
        return;
    }

    try {
        const { ok, data: resp } = await callApi('/api/v1/auth/login', { email, password });

        if (!ok) {
            statusEl.textContent = (resp && resp.message) || 'Login failed.';
            return;
        }

        if (resp && 'success' in resp && !resp.success) {
            statusEl.textContent = resp.message || 'Login failed.';
            return;
        }

        const data = resp && resp.data ? resp.data : resp;

        const token =
            (data && data.access_token) ||    // ⭐ 正确字段（下划线）
            (data && data.accessToken) ||     // 兼容驼峰
            (data && data.token) ||
            data;

        if (!token) {
            statusEl.textContent = 'Login failed: no accessToken returned.';
            return;
        }

        localStorage.setItem('ledger_base_url', getBaseUrl());
        localStorage.setItem('ledger_access_token', token);

        statusEl.textContent = 'Login success. Redirecting...';
        window.location.href = 'dashboard.html';

    } catch (err) {
        console.error(err);
        statusEl.textContent = 'Login error: ' + err.message;
    }
}



async function handleRegister() {
    const name = document.getElementById('name').value.trim();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value.trim();
    const statusEl = document.getElementById('loginStatus');
    statusEl.textContent = '';

    if (!email || !password || !name) {
        statusEl.textContent = 'Name, email and password are required for registration.';
        return;
    }

    try {
        const { ok, data } = await callApi('/api/v1/auth/register', { name, email, password });
        if (!ok || !data.success) {
            statusEl.textContent = data.message || 'Register failed.';
            return;
        }
        statusEl.textContent = 'Register success. Logging in...';
        // 注册成功后自动登录
        await handleLogin();
    } catch (err) {
        console.error(err);
        statusEl.textContent = 'Register error: ' + err.message;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    // 恢复 baseUrl
    const storedBase = localStorage.getItem('ledger_base_url');
    if (storedBase) {
        document.getElementById('baseUrl').value = storedBase;
    }

    document.getElementById('btnLogin').addEventListener('click', handleLogin);
    document.getElementById('btnRegister').addEventListener('click', handleRegister);
});
