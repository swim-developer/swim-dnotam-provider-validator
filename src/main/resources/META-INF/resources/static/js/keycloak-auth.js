import Keycloak from '/static/js/keycloak.js';

let keycloak = null;
let initInProgress = false;
let initCompleted = false;

async function fetchConfig() {
    const response = await fetch('/api/config/keycloak');
    if (!response.ok) {
        throw new Error('Failed to fetch Keycloak configuration');
    }
    return response.json();
}

async function init() {
    if (initInProgress || initCompleted) {
        return window.keycloakReady;
    }
    initInProgress = true;

    try {
        const config = await fetchConfig();
        keycloak = new Keycloak(config);

        const authenticated = await keycloak.init({
            onLoad: 'check-sso',
            pkceMethod: 'S256',
            checkLoginIframe: false
        });

        window.keycloakInstance = keycloak;
        window.resolveKeycloakReady(authenticated);
        initCompleted = true;

        updateUI(authenticated);

        if (authenticated) {
            setupTokenRefresh();
        }

        return authenticated;
    } catch (error) {
        console.error('Failed to initialize Keycloak:', error);
        showError('Failed to initialize authentication: ' + (error.message || error));
        window.keycloakInstance = keycloak;
        window.resolveKeycloakReady(false);
        initCompleted = true;
        updateUI(false);
        return false;
    } finally {
        initInProgress = false;
    }
}

function login() {
    if (keycloak) {
        keycloak.login();
    }
}

function logout() {
    if (keycloak) {
        keycloak.logout({
            redirectUri: window.location.origin + '/ui/token'
        });
    }
}

async function refreshToken() {
    if (keycloak && keycloak.authenticated) {
        try {
            const refreshed = await keycloak.updateToken(-1);
            if (refreshed) {
                updateTokenDisplay();
                showSuccess('Token refreshed successfully!');
            }
        } catch (error) {
            console.error('Failed to refresh token:', error);
            showError('Failed to refresh token. Please login again.');
            keycloak.login();
        }
    }
}

function setupTokenRefresh() {
    setInterval(async () => {
        if (keycloak && keycloak.authenticated) {
            try {
                await keycloak.updateToken(70);
            } catch (error) {
                console.error('Token refresh failed:', error);
            }
        }
    }, 60000);
}

function decodeTokenHeader(token) {
    if (!token) return null;
    try {
        const base64Url = token.split('.')[0];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
        return JSON.parse(jsonPayload);
    } catch (e) {
        console.error('Error decoding token header:', e);
        return null;
    }
}

function decodeTokenPayload(token) {
    if (!token) return null;
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
        return JSON.parse(jsonPayload);
    } catch (e) {
        console.error('Error decoding token payload:', e);
        return null;
    }
}

function updateUI(authenticated) {
    const tokenInfoBar = document.getElementById('token-info-bar');
    const jwtContainer = document.getElementById('jwt-container');
    const loginPrompt = document.getElementById('login-prompt');
    const loginButton = document.getElementById('login-button');
    const logoutButton = document.getElementById('logout-button');
    const refreshButton = document.getElementById('refresh-token-button');
    
    const footerIndicator = document.getElementById('footer-auth-indicator');
    const footerStatus = document.getElementById('footer-auth-status');
    
    if (footerIndicator && footerStatus) {
        if (authenticated) {
            footerIndicator.className = 'status-indicator connected';
            footerStatus.textContent = 'Authenticated';
        } else {
            footerIndicator.className = 'status-indicator disconnected';
            footerStatus.textContent = 'Not Authenticated';
        }
    }

    if (authenticated) {
        if (tokenInfoBar) {
            tokenInfoBar.style.display = 'flex';
            document.getElementById('token-user-info').textContent = keycloak.tokenParsed?.preferred_username || 'N/A';
            document.getElementById('token-email-info').textContent = keycloak.tokenParsed?.email || 'N/A';
            updateTokenExpiry();
        }

        if (loginButton) loginButton.style.display = 'none';
        if (logoutButton) logoutButton.style.display = 'inline-flex';
        if (refreshButton) refreshButton.style.display = 'inline-flex';
        if (jwtContainer) jwtContainer.style.display = 'grid';
        if (loginPrompt) loginPrompt.style.display = 'none';

        updateTokenDisplay();
    } else {
        if (tokenInfoBar) tokenInfoBar.style.display = 'none';
        if (loginButton) loginButton.style.display = 'inline-flex';
        if (logoutButton) logoutButton.style.display = 'none';
        if (refreshButton) refreshButton.style.display = 'none';
        if (jwtContainer) jwtContainer.style.display = 'none';
        if (loginPrompt) loginPrompt.style.display = 'flex';
    }
}

