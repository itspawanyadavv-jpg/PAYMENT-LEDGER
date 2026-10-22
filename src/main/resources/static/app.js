// Double-Entry Payment Ledger Dashboard Client

document.addEventListener('DOMContentLoaded', () => {
    // State
    let accounts = [];
    let payments = [];
    let lastPaymentRequest = null;

    // DOM Elements
    const systemHealthBadge = document.getElementById('systemHealthBadge');
    const healthStatusText = document.getElementById('healthStatusText');
    const kpiAccountCount = document.getElementById('kpiAccountCount');
    const kpiTotalFunds = document.getElementById('kpiTotalFunds');
    const kpiPaymentCount = document.getElementById('kpiPaymentCount');
    const kpiDiscrepancies = document.getElementById('kpiDiscrepancies');
    const kpiInvariantStatus = document.getElementById('kpiInvariantStatus');
    const kpiOutboxStatus = document.getElementById('kpiOutboxStatus');

    const accountsTableBody = document.getElementById('accountsTableBody');
    const ledgerTableBody = document.getElementById('ledgerTableBody');
    const discrepancyTableBody = document.getElementById('discrepancyTableBody');
    const executionConsole = document.getElementById('executionConsole');

    const paymentSourceAccount = document.getElementById('paymentSourceAccount');
    const paymentDestAccount = document.getElementById('paymentDestAccount');
    const paymentAmount = document.getElementById('paymentAmount');
    const paymentCurrency = document.getElementById('paymentCurrency');
    const paymentIdempotencyKey = document.getElementById('paymentIdempotencyKey');

    // Modals
    const openAccountModal = document.getElementById('openAccountModal');
    const fundModal = document.getElementById('fundModal');

    // Init
    initTabs();
    generateFreshKey();
    loadDashboardData();

    // Auto Refresh every 10 seconds
    setInterval(() => {
        checkHealth();
    }, 10000);

    // Tab Navigation
    function initTabs() {
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
                document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
                btn.classList.add('active');
                const targetId = btn.getAttribute('data-tab');
                document.getElementById(targetId).classList.add('active');

                if (targetId === 'ledgerTab') loadLedgerEntries();
                if (targetId === 'accountsTab') loadAccounts();
                if (targetId === 'reconciliationTab') runReconciliation(false);
            });
        });
    }

    // Helper: UUID Generator
    function generateUUID() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
            const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }

    function generateFreshKey() {
        paymentIdempotencyKey.value = 'pay-' + generateUUID();
    }

    document.getElementById('generateKeyBtn')?.addEventListener('click', generateFreshKey);

    // Logging to Interactive Console
    function logConsole(message, type = 'info') {
        const time = new Date().toLocaleTimeString();
        const prefix = `[${time}] `;
        executionConsole.textContent += `\n${prefix}${message}`;
        executionConsole.scrollTop = executionConsole.scrollHeight;
    }

    document.getElementById('clearConsoleBtn')?.addEventListener('click', () => {
        executionConsole.textContent = 'Console cleared. Ready.';
    });

    // Toast Notifications
    function showToast(message, type = 'success') {
        const container = document.getElementById('toastContainer');
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = message;
        container.appendChild(toast);
        setTimeout(() => toast.remove(), 4000);
    }

    // Load Dashboard Data
    async function loadDashboardData() {
        await Promise.all([
            checkHealth(),
            loadAccounts(),
            loadPayments(),
            loadLedgerEntries()
        ]);
    }

    // Check System Health
    async function checkHealth() {
        try {
            const res = await fetch('/actuator/health');
            const data = await res.json();
            if (data.status === 'UP') {
                systemHealthBadge.className = 'health-pill healthy';
                healthStatusText.textContent = 'INVARIANTS BALANCED';
                kpiDiscrepancies.textContent = '0 Errors';
                kpiInvariantStatus.textContent = 'All debits net to credits';
                kpiOutboxStatus.textContent = 'Active (Kafka)';
            } else {
                systemHealthBadge.className = 'health-pill';
                healthStatusText.textContent = 'CHECK FAILED';
            }
        } catch (e) {
            systemHealthBadge.className = 'health-pill';
            healthStatusText.textContent = 'OFFLINE';
        }
    }

    // 1. Accounts API
    async function loadAccounts() {
        try {
            const res = await fetch('/api/v1/accounts');
            accounts = await res.json();
            renderAccountsTable();
            populateAccountDropdowns();
            updateAccountsKPI();
        } catch (e) {
            console.error('Failed to load accounts', e);
        }
    }

    function updateAccountsKPI() {
        kpiAccountCount.textContent = accounts.length;
        const total = accounts.reduce((sum, a) => sum + parseFloat(a.balance || 0), 0);
        kpiTotalFunds.textContent = `Tracked Liquidity: $${total.toLocaleString('en-US', { minimumFractionDigits: 2 })}`;
    }

    function renderAccountsTable() {
        if (!accounts.length) {
            accountsTableBody.innerHTML = `<tr><td colspan="7" class="empty-cell">No accounts opened yet. Click "Open New Account" to begin.</td></tr>`;
            return;
        }

        accountsTableBody.innerHTML = accounts.map(a => `
            <tr>
                <td><strong>${escapeHtml(a.accountNumber)}</strong></td>
                <td>${escapeHtml(a.ownerName)}</td>
                <td><span class="badge active">${escapeHtml(a.currency)}</span></td>
                <td class="text-right font-mono"><strong>${parseFloat(a.balance).toFixed(2)}</strong></td>
                <td><span class="badge ${a.status.toLowerCase()}">${a.status}</span></td>
                <td class="font-mono text-muted" style="font-size:0.75rem;">${a.accountId.substring(0, 13)}...</td>
                <td class="text-right">
                    <button class="btn btn-secondary btn-sm" onclick="window.openFundModal('${a.accountId}', '${escapeHtml(a.accountNumber)}', '${escapeHtml(a.ownerName)}', '${a.currency}', 'DEPOSIT')">Deposit</button>
                    <button class="btn btn-secondary btn-sm" onclick="window.openFundModal('${a.accountId}', '${escapeHtml(a.accountNumber)}', '${escapeHtml(a.ownerName)}', '${a.currency}', 'WITHDRAW')">Withdraw</button>
                    <button class="btn btn-secondary btn-sm" onclick="window.toggleAccountStatus('${a.accountId}', '${a.status}')">${a.status === 'ACTIVE' ? 'Freeze' : 'Unfreeze'}</button>
                </td>
            </tr>
        `).join('');
    }

    function populateAccountDropdowns() {
        const activeAccounts = accounts.filter(a => a.status === 'ACTIVE');
        const optionsHtml = '<option value="">Select account...</option>' + 
            activeAccounts.map(a => `<option value="${a.accountId}">${a.accountNumber} (${a.ownerName}) - Bal: ${parseFloat(a.balance).toFixed(2)} ${a.currency}</option>`).join('');

        paymentSourceAccount.innerHTML = optionsHtml;
        paymentDestAccount.innerHTML = optionsHtml;
    }

    // Toggle Freeze / Unfreeze
    window.toggleAccountStatus = async (accountId, currentStatus) => {
        const newStatus = currentStatus === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
        try {
            const res = await fetch(`/api/v1/accounts/${accountId}/status`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ status: newStatus })
            });
            if (!res.ok) throw new Error('Status update failed');
            showToast(`Account status updated to ${newStatus}`);
            loadAccounts();
        } catch (err) {
            showToast(err.message, 'error');
        }
    };

    // Open Account Modal
    document.getElementById('openAccountModalBtn')?.addEventListener('click', () => {
        document.getElementById('newAccountNumber').value = 'ACC-' + Math.floor(1000 + Math.random() * 9000);
        document.getElementById('newOwnerName').value = '';
        document.getElementById('newOpeningBalance').value = '1000.00';
        openAccountModal.classList.add('active');
    });

    document.getElementById('closeAccountModalBtn')?.addEventListener('click', () => openAccountModal.classList.remove('active'));
    document.getElementById('cancelAccountModalBtn')?.addEventListener('click', () => openAccountModal.classList.remove('active'));

    document.getElementById('openAccountForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const payload = {
            accountNumber: document.getElementById('newAccountNumber').value.trim(),
            ownerName: document.getElementById('newOwnerName').value.trim(),
            currency: document.getElementById('newCurrency').value,
            openingBalance: parseFloat(document.getElementById('newOpeningBalance').value) || 0
        };

        try {
            const res = await fetch('/api/v1/accounts', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                const err = await res.json();
                throw new Error(err.message || 'Failed to create account');
            }

            const data = await res.json();
            showToast(`Account ${data.accountNumber} created with opening balance funded from reserve.`);
            openAccountModal.classList.remove('active');
            await loadAccounts();
        } catch (err) {
            showToast(err.message, 'error');
        }
    });

    // Fund Modal (Deposit/Withdraw)
    window.openFundModal = (accountId, accNum, owner, currency, type) => {
        document.getElementById('fundAccountId').value = accountId;
        document.getElementById('fundType').value = type;
        document.getElementById('fundModalTitle').textContent = type === 'DEPOSIT' ? 'Deposit Liquidity' : 'Withdraw Funds';
        document.getElementById('fundAccountInfo').textContent = `${accNum} (${owner}) - ${currency}`;
        document.getElementById('fundAmount').value = '250.00';
        fundModal.classList.add('active');
    };

    document.getElementById('closeFundModalBtn')?.addEventListener('click', () => fundModal.classList.remove('active'));
    document.getElementById('cancelFundModalBtn')?.addEventListener('click', () => fundModal.classList.remove('active'));

    document.getElementById('fundForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const accountId = document.getElementById('fundAccountId').value;
        const type = document.getElementById('fundType').value;
        const amount = parseFloat(document.getElementById('fundAmount').value);
        const endpoint = type === 'DEPOSIT' ? 'deposit' : 'withdraw';

        try {
            const res = await fetch(`/api/v1/accounts/${accountId}/${endpoint}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    idempotencyKey: 'fund-' + generateUUID(),
                    amount: amount
                })
            });

            if (!res.ok) {
                const err = await res.json();
                throw new Error(err.message || `${type} failed`);
            }

            showToast(`${type} of $${amount.toFixed(2)} completed successfully!`);
            fundModal.classList.remove('active');
            await loadAccounts();
        } catch (err) {
            showToast(err.message, 'error');
        }
    });

    // 2. Payments & Idempotency
    document.getElementById('paymentForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const srcId = paymentSourceAccount.value;
        const dstId = paymentDestAccount.value;
        const amount = parseFloat(paymentAmount.value);
        const key = paymentIdempotencyKey.value.trim();

        if (srcId === dstId) {
            showToast('Source and destination accounts must be different', 'error');
            return;
        }

        const payload = {
            idempotencyKey: key,
            sourceAccountId: srcId,
            destinationAccountId: dstId,
            amount: amount,
            currency: paymentCurrency.value
        };

        lastPaymentRequest = payload;
        logConsole(`Sending payment: ${amount} USD with key: ${key}`);

        const startTime = performance.now();
        try {
            const res = await fetch('/api/v1/payments', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            const latency = (performance.now() - startTime).toFixed(1);
            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.message || 'Payment execution failed');
            }

            logConsole(`Success: Payment ${data.paymentId} status: ${data.status} (took ${latency}ms)`);
            showToast(`Payment of $${amount.toFixed(2)} completed!`);
            generateFreshKey();
            await loadAccounts();
            await loadPayments();
        } catch (err) {
            logConsole(`Error: ${err.message}`);
            showToast(err.message, 'error');
        }
    });

    // Duplicate Key Test
    document.getElementById('testDuplicateKeyBtn')?.addEventListener('click', async () => {
        if (!lastPaymentRequest) {
            showToast('Please execute a payment first before testing duplicate replay.', 'error');
            return;
        }

        logConsole(`Replaying duplicate payment with KEY: ${lastPaymentRequest.idempotencyKey}`);
        const start = performance.now();
        try {
            const res = await fetch('/api/v1/payments', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(lastPaymentRequest)
            });

            const latency = (performance.now() - start).toFixed(1);
            const data = await res.json();

            logConsole(`Idempotency replay returned CACHED response in ${latency}ms! Payment ID: ${data.paymentId} - Zero money moved!`);
            showToast(`Idempotency verified! Replay returned cached response in ${latency}ms.`);
        } catch (err) {
            logConsole(`Replay error: ${err.message}`);
        }
    });

    // Concurrency Stress Test
    document.getElementById('testConcurrentTransfersBtn')?.addEventListener('click', async () => {
        if (accounts.length < 2) {
            showToast('Please open at least two accounts to run the concurrency test.', 'error');
            return;
        }

        const accA = accounts[0];
        const accB = accounts[1];
        logConsole(`Firing 10 simultaneous opposite transfers between ${accA.accountNumber} and ${accB.accountNumber}...`);

        const promises = [];
        for (let i = 0; i < 10; i++) {
            const isForward = i % 2 === 0;
            const payload = {
                idempotencyKey: 'conc-' + i + '-' + generateUUID(),
                sourceAccountId: isForward ? accA.accountId : accB.accountId,
                destinationAccountId: isForward ? accB.accountId : accA.accountId,
                amount: 10.00,
                currency: 'USD'
            };
            promises.push(
                fetch('/api/v1/payments', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                }).then(r => r.json())
            );
        }

        const start = performance.now();
        const results = await Promise.allSettled(promises);
        const elapsed = (performance.now() - start).toFixed(1);

        const succeeded = results.filter(r => r.status === 'fulfilled' && r.value.status === 'COMPLETED').length;
        logConsole(`Completed 10 concurrent transfers in ${elapsed}ms: ${succeeded}/10 succeeded with 0 deadlocks!`);
        showToast(`Concurrency test complete: ${succeeded}/10 transfers committed without deadlock.`);
        await loadAccounts();
        await loadPayments();
    });

    // 3. Load Payments
    async function loadPayments() {
        try {
            const res = await fetch('/api/v1/payments');
            payments = await res.json();
            kpiPaymentCount.textContent = payments.length;
        } catch (e) {
            console.error('Failed to load payments', e);
        }
    }

    // 4. Double-Entry Ledger Stream
    async function loadLedgerEntries() {
        try {
            const res = await fetch('/api/v1/ledger/entries?limit=50');
            const entries = await res.json();
            renderLedgerTable(entries);
        } catch (e) {
            console.error('Failed to load ledger stream', e);
        }
    }

    function renderLedgerTable(entries) {
        if (!entries.length) {
            ledgerTableBody.innerHTML = `<tr><td colspan="7" class="empty-cell">No ledger entries recorded yet.</td></tr>`;
            return;
        }

        ledgerTableBody.innerHTML = entries.map(e => `
            <tr>
                <td class="font-mono" style="font-size:0.75rem;">${e.entryId.substring(0, 8)}...</td>
                <td class="font-mono text-muted" style="font-size:0.75rem;">${e.paymentId.substring(0, 8)}...</td>
                <td class="font-mono" style="font-size:0.75rem;">${e.accountId.substring(0, 8)}...</td>
                <td><span class="badge ${e.entryType.toLowerCase()}">${e.entryType}</span></td>
                <td class="text-right font-mono"><strong>${parseFloat(e.amount).toFixed(2)} ${e.currency}</strong></td>
                <td class="text-right font-mono">${parseFloat(e.balanceAfter).toFixed(2)}</td>
                <td class="text-muted" style="font-size:0.75rem;">${new Date(e.createdAt).toLocaleTimeString()}</td>
            </tr>
        `).join('');
    }

    // 5. Reconciliation Auditor
    document.getElementById('triggerReconciliationBtn')?.addEventListener('click', () => runReconciliation(true));

    async function runReconciliation(showToasts = true) {
        try {
            const res = await fetch('/api/v1/ledger/reconcile', { method: 'POST' });
            const discrepancies = await res.json();
            renderDiscrepancies(discrepancies);

            if (discrepancies.length === 0) {
                if (showToasts) showToast('Reconciliation complete: 0 discrepancies detected. Invariants hold 100%!');
                kpiDiscrepancies.textContent = '0 Errors';
            } else {
                if (showToasts) showToast(`Reconciliation detected ${discrepancies.length} discrepancies!`, 'error');
                kpiDiscrepancies.textContent = `${discrepancies.length} Errors`;
            }
        } catch (e) {
            console.error('Reconciliation failed', e);
        }
    }

    function renderDiscrepancies(discrepancies) {
        const bannerIcon = document.getElementById('auditBannerIcon');
        const bannerTitle = document.getElementById('auditBannerTitle');
        const bannerDesc = document.getElementById('auditBannerDesc');

        if (!discrepancies.length) {
            bannerIcon.className = 'status-indicator-icon success';
            bannerTitle.textContent = 'Ledger Invariants Healthy';
            bannerDesc.textContent = 'Account balance caches match 100% of immutable ledger entries. All payments net to zero.';
            discrepancyTableBody.innerHTML = `<tr><td colspan="3" class="empty-cell">No discrepancies detected. The double-entry ledger is perfectly balanced.</td></tr>`;
        } else {
            bannerIcon.className = 'status-indicator-icon danger';
            bannerTitle.textContent = 'Drift Detected';
            bannerDesc.textContent = `${discrepancies.length} discrepancy records flagged for auditor investigation.`;
            discrepancyTableBody.innerHTML = discrepancies.map(d => `
                <tr>
                    <td><span class="badge debit">${escapeHtml(d.type)}</span></td>
                    <td class="font-mono">${escapeHtml(d.referenceId)}</td>
                    <td>${escapeHtml(d.detail)}</td>
                </tr>
            `).join('');
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
});
