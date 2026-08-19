'use strict';

// Same-origin API - this page is served by the same Spring Boot app it talks to (src/main/resources/static),
// so relative paths are all that's needed. No base URL to configure, no CORS involved in this deployment mode
// at all (CORS only matters when the frontend is served from a different origin, e.g. a separate dev server).
const API_BASE = '/api';

// ---- DOM references, grabbed once ----
const investorSelect = document.getElementById('investor-select');
const investorIdInput = document.getElementById('investor-id-input');
const loadByIdBtn = document.getElementById('load-by-id-btn');
const investorError = document.getElementById('investor-error');

const dashboardSection = document.getElementById('dashboard-section');
const investorNameEl = document.getElementById('investor-name');
const investorAgeEl = document.getElementById('investor-age');
const productCardsEl = document.getElementById('product-cards');

const withdrawalSection = document.getElementById('withdrawal-section');
const withdrawalForm = document.getElementById('withdrawal-form');
const productSelect = document.getElementById('product-select');
const productErrorEl = document.getElementById('product-error');
const amountInput = document.getElementById('amount-input');
const amountErrorEl = document.getElementById('amount-error');
const submitBtn = document.getElementById('submit-withdrawal-btn');
const withdrawalMessageEl = document.getElementById('withdrawal-message');

const historySection = document.getElementById('history-section');
const historyBody = document.getElementById('history-body');
const historyEmptyEl = document.getElementById('history-empty');

const csvSection = document.getElementById('csv-section');
const csvFromInput = document.getElementById('csv-from');
const csvToInput = document.getElementById('csv-to');
const csvDownloadBtn = document.getElementById('csv-download-btn');
const csvMessageEl = document.getElementById('csv-message');

// Current investor's products, kept around only to populate the withdrawal dropdown - not re-fetched every
// time the form renders.
let currentInvestorId = null;
let currentProducts = [];

// ---- Formatting helpers ----

const currencyFormatter = new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' });

function formatCurrency(value) {
    return currencyFormatter.format(Number(value));
}

const dateFormatter = new Intl.DateTimeFormat('en-ZA', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
});

// Java's LocalDateTime can serialize with up to 9 fractional-second digits ("...16:07:34.033771"); the JS
// Date constructor only reliably parses up to 3 (milliseconds). Trim before parsing rather than risk an
// "Invalid Date" in whichever browser is less forgiving about the extra digits.
function parseServerDateTime(value) {
    if (!value) return null;
    const trimmed = value.replace(/(\.\d{3})\d*$/, '$1');
    return new Date(trimmed);
}

function formatDate(value) {
    const date = parseServerDateTime(value);
    return date ? dateFormatter.format(date) : '';
}

// ---- API helper ----

// Thrown for any non-2xx response, carrying the parsed error body (our backend's consistent ErrorResponse
// shape: message + optional fieldErrors) so callers can render the real server message instead of a generic
// one. A 422 rule violation and a 400 field error look different in the UI on purpose - they mean different
// things - and this is what keeps that distinction available to the caller.
class ApiError extends Error {
    constructor(status, body) {
        super(body && body.message ? body.message : `Request failed with status ${status}`);
        this.status = status;
        this.body = body;
    }
}

async function apiRequest(path, options) {
    let response;
    try {
        response = await fetch(`${API_BASE}${path}`, options);
    } catch (networkError) {
        // fetch() itself only rejects on a network-level failure (server unreachable, DNS, offline) - never
        // on an HTTP error status, which is handled below instead.
        throw new ApiError(0, { message: 'Unable to reach the server. Please check your connection and try again.' });
    }

    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get('content-type') || '';
    const body = contentType.includes('application/json') ? await response.json() : await response.text();

    if (!response.ok) {
        throw new ApiError(response.status, typeof body === 'object' ? body : { message: String(body) });
    }
    return body;
}

function getJson(path) {
    return apiRequest(path, { headers: { Accept: 'application/json' } });
}

function postJson(path, payload) {
    return apiRequest(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(payload)
    });
}

// ---- Rendering ----

