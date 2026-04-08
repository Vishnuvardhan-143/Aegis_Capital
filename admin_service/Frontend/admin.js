const API_BASE = 'http://localhost:5065/api/admin';

// ─── SELECTION STATE ──────────────────────────────────────────────────────────
const selectedEmails = new Set();

function toggleSelectAll(masterCheckbox) {
    const checkboxes = document.querySelectorAll('.user-checkbox');
    checkboxes.forEach(cb => {
        cb.checked = masterCheckbox.checked;
        const email = cb.dataset.email;
        if (masterCheckbox.checked) {
            selectedEmails.add(email);
        } else {
            selectedEmails.delete(email);
        }
    });
    updateSelectionUI();
}

function toggleUserSelection(checkbox) {
    const email = checkbox.dataset.email;
    if (checkbox.checked) {
        selectedEmails.add(email);
    } else {
        selectedEmails.delete(email);
        // Uncheck "select all" if any individual is unchecked
        document.getElementById('selectAllUsers').checked = false;
    }
    updateSelectionUI();
}

function updateSelectionUI() {
    const count = selectedEmails.size;
    const selectedBtn = document.getElementById('applyInterestSelectedBtn');
    const countSpan = document.getElementById('selectedCount');
    if (count > 0) {
        selectedBtn.style.display = 'inline-flex';
        countSpan.textContent = count;
    } else {
        selectedBtn.style.display = 'none';
    }
}

// ─── SECTIONS HELPER ──────────────────────────────────────────────────────────
function showSection(sectionId) {
    const sections = ['usersSection', 'allAccountsSection', 'accountsSection', 'transactionsSection'];
    sections.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.classList.toggle('hidden', id !== sectionId);
    });
    // Update active nav link
    document.querySelectorAll('.nav-link').forEach(link => link.classList.remove('active'));
    if (sectionId === 'usersSection') document.getElementById('navUsers').classList.add('active');
    if (sectionId === 'allAccountsSection') document.getElementById('navAccounts').classList.add('active');
}

// ─── INIT ─────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    // Accept token from URL hash (cross-origin redirect from Auth frontend)
    const hash = window.location.hash;
    if (hash && hash.startsWith('#token=')) {
        const tokenFromUrl = decodeURIComponent(hash.substring(7));
        localStorage.setItem('jwt', tokenFromUrl);
        history.replaceState(null, '', window.location.pathname);
    }

    const token = localStorage.getItem('jwt');
    if (!token) {
        window.location.href = 'http://localhost:5173/login.html';
        return;
    }

    // Decode JWT to get admin identity
    const payload = JSON.parse(atob(token.split('.')[1]));
    const adminEmail = payload.sub;
    document.getElementById('adminEmail').textContent = adminEmail;

    loadDashboardData(adminEmail);
    loadAccountStats();

    // ── Navigation listeners ──────────────────────────────────────────────────
    document.getElementById('logoutBtn').addEventListener('click', (e) => {
        e.preventDefault();
        localStorage.removeItem('jwt');
        window.location.href = 'http://localhost:5173/login.html';
    });

    document.getElementById('navUsers').addEventListener('click', (e) => {
        e.preventDefault();
        showSection('usersSection');
    });

    document.getElementById('navAccounts').addEventListener('click', (e) => {
        e.preventDefault();
        loadAllAccountsOverview();
    });

    document.getElementById('backToUsersBtn').addEventListener('click', () => {
        showSection('usersSection');
    });

    document.getElementById('backToAccountsBtn').addEventListener('click', () => {
        // Figure out where we came from — if from "all accounts" overview, go back there
        const fromOverview = document.getElementById('backToAccountsBtn').dataset.fromOverview === 'true';
        if (fromOverview) {
            showSection('allAccountsSection');
        } else {
            showSection('accountsSection');
        }
    });
});

// ─── UTILITY ──────────────────────────────────────────────────────────────────
function showMessage(msg, type = 'success') {
    const box = document.getElementById('messageBox');
    box.textContent = msg;
    box.style.display = 'block';
    box.style.borderLeft = type === 'success' ? '5px solid var(--success)' : '5px solid var(--error)';
    setTimeout(() => { box.style.display = 'none'; }, 5000);
}

async function fetchWithAuth(url, options = {}) {
    const token = localStorage.getItem('jwt');
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };
    const response = await fetch(url, { ...options, headers });
    if (response.status === 401 || response.status === 403) {
        localStorage.removeItem('jwt');
        window.location.href = 'http://localhost:5173/login.html';
        throw new Error('Unauthorized');
    }
    return response;
}