function updateTokenDisplay() {
    const accessHeaderContent = document.getElementById('access-header-content');
    const accessTokenContent = document.getElementById('access-token-content');
    const idHeaderContent = document.getElementById('id-header-content');
    const idTokenContent = document.getElementById('id-token-content');
    const rawTokenContent = document.getElementById('raw-token-content');

    if (accessHeaderContent && keycloak.token) {
        const header = decodeTokenHeader(keycloak.token);
        accessHeaderContent.textContent = JSON.stringify(header, null, 2);
        if (typeof Prism !== 'undefined') {
            Prism.highlightElement(accessHeaderContent);
        }
    }

    if (accessTokenContent && keycloak.token) {
        const payload = decodeTokenPayload(keycloak.token);
        accessTokenContent.textContent = JSON.stringify(payload, null, 2);
        if (typeof Prism !== 'undefined') {
            Prism.highlightElement(accessTokenContent);
        }
    }

    if (idHeaderContent && keycloak.idToken) {
        const header = decodeTokenHeader(keycloak.idToken);
        idHeaderContent.textContent = JSON.stringify(header, null, 2);
        if (typeof Prism !== 'undefined') {
            Prism.highlightElement(idHeaderContent);
        }
    }

    if (idTokenContent && keycloak.idToken) {
        const payload = decodeTokenPayload(keycloak.idToken);
        idTokenContent.textContent = JSON.stringify(payload, null, 2);
        if (typeof Prism !== 'undefined') {
            Prism.highlightElement(idTokenContent);
        }
    }

    if (rawTokenContent && keycloak.token) {
        const parts = keycloak.token.split('.');
        rawTokenContent.innerHTML = 
            `<span class="jwt-header">${parts[0]}</span>` +
            `<span class="jwt-dot">.</span>` +
            `<span class="jwt-payload">${parts[1]}</span>` +
            `<span class="jwt-dot">.</span>` +
            `<span class="jwt-signature">${parts[2]}</span>`;
    }
}

function updateTokenExpiry() {
    const expiryInfo = document.getElementById('token-expiry-info');
    if (!expiryInfo || !keycloak || !keycloak.tokenParsed) return;

    function update() {
        const exp = keycloak.tokenParsed.exp * 1000;
        const now = Date.now();
        const diff = exp - now;

        if (diff <= 0) {
            expiryInfo.textContent = 'Expired';
            expiryInfo.style.color = '#ef4444';
        } else {
            const minutes = Math.floor(diff / 60000);
            const seconds = Math.floor((diff % 60000) / 1000);
            expiryInfo.textContent = `Expires: ${minutes}m ${seconds}s`;
            expiryInfo.style.color = diff < 60000 ? '#ffc107' : '#a0a0a0';
        }
    }

    update();
    setInterval(update, 1000);
}

function showSuccess(message) {
    showMessage(message, 'success');
}

function showError(message) {
    showMessage(message, 'error');
}

function showMessage(message, type) {
    const container = document.getElementById('message-container');
    if (!container) return;

    const msgDiv = document.createElement('div');
    msgDiv.className = `message message-${type}`;
    msgDiv.textContent = message;
    container.appendChild(msgDiv);

    setTimeout(() => {
        msgDiv.remove();
    }, 5000);
}

document.addEventListener('DOMContentLoaded', function() {
    init();

    const loginBtn = document.getElementById('login-button');
    const loginBtnCenter = document.getElementById('login-button-center');
    const logoutBtn = document.getElementById('logout-button');
    const refreshBtn = document.getElementById('refresh-token-button');

    if (loginBtn) loginBtn.addEventListener('click', login);
    if (loginBtnCenter) loginBtnCenter.addEventListener('click', login);
    if (logoutBtn) logoutBtn.addEventListener('click', logout);
    if (refreshBtn) refreshBtn.addEventListener('click', refreshToken);
});