function showBanner(el, message, kind) {
    el.textContent = message;
    el.className = `message-banner ${kind}`;
    el.hidden = false;
}

function hideBanner(el) {
    el.hidden = true;
    el.textContent = '';
}

function renderPortfolio(portfolio) {
    investorNameEl.textContent = `${portfolio.firstName} ${portfolio.lastName}`;
    investorAgeEl.textContent = `Age ${portfolio.age} · ${portfolio.email}`;

    productCardsEl.innerHTML = '';
    portfolio.products.forEach((product) => {
        const card = document.createElement('article');
        card.className = 'product-card';
        card.innerHTML = `
            <h3>${escapeHtml(product.name)}</h3>
            <p class="product-type">${escapeHtml(product.type)}</p>
            <p class="product-balance">${formatCurrency(product.balance)}</p>
        `;
        productCardsEl.appendChild(card);
    });

    currentProducts = portfolio.products;
    populateProductDropdown(currentProducts);
    dashboardSection.hidden = false;
    withdrawalSection.hidden = false;
    csvSection.hidden = false;
}

function populateProductDropdown(products) {
    productSelect.innerHTML = '<option value="">-- choose a product --</option>';
    products.forEach((product) => {
        const option = document.createElement('option');
        option.value = product.id;
        option.textContent = `${product.name} (${product.type}) — ${formatCurrency(product.balance)}`;
        productSelect.appendChild(option);
    });
}