// ─── DASHBOARD DATA ───────────────────────────────────────────────────────────
async function loadDashboardData(adminEmail) {
    try {
        const response = await fetchWithAuth(`${API_BASE}/users`);
        if (!response.ok) throw new Error('Failed to load system users');
        const users = await response.json();

        // Filter out the currently logged-in admin
        const filteredUsers = users.filter(user => user.email !== adminEmail);

        document.getElementById('statUsers').textContent = filteredUsers.length;

        const tbody = document.querySelector('#usersTable tbody');
        tbody.innerHTML = '';

        filteredUsers.forEach(user => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><input type="checkbox" class="user-checkbox" data-email="${user.email}" onclick="toggleUserSelection(this)" ${selectedEmails.has(user.email) ? 'checked' : ''}></td>
                <td>#${user.id}</td>
                <td>
                    <div style="font-weight:600">${user.name}</div>
                    <div style="font-size:12px; color:var(--text-secondary)">ID VERIFIED</div>
                </td>
                <td>${user.email}</td>
                <td>${user.panNo}</td>
                <td>
                    <span class="badge ${user.mfaEnabled ? 'badge-success' : 'badge-warning'}">
                        ${user.mfaEnabled ? 'ENFORCED' : 'PENDING'}
                    </span>
                </td>
                <td class="actions-cell">
                    <div style="display:flex; gap:10px;">
                        <button class="btn btn-primary" onclick="viewAccounts('${user.email}')">Portfolio</button>
                        <button class="btn btn-warning" onclick="transferAdmin('${user.email}')">Transfer Rights</button>
                        <button class="btn btn-danger" onclick="deleteUser(${user.id})">Terminate</button>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        showMessage(e.message, 'error');
    }
}

/** Fetch live account count + total system liquidity for the stat cards. */
async function loadAccountStats() {
    try {
        const response = await fetchWithAuth(`${API_BASE}/accounts/stats`);
        if (!response.ok) throw new Error('Could not fetch account stats');
        const stats = await response.json();
        document.getElementById('statAccounts').textContent = stats.count ?? 0;
        document.getElementById('statLiquidity').textContent =
            '$' + Number(stats.totalLiquidity ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    } catch (e) {
        document.getElementById('statAccounts').textContent = 'N/A';
        document.getElementById('statLiquidity').textContent = 'N/A';
        console.error('Account stats error:', e);
    }
}

// ─── USER ACTIONS ────────────────────────────────────────────────────────────
async function deleteUser(id) {
    if (!confirm('CRITICAL WARNING: Terminating this legal entity will result in IRREVERSIBLE loss of all associated bank accounts. Proceed?')) return;
    try {
        const response = await fetchWithAuth(`${API_BASE}/users/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('System failed to process termination request');
        showMessage('Entity termination protocols complete.');
        const adminEmail = document.getElementById('adminEmail').textContent;
        loadDashboardData(adminEmail);
        loadAccountStats(); // Refresh stats since accounts may have been deleted
    } catch (e) {
        showMessage(e.message, 'error');
    }
}

async function transferAdmin(targetEmail) {
    if (!confirm(`SECURITY CLEARANCE REQUIRED: You are about to transfer all MASTER ADMIN rights to ${targetEmail}. You will be demoted to a standard user and immediately logged out. Execute protocol?`)) return;
    try {
        const currentAdmin = document.getElementById('adminEmail').textContent;
        const response = await fetchWithAuth(
            `${API_BASE}/transfer-admin?currentAdminEmail=${currentAdmin}&newAdminEmail=${targetEmail}`,
            { method: 'POST' }
        );
        if (!response.ok) throw new Error('Administrative transfer protocol failed');
        showMessage('Succession complete. Revoking your access...', 'success');
        setTimeout(() => {
            localStorage.removeItem('jwt');
            window.location.href = 'http://localhost:5173/login.html';
        }, 2000);
    } catch (e) {
        showMessage(e.message, 'error');
    }
}

// ─── ACCOUNTS OVERVIEW (ALL ACCOUNTS) ────────────────────────────────────────
async function loadAllAccountsOverview() {
    try {
        const response = await fetchWithAuth(`${API_BASE}/accounts/all`);
        if (!response.ok) throw new Error('Failed to load accounts overview');
        const accounts = await response.json();

        const tbody = document.querySelector('#allAccountsTable tbody');
        tbody.innerHTML = '';

        if (accounts.length === 0) {
            tbody.innerHTML = `<tr><td colspan="8" style="text-align:center; padding: 40px; color:var(--text-secondary)">No bank accounts found in the system.</td></tr>`;
        } else {
            accounts.forEach(acc => {
                const createdDate = formatCreatedAt(acc.createdAt);
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${acc.id}</td>
                    <td>${acc.accno}</td>
                    <td>${acc.ifsccode}</td>
                    <td>${acc.bankname}</td>
                    <td style="font-size:13px; color:var(--text-secondary)">${acc.userEmail}</td>
                    <td><span style="color:var(--success); font-weight:700">$${Number(acc.balance).toLocaleString('en-US', { minimumFractionDigits: 2 })}</span></td>
                    <td style="font-size:13px; color:var(--text-secondary)">${createdDate}</td>
                    <td class="actions-cell">
                        <button class="btn btn-primary" onclick="viewTransactionsFromOverview(${acc.id}, '${acc.accno}')">View Ledger</button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        }

        showSection('allAccountsSection');
    } catch (e) {
        showMessage(e.message, 'error');
    }
}

function formatCreatedAt(createdAt) {
    if (!createdAt) return 'N/A';
    // Jackson serializes LocalDateTime as array [year, month, day, hour, minute, second, nano]
    if (Array.isArray(createdAt) && createdAt.length >= 3) {
        const [year, month, day, hour = 0, minute = 0] = createdAt;
        return new Date(year, month - 1, day, hour, minute).toLocaleDateString('en-US', {
            year: 'numeric', month: 'short', day: 'numeric'
        });
    }
    if (typeof createdAt === 'string') {
        return new Date(createdAt).toLocaleDateString('en-US', {
            year: 'numeric', month: 'short', day: 'numeric'
        });
    }
    return 'N/A';
}

// ─── PER-USER PORTFOLIO ────────────────────────────────────────────────────────
async function viewAccounts(email) {
    try {
        const response = await fetchWithAuth(`${API_BASE}/users/${email}/accounts`);
        if (!response.ok) throw new Error('Connectivity failure to account ledger');
        const accounts = await response.json();

        document.getElementById('currentAccountsUserEmail').textContent = email;
        const tbody = document.querySelector('#accountsTable tbody');
        tbody.innerHTML = '';

        if (accounts.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding: 40px; color:var(--text-secondary)">No active digital assets found for this entity.</td></tr>`;
        } else {
            accounts.forEach(acc => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${acc.id}</td>
                    <td>${acc.accno}</td>
                    <td>${acc.ifsccode}</td>
                    <td>${acc.bankname}</td>
                    <td><span style="color:var(--success); font-weight:700">$${Number(acc.balance).toLocaleString('en-US', { minimumFractionDigits: 2 })}</span></td>
                    <td class="actions-cell">
                        <div style="display:flex; gap:10px;">
                            <button class="btn btn-primary" onclick="viewTransactions(${acc.id}, '${acc.accno}')">View Ledger</button>
                            <button class="btn btn-danger" onclick="deleteAccount(${acc.id}, '${email}')">Liquidate</button>
                        </div>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        }

        showSection('accountsSection');
    } catch (e) {
        showMessage(e.message, 'error');
    }
}

// ─── TRANSACTION LEDGER ───────────────────────────────────────────────────────
async function viewTransactions(accountId, accNo) {
    // Mark that we came from the per-user accounts section
    document.getElementById('backToAccountsBtn').dataset.fromOverview = 'false';
    await _loadTransactions(accountId, accNo);
    showSection('transactionsSection');
}

async function viewTransactionsFromOverview(accountId, accNo) {
    // Mark that we came from the all-accounts overview
    document.getElementById('backToAccountsBtn').dataset.fromOverview = 'true';
    await _loadTransactions(accountId, accNo);
    showSection('transactionsSection');
}

async function _loadTransactions(accountId, accNo) {
    try {
        const response = await fetchWithAuth(`${API_BASE}/accounts/${accountId}/transactions`);
        if (!response.ok) throw new Error('System failed to retrieve historical ledger data');
        const transactions = await response.json();

        document.getElementById('currentTransactionsAccNo').textContent = `ACC: ${accNo}`;
        const tbody = document.querySelector('#transactionsTable tbody');
        tbody.innerHTML = '';

        if (transactions.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" style="text-align:center; padding: 40px; color:var(--text-secondary)">No recorded transactions found for this account.</td></tr>`;
        } else {
            transactions.forEach(t => {
                const isDebit = (t.type === 'WITHDRAW' || t.type === 'TRANSFER');
                const isInterest = (t.type === 'INTEREST');
                const symbol = isDebit ? '-' : '+';
                let amountClass = isDebit ? 'amount-debit' : 'amount-credit';
                if (isInterest) amountClass = 'amount-credit';

                let typeBadgeClass = 'badge-info';
                if (isInterest) typeBadgeClass = 'badge-success';
                if (isDebit) typeBadgeClass = 'badge-warning';

                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>#${t.id}</td>
                    <td><span class="badge ${typeBadgeClass}">${t.type}</span></td>
                    <td>${t.referenceAccountId ? `REF ACC #${t.referenceAccountId}` : isInterest ? '🏦 SYSTEM INTEREST' : 'DIRECT SYSTEM'}</td>
                    <td class="${amountClass}">${symbol}$${Number(t.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                    <td style="color:var(--text-secondary); font-size:13px">${new Date(t.createdAt).toLocaleString()}</td>
                `;
                tbody.appendChild(tr);
            });
        }
    } catch (e) {
        showMessage(e.message, 'error');
    }
}

async function deleteAccount(id, email) {
    if (!confirm('CONFIRMATION REQUIRED: Liquidating this asset will permanently erase transaction history. Execute?')) return;
    try {
        const response = await fetchWithAuth(`${API_BASE}/accounts/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Asset liquidation protocol failed');
        showMessage('Asset successfully liquidated.');
        viewAccounts(email);
        loadAccountStats(); // Refresh stat cards
    } catch (e) {
        showMessage(e.message, 'error');
    }
}

// ─── MONTHLY INTEREST ─────────────────────────────────────────────────────────

/** Apply interest to ALL accounts */
async function applyMonthlyInterest() {
    if (!confirm('⚠️ INTEREST APPLICATION\n\nThis will apply 0.5% × months-since-creation interest to ALL bank accounts and record INTEREST transactions in each account\'s ledger.\n\nProceed?')) return;
    await _executeInterestApply(null, document.getElementById('applyInterestAllBtn'));
}

/** Apply interest to SELECTED users' accounts only */
async function applyInterestToSelected() {
    if (selectedEmails.size === 0) {
        showMessage('No users selected. Check the boxes next to users first.', 'error');
        return;
    }
    const emailList = Array.from(selectedEmails);
    const names = emailList.join(', ');
    if (!confirm(`🎯 TARGETED INTEREST\n\nApply 0.5% monthly interest to accounts belonging to:\n${names}\n\nProceed?`)) return;
    await _executeInterestApply(emailList, document.getElementById('applyInterestSelectedBtn'));
}

/** Shared interest execution logic */
async function _executeInterestApply(emails, btn) {
    const resultMsg = document.getElementById('interestResultMsg');
    const originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = '⏳ Processing...';
    resultMsg.style.display = 'none';

    try {
        const body = emails ? JSON.stringify({ emails }) : null;
        const response = await fetchWithAuth(`${API_BASE}/apply-interest`, {
            method: 'POST',
            body: body
        });
        if (!response.ok) throw new Error('Interest application failed on server');
        const result = await response.json();

        const processed = result.accountsProcessed ?? 0;
        const total = result.totalInterestApplied ?? 0;

        resultMsg.style.display = 'inline';
        resultMsg.style.color = 'var(--success)';
        resultMsg.textContent = `✅ Interest applied to ${processed} account(s). Total disbursed: $${Number(total).toLocaleString('en-US', { minimumFractionDigits: 2 })}`;

        const scope = emails ? `${emails.length} user(s)` : 'all accounts';
        showMessage(`Monthly interest applied to ${scope}! ${processed} account(s) credited. Total: $${Number(total).toFixed(2)}`, 'success');
        loadAccountStats();
    } catch (e) {
        resultMsg.style.display = 'inline';
        resultMsg.style.color = 'var(--error)';
        resultMsg.textContent = `❌ ${e.message}`;
        showMessage(e.message, 'error');
    } finally {
        btn.disabled = false;
        btn.textContent = originalText;
    }
}
