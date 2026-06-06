const consoleEntries = [];
const MAX_ENTRIES = 200;

function toggleConsole() {
    const consoleEl = document.getElementById('client-console');
    const content = document.querySelector('.content');
    if (!consoleEl) return;
    
    consoleEl.classList.toggle('collapsed');
    consoleEl.classList.toggle('expanded');
    content.classList.toggle('with-console');
}

function addConsoleEntry(type, message) {
    const entry = {
        time: new Date().toLocaleTimeString(),
        type: type,
        message: message
    };
    
    consoleEntries.unshift(entry);
    if (consoleEntries.length > MAX_ENTRIES) {
        consoleEntries.pop();
    }
    
    renderConsoleEntries();
    renderFullConsoleEntries();
    updateConsoleCount();
}

function renderConsoleEntries() {
    const body = document.getElementById('console-body');
    if (!body) return;
    
    if (consoleEntries.length === 0) {
        body.innerHTML = '<div class="console-empty">No events yet...</div>';
        return;
    }
    
    body.innerHTML = consoleEntries.map(entry => 
        '<div class="console-entry">' +
        '<span class="console-time">' + entry.time + '</span>' +
        '<span class="console-type ' + entry.type + '">' + entry.type.toUpperCase() + '</span>' +
        '<span class="console-message">' + escapeHtml(entry.message) + '</span>' +
        '</div>'
    ).join('');
}

function renderFullConsoleEntries() {
    const body = document.getElementById('full-console-body');
    if (!body) return;
    
    if (consoleEntries.length === 0) {
        body.innerHTML = '<div class="console-empty">No events yet...</div>';
        return;
    }
    
    body.innerHTML = consoleEntries.map(entry => 
        '<div class="console-entry">' +
        '<span class="console-time">' + entry.time + '</span>' +
        '<span class="console-type ' + entry.type + '">' + entry.type.toUpperCase() + '</span>' +
        '<span class="console-message">' + escapeHtml(entry.message) + '</span>' +
        '</div>'
    ).join('');
}

function updateConsoleCount() {
    const count = document.getElementById('console-count');
    if (count) {
        count.textContent = consoleEntries.length;
        count.style.display = consoleEntries.length > 0 ? 'inline-block' : 'none';
    }
}

function clearConsole() {
    consoleEntries.length = 0;
    renderConsoleEntries();
    renderFullConsoleEntries();
    updateConsoleCount();
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function initServerConsole() {
    window.sseConnected = false;
    let sseUrl = '/api/console/stream';
    if (window.keycloakInstance && window.keycloakInstance.token) {
        sseUrl += '?token=' + encodeURIComponent(window.keycloakInstance.token);
    }
    const evtSource = new EventSource(sseUrl);
    
    evtSource.onopen = function() {
        window.sseConnected = true;
        updateSseStatus();
    };
    
    evtSource.onmessage = function(event) {
        if (event.data.startsWith(':')) return;
        try {
            const data = JSON.parse(event.data);
            if (data.type === 'heartbeat') return;
            
            if (data.type === 'amqp_connected') {
                updateAmqpStatus(true);
            } else if (data.type === 'amqp_disconnected' || data.type === 'amqp_error') {
                updateAmqpStatus(false);
            } else if (data.type === 'message_received') {
                handleNewMessage(data.message);
            }
            
            addConsoleEntry(data.type, data.message);
        } catch (e) {
            // ignore parse errors
        }
    };
    
    evtSource.onerror = function() {
        window.sseConnected = false;
        updateSseStatus();
        evtSource.close();
        setTimeout(initServerConsole, 5000);
    };
}

function updateSseStatus() {
    const icon = document.getElementById('sse-status-icon');
    const detail = document.getElementById('sse-detail');
    if (!icon || !detail) return;
    
    if (window.sseConnected) {
        icon.textContent = '📡';
        icon.className = 'stat-value status-ok';
        detail.textContent = 'Connected';
    } else {
        icon.textContent = '📴';
        icon.className = 'stat-value status-warning';
        detail.textContent = 'Reconnecting...';
    }
}

function updateAmqpStatus(connected) {
    window.amqpConnected = connected;
    
    const footerIndicator = document.getElementById('footer-amqp-indicator');
    const footerStatus = document.getElementById('footer-amqp-status');
    
    if (footerIndicator && footerStatus) {
        if (connected) {
            footerIndicator.className = 'status-indicator connected';
            footerStatus.textContent = 'AMQP: Connected';
        } else {
            footerIndicator.className = 'status-indicator disconnected';
            footerStatus.textContent = 'AMQP: Disconnected';
        }
    }
    
    const amqpIcon = document.getElementById('amqp-status-icon');
    const amqpDetail = document.getElementById('amqp-detail');
    if (amqpIcon && amqpDetail) {
        if (connected) {
            amqpIcon.textContent = '✅';
            amqpIcon.className = 'stat-value status-ok';
            amqpDetail.textContent = 'Connected';
        } else {
            amqpIcon.textContent = '❌';
            amqpIcon.className = 'stat-value status-error';
            amqpDetail.textContent = 'Disconnected';
        }
    }
}

function handleNewMessage(messageJson) {
    try {
        const msg = JSON.parse(messageJson);

        window.dispatchEvent(new CustomEvent('swim:messageReceived', { detail: msg }));

        const counter = document.getElementById('message-count');
        if (counter) {
            counter.textContent = parseInt(counter.textContent || '0') + 1;
        }

        const noMessagesRow = document.getElementById('no-messages-row');
        if (noMessagesRow) {
            noMessagesRow.remove();
        }
    } catch (e) {
        console.error('Error handling new message:', e);
    }
}

function viewMessageDetails(messageId) {
    window.location.href = '/ui/messages/' + messageId;
}

window.addConsoleEntry = addConsoleEntry;
window.toggleConsole = toggleConsole;
window.clearConsole = clearConsole;
window.updateAmqpStatus = updateAmqpStatus;
window.handleNewMessage = handleNewMessage;
window.viewMessageDetails = viewMessageDetails;

async function loadAmqpStatus() {
    try {
        const response = await fetch('/api/amqp/status');
        const data = await response.json();
        updateAmqpStatus(data.connected);
    } catch (e) {
        updateAmqpStatus(false);
    }
}

document.addEventListener('DOMContentLoaded', async function() {
    renderConsoleEntries();
    renderFullConsoleEntries();
    updateConsoleCount();

    await window.keycloakReady;
    initServerConsole();
    loadAmqpStatus();
});