function renderHistory(notices) {
    historyBody.innerHTML = '';
    if (notices.length === 0) {
        historySection.querySelector('table').hidden = true;
        historyEmptyEl.hidden = false;
    } else {
        historySection.querySelector('table').hidden = false;
        historyEmptyEl.hidden = true;
        notices.forEach((notice) => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${formatDate(notice.requestedAt)}</td>
                <td>${escapeHtml(notice.productName)}</td>
                <td>${formatCurrency(notice.amount)}</td>
                <td>${formatCurrency(notice.balanceAfter)}</td>
            `;
            historyBody.appendChild(row);
        });
    }
    historySection.hidden = false;
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
}

// ---- Investor loading ----

async function loadInvestor(id) {
    hideBanner(investorError);
    dashboardSection.hidden = true;
    withdrawalSection.hidden = true;
    historySection.hidden = true;
    csvSection.hidden = true;

    if (!id || Number(id) <= 0) {
        showBanner(investorError, 'Enter a valid investor id.', 'error');
        return;
    }

    try {
        const [portfolio, history] = await Promise.all([
            getJson(`/investors/${id}/portfolio`),
            getJson(`/investors/${id}/withdrawals`)
        ]);
        currentInvestorId = id;
        renderPortfolio(portfolio);
        renderHistory(history);
    } catch (err) {
        currentInvestorId = null;
        showBanner(investorError, describeError(err), 'error');
    }
}

// Refreshes the dashboard and history for whichever investor is currently loaded, without a page reload -
// used after a successful withdrawal so the new balance and the new history row appear immediately.
async function refreshCurrentInvestor() {
    if (!currentInvestorId) return;
    const [portfolio, history] = await Promise.all([
        getJson(`/investors/${currentInvestorId}/portfolio`),
        getJson(`/investors/${currentInvestorId}/withdrawals`)
    ]);
    renderPortfolio(portfolio);
    renderHistory(history);
}

function describeError(err) {
    if (err instanceof ApiError) {
        return err.body && err.body.message ? err.body.message : err.message;
    }
    return 'Something went wrong. Please try again.';
}

investorSelect.addEventListener('change', () => {
    if (investorSelect.value) {
        investorIdInput.value = '';
        loadInvestor(investorSelect.value);
    }
});

loadByIdBtn.addEventListener('click', () => {
    investorSelect.value = '';
    loadInvestor(investorIdInput.value);
});

// ---- Withdrawal form: client-side validation ----

// Required fields and "amount is a positive number" are checked here, before any network call - the UI
// validation feature the assessment asks for. What's deliberately NOT checked here: whether the amount
// exceeds the balance, the 90% cap, or the retirement age rule. Those are the four business rules
// WithdrawalService already enforces server-side, and re-implementing them here would mean two sources of
// truth that can drift - e.g. if the 90% figure ever changed, this file could silently go stale and let
// invalid amounts through client-side while the server (correctly) rejects them anyway. Structural validation
// (is this field present, is this a positive number) is safe to duplicate; account-state rules are not.
function validateWithdrawalForm() {
    let valid = true;
    hideBanner(withdrawalMessageEl);
    productErrorEl.textContent = '';
    amountErrorEl.textContent = '';

    if (!productSelect.value) {
        productErrorEl.textContent = 'Please select a product.';
        valid = false;
    }

    const rawAmount = amountInput.value.trim();
    if (!rawAmount) {
        amountErrorEl.textContent = 'Please enter an amount.';
        valid = false;
    } else {
        const amount = Number(rawAmount);
        if (!Number.isFinite(amount) || amount <= 0) {
            amountErrorEl.textContent = 'Amount must be a positive number.';
            valid = false;
        }
    }

    return valid;
}

withdrawalForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!validateWithdrawalForm()) {
        return;
    }

    submitBtn.disabled = true;
    try {
        const notice = await postJson('/withdrawals', {
            productId: Number(productSelect.value),
            amount: Number(amountInput.value)
        });
        showBanner(
            withdrawalMessageEl,
            `Withdrawal submitted. New balance for ${notice.productName}: ${formatCurrency(notice.balanceAfter)}.`,
            'success'
        );
        amountInput.value = '';
        await refreshCurrentInvestor();
    } catch (err) {
        applyWithdrawalError(err);
    } finally {
        submitBtn.disabled = false;
    }
});

// Renders whatever the server actually said, distinguishing the two shapes GlobalExceptionHandler can send:
// a 400 with fieldErrors goes next to the field it's about (matching WithdrawalRequest's own field names,
// productId/amount); everything else (422 rule violation, 404 unknown product, 400 malformed body, 500) is
// the one message the server gave, shown as-is in the form banner - never a generic "something went wrong"
// standing in for a specific rule the server already spelled out.
function applyWithdrawalError(err) {
    if (err instanceof ApiError && err.body && err.body.fieldErrors) {
        const fieldErrors = err.body.fieldErrors;
        if (fieldErrors.productId) productErrorEl.textContent = fieldErrors.productId;
        if (fieldErrors.amount) amountErrorEl.textContent = fieldErrors.amount;
        showBanner(withdrawalMessageEl, err.body.message || 'Validation failed.', 'error');
        return;
    }
    showBanner(withdrawalMessageEl, describeError(err), 'error');
}

// ---- CSV export ----

csvDownloadBtn.addEventListener('click', async () => {
    if (!currentInvestorId) return;
    hideBanner(csvMessageEl);

    const params = new URLSearchParams();
    if (csvFromInput.value) params.set('from', csvFromInput.value);
    if (csvToInput.value) params.set('to', csvToInput.value);
    const query = params.toString();
    const path = `/investors/${currentInvestorId}/statement.csv${query ? `?${query}` : ''}`;

    csvDownloadBtn.disabled = true;
    try {
        const response = await fetch(`${API_BASE}${path}`);
        if (!response.ok) {
            const contentType = response.headers.get('content-type') || '';
            const body = contentType.includes('application/json') ? await response.json() : null;
            throw new ApiError(response.status, body || { message: `Download failed with status ${response.status}.` });
        }

        // Fetched (rather than a plain link navigation) specifically so a 400/404 here shows the same kind
        // of in-app error as everywhere else, instead of the browser either rendering raw JSON in a new tab
        // or downloading a file literally named "error" with .csv content that isn't a CSV.
        const blob = await response.blob();
        const objectUrl = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = objectUrl;
        link.download = `statement-${currentInvestorId}.csv`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(objectUrl);
    } catch (err) {
        showBanner(csvMessageEl, describeError(err), 'error');
    } finally {
        csvDownloadBtn.disabled = false;
    }
});
