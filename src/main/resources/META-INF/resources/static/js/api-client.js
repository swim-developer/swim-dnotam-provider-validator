
const REQUEST_EXAMPLES = {
    createSubscription: [
        {
            title: 'Minimal Request',
            description: 'Required field only',
            body: { topic: 'DigitalNOTAMService' }
        },
        {
            title: 'Basic Request',
            description: 'Common filters',
            body: {
                topic: 'DigitalNOTAMService',
                eventScenario: ['SFC.CON'],
                airportHeliport: ['EADD']
            }
        },
        {
            title: 'Complete Request',
            description: 'All available fields',
            body: {
                topic: 'DigitalNOTAMService',
                qos: 'EXACTLY_ONCE',
                durable: true,
                description: 'Subscription for Runway and Airspace closures',
                comment: 'Operational desk A - Critical alerts',
                queueName: 'DNOTAM-MY_CLIENT_ID-ExistingQueueUUID',
                eventScenario: ['AD.CLS', 'SAA.NEW', 'STAND.LIM'],
                airportHeliport: ['EHAM', 'LFPO'],
                airspace: ['EHAA', 'LFFF'],
                publisher: 'EUROCONTROL',
                provider: 'EAD',
                eventSeries: 'A'
            }
        }
    ],
    updateSubscription: [
        {
            title: 'Activate',
            description: 'Resume message delivery',
            body: { subscription_status: 'ACTIVE' }
        },
        {
            title: 'Pause',
            description: 'Stop message delivery temporarily',
            body: { subscription_status: 'PAUSED' }
        },
        {
            title: 'Cancel/Delete',
            description: 'Mark subscription for deletion',
            body: { subscription_status: 'DELETED' }
        }
    ]
};

function buildBackendEndpoint(endpoint, resolvedPath, providerUrl) {
    const method = endpoint.method;
    const encodedProvider = encodeURIComponent(providerUrl);
    
    if (resolvedPath === '/swim/v1/subscriptions' && method === 'POST') {
        return { method: 'POST', url: `/api/provider/subscriptions?providerUrl=${encodedProvider}` };
    }
    if (resolvedPath === '/swim/v1/subscriptions' && method === 'GET') {
        return { method: 'GET', url: `/api/provider/subscriptions?providerUrl=${encodedProvider}` };
    }
    if (resolvedPath.startsWith('/swim/v1/subscriptions/') && method === 'GET') {
        const id = resolvedPath.split('/').pop();
        return { method: 'GET', url: `/api/provider/subscriptions/${id}?providerUrl=${encodedProvider}` };
    }
    if (resolvedPath.startsWith('/swim/v1/subscriptions/') && method === 'PUT') {
        const id = resolvedPath.split('/').pop();
        return { method: 'PUT', url: `/api/provider/subscriptions/${id}?providerUrl=${encodedProvider}` };
    }
    if (resolvedPath.startsWith('/swim/v1/subscriptions/') && method === 'DELETE') {
        const id = resolvedPath.split('/').pop();
        return { method: 'DELETE', url: `/api/provider/subscriptions/${id}?providerUrl=${encodedProvider}` };
    }
    if (resolvedPath === '/swim/v1/topics' && method === 'GET') {
        return { method: 'GET', url: `/api/provider/topics?providerUrl=${encodedProvider}` };
    }
    if (resolvedPath.startsWith('/swim/v1/topics/') && method === 'GET') {
        const id = resolvedPath.split('/').pop();
        return { method: 'GET', url: `/api/provider/topics/${id}?providerUrl=${encodedProvider}` };
    }
    if (resolvedPath === '/swim/v1/features' && method === 'GET') {
        return { method: 'GET', url: `/api/provider/features?providerUrl=${encodedProvider}` };
    }
    if (resolvedPath === '/swim/v1/features' && method === 'POST') {
        return { method: 'POST', url: `/api/provider/features?providerUrl=${encodedProvider}` };
    }
    
    return { method: method, url: `/api/provider/subscriptions?providerUrl=${encodedProvider}` };
}

const API_DEFINITIONS = {
    listSubscriptions: {
        method: 'GET',
        path: '/swim/v1/subscriptions',
        summary: 'List all subscriptions',
        description: 'Returns all subscriptions for the authenticated user.',
        params: []
    },
    createSubscription: {
        method: 'POST',
        path: '/swim/v1/subscriptions',
        summary: 'Create subscription',
        description: 'Creates a new subscription to receive DNOTAM events for specified scenarios.',
        params: [],
        body: {
            topic: 'DigitalNOTAMService',
            eventScenario: ['AD.LIM'],
            airportHeliport: ['EADD'],
            description: 'Runway closure notifications'
        }
    },
    getSubscription: {
        method: 'GET',
        path: '/swim/v1/subscriptions/:subscriptionId',
        summary: 'Get subscription details',
        description: 'Returns detailed information about a specific subscription.',
        params: [{ name: 'subscriptionId', type: 'path', required: true, placeholder: 'UUID' }]
    },
    updateSubscription: {
        method: 'PUT',
        path: '/swim/v1/subscriptions/:subscriptionId',
        summary: 'Update subscription status',
        description: 'Updates the status of a subscription. Valid values: ACTIVE (resume), PAUSED (pause), DELETED (cancel).',
        params: [{ name: 'subscriptionId', type: 'path', required: true, placeholder: 'UUID' }],
        body: { subscription_status: 'ACTIVE' }
    },
    deleteSubscription: {
        method: 'DELETE',
        path: '/swim/v1/subscriptions/:subscriptionId',
        summary: 'Delete subscription',
        description: 'Permanently deletes a subscription and its associated AMQP queue.',
        params: [{ name: 'subscriptionId', type: 'path', required: true, placeholder: 'UUID' }]
    },
    listTopics: {
        method: 'GET',
        path: '/swim/v1/topics',
        summary: 'List available topics',
        description: 'Returns the list of topics (event scenarios) available for subscription.',
        params: []
    },
    getTopic: {
        method: 'GET',
        path: '/swim/v1/topics/:topicId',
        summary: 'Get topic details',
        description: 'Returns detailed information about a specific topic including supported filters.',
        params: [{ name: 'topicId', type: 'path', required: true, placeholder: 'e.g. RUNWAY_CLOSURE' }]
    },
    getFeatures: {
        method: 'GET',
        path: '/swim/v1/features',
        summary: 'Query DNOTAM features (WFS GetFeature)',
        description: 'Direct query interface for retrieving DNOTAM events as AIXM 5.1.1 Basic Message.',
        params: [
            { name: 'airportHeliport', type: 'query', required: false, placeholder: 'ICAO code (e.g. EADD)' },
            { name: 'eventScenario', type: 'query', required: false, placeholder: 'e.g. RWY.CLS' }
        ]
    },
    postFeatures: {
        method: 'POST',
        path: '/swim/v1/features',
        summary: 'Query DNOTAM features with OGC Filter',
        description: 'Advanced query interface accepting OGC Filter Encoding 2.0 XML in the request body.',
        params: [],
        body: '<Filter xmlns="http://www.opengis.net/fes/2.0"></Filter>',
        contentType: 'application/xml'
    }
};

let keycloak = null;
let currentEndpoint = null;
let providerApiUrl = '';

async function fetchConfig() {
    const [kcResponse, providerResponse] = await Promise.all([
        fetch('/api/config/keycloak'),
        fetch('/api/config/provider')
    ]);
    
    const kcConfig = await kcResponse.json();
    const providerConfig = await providerResponse.json();
    
    return { keycloak: kcConfig, provider: providerConfig };
}

async function initApiClient() {
    try {
        const config = await fetchConfig();
        const apiUrls = config.provider.apiUrls || [];
        providerApiUrl = apiUrls[0] || '';
        
        const serverSelect = document.getElementById('api-server');
        if (serverSelect && apiUrls.length > 0) {
            serverSelect.innerHTML = apiUrls.map(url => 
                '<option value="' + url + '">' + url + '</option>'
            ).join('');
        }
        
        const authenticated = await window.keycloakReady;
        keycloak = window.keycloakInstance;
    } catch (e) {
        console.error('Init failed:', e);
    }
}


function selectEndpoint(endpointId) {
    const def = API_DEFINITIONS[endpointId];
    if (!def) return;
    
    currentEndpoint = { id: endpointId, ...def };
    
    document.querySelectorAll('.swagger-endpoint').forEach(el => el.classList.remove('active'));
    document.querySelector('[data-id="' + endpointId + '"]').classList.add('active');
    
    document.querySelector('.api-detail-placeholder').style.display = 'none';
    document.getElementById('api-detail-content').style.display = 'block';
    
    const methodBadge = document.getElementById('detail-method');
    methodBadge.textContent = def.method;
    methodBadge.className = 'method-badge method-' + def.method.toLowerCase();
    
    document.getElementById('detail-path').textContent = def.path;
    document.getElementById('detail-description').textContent = def.description;
    
    const paramsSection = document.getElementById('params-section');
    const paramsContainer = document.getElementById('params-container');
    paramsContainer.innerHTML = '';
    
    if (def.params && def.params.length > 0) {
        paramsSection.style.display = 'block';
        def.params.forEach(param => {
            const div = document.createElement('div');
            div.className = 'param-row';
            div.innerHTML = '<label class="param-label">' + param.name + 
                (param.required ? ' <span class="required">*</span>' : '') + 
                '</label><input type="text" class="form-control" id="param-' + param.name + 
                '" placeholder="' + (param.placeholder || '') + '">';
            paramsContainer.appendChild(div);
        });
    } else {
        paramsSection.style.display = 'none';
    }
    
    const bodySection = document.getElementById('body-section');
    if (def.body) {
        bodySection.style.display = 'block';
        const bodyText = typeof def.body === 'string' ? def.body : JSON.stringify(def.body, null, 2);
        document.getElementById('request-body').value = bodyText;
    } else {
        bodySection.style.display = 'none';
    }
    
    renderExamples(endpointId);
    document.getElementById('response-section').style.display = 'none';
}

function renderExamples(endpointId) {
    const examplesSection = document.getElementById('examples-section');
    const examplesContainer = document.getElementById('examples-container');
    const examples = REQUEST_EXAMPLES[endpointId];
    
    if (!examples || examples.length === 0) {
        examplesSection.style.display = 'none';
        return;
    }
    
    examplesSection.style.display = 'block';
    examplesContainer.innerHTML = examples.map((ex, idx) => {
        const jsonStr = JSON.stringify(ex.body, null, 2);
        return `
            <div class="example-card">
                <div class="example-header">
                    <div class="example-info">
                        <span class="example-title">${ex.title}</span>
                        <span class="example-desc">${ex.description}</span>
                    </div>
                    <div class="example-actions">
                        <button class="btn btn-sm btn-primary" onclick="useExample(${idx})">Use this</button>
                        <button class="btn btn-sm btn-secondary" onclick="copyExample(${idx})">Copy</button>
                    </div>
                </div>
                <pre class="example-code"><code>${escapeHtml(jsonStr)}</code></pre>
            </div>
        `;
    }).join('');
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

window.useExample = function(idx) {
    const examples = REQUEST_EXAMPLES[currentEndpoint.id];
    if (examples && examples[idx]) {
        const jsonStr = JSON.stringify(examples[idx].body, null, 2);
        document.getElementById('request-body').value = jsonStr;
        showMessage('Example loaded into request body', 'success');
    }
};

window.copyExample = function(idx) {
    const examples = REQUEST_EXAMPLES[currentEndpoint.id];
    if (examples && examples[idx]) {
        const jsonStr = JSON.stringify(examples[idx].body, null, 2);
        navigator.clipboard.writeText(jsonStr).then(() => {
            showMessage('Copied to clipboard', 'success');
        }).catch(() => {
            showMessage('Failed to copy', 'error');
        });
    }
}

window.executeRequest = async function() {
    if (!currentEndpoint) return;
    
    if (!keycloak || !keycloak.authenticated) {
        showMessage('Please login first (Token page)', 'error');
        return;
    }
    
    try {
        await keycloak.updateToken(30);
    } catch (e) {
        showMessage('Token expired. Please login again.', 'error');
        return;
    }
    
    const server = document.getElementById('api-server').value;
    let path = currentEndpoint.path;
    
    currentEndpoint.params.forEach(param => {
        if (param.type === 'path') {
            const value = document.getElementById('param-' + param.name).value;
            path = path.replace(':' + param.name, value);
        }
    });
    
    const queryParams = currentEndpoint.params
        .filter(p => p.type === 'query')
        .map(p => {
            const val = document.getElementById('param-' + p.name).value;
            return val ? p.name + '=' + encodeURIComponent(val) : null;
        })
        .filter(Boolean)
        .join('&');
    
    const targetUrl = server + path + (queryParams ? '?' + queryParams : '');
    const requestBody = currentEndpoint.body ? document.getElementById('request-body').value : null;
    const contentType = currentEndpoint.contentType || 'application/json';
    
    if (typeof addConsoleEntry === 'function') {
        addConsoleEntry('info', currentEndpoint.method + ' ' + targetUrl);
    }
    
    try {
        const backendEndpoint = buildBackendEndpoint(currentEndpoint, path, server);
        
        const fetchOptions = {
            method: backendEndpoint.method,
            headers: { 
                'Authorization': 'Bearer ' + keycloak.token,
                'Content-Type': 'application/json'
            }
        };
        
        if (requestBody && (backendEndpoint.method === 'POST' || backendEndpoint.method === 'PUT')) {
            fetchOptions.body = requestBody;
        }
        
        const proxyResponse = await fetch(backendEndpoint.url, fetchOptions);
        
        let proxyResult = {};
        const responseText = await proxyResponse.text();
        if (responseText && responseText.trim()) {
            try {
                proxyResult = JSON.parse(responseText);
            } catch (e) {
                proxyResult = { body: responseText };
            }
        }
        
        const status = proxyResult.status || proxyResponse.status;
        const statusText = status >= 200 && status < 300 ? 'OK' : 'Error';
        const responseContentType = proxyResult.contentType || 'application/json';
        let bodyText = proxyResult.body || (responseText ? responseText : '{"message": "Operation completed"}');
        
        const isOk = status >= 200 && status < 300;
        
        const statusEl = document.getElementById('response-status');
        statusEl.textContent = status + ' ' + statusText;
        statusEl.className = 'response-status ' + (isOk ? 'status-ok' : 'status-error');
        
        if (typeof addConsoleEntry === 'function') {
            const logType = isOk ? 'success' : 'error';
            addConsoleEntry(logType, currentEndpoint.method + ' ' + targetUrl + ' → ' + status + ' ' + statusText);
        }
        
        if (responseContentType.includes('json') && bodyText) {
            try {
                bodyText = JSON.stringify(JSON.parse(bodyText), null, 2);
            } catch (e) {}
        }
        
        console.group('%c API Response', isOk ? 'color: #00d26a; font-weight: bold;' : 'color: #e94560; font-weight: bold;');
        console.log('%c Status:', 'font-weight: bold;', status, statusText);
        console.log('%c Content-Type:', 'font-weight: bold;', responseContentType);
        console.log('%c Body:', 'font-weight: bold;', bodyText || '(empty)');
        console.groupEnd();
        
        const responseBody = document.getElementById('response-body');
        responseBody.textContent = bodyText || '(empty response)';
        responseBody.className = responseContentType.includes('xml') ? 'language-xml' : 'language-json';
        
        if (typeof Prism !== 'undefined') {
            Prism.highlightElement(responseBody);
        }
        
        document.getElementById('response-section').style.display = 'block';
        
        const copyIdBtn = document.getElementById('copy-subscription-id-btn');
        if (copyIdBtn) {
            copyIdBtn.style.display = 'none';
            if (proxyResult.body && typeof proxyResult.body === 'string' && proxyResult.body.trim()) {
                try {
                    const parsed = JSON.parse(proxyResult.body);
                    if (parsed && (parsed.subscription_id || parsed.subscriptionId)) {
                        copyIdBtn.style.display = 'inline-block';
                        copyIdBtn.dataset.subscriptionId = parsed.subscription_id || parsed.subscriptionId;
                    }
                } catch (e) {}
            }
        }
    } catch (error) {
        console.error('%c API Error', 'color: #e94560; font-weight: bold;', error);
        showMessage('Request failed: ' + error.message, 'error');
    }
};

window.copySubscriptionId = function() {
    const btn = document.getElementById('copy-subscription-id-btn');
    const subscriptionId = btn?.dataset?.subscriptionId;
    if (subscriptionId) {
        navigator.clipboard.writeText(subscriptionId).then(() => {
            showMessage('Copied: ' + subscriptionId, 'success');
        }).catch(() => {
            showMessage('Failed to copy', 'error');
        });
    }
};

window.clearResponse = function() {
    document.getElementById('response-section').style.display = 'none';
    const copyIdBtn = document.getElementById('copy-subscription-id-btn');
    if (copyIdBtn) copyIdBtn.style.display = 'none';
};

function showMessage(message, type) {
    const container = document.getElementById('message-container');
    const msg = document.createElement('div');
    msg.className = 'message message-' + type;
    msg.textContent = message;
    container.appendChild(msg);
    setTimeout(() => msg.remove(), 5000);
    
    if (typeof addConsoleEntry === 'function') {
        addConsoleEntry(type, message);
    }
}

document.addEventListener('DOMContentLoaded', function() {
    initApiClient();
    
    document.querySelectorAll('.swagger-endpoint').forEach(el => {
        el.addEventListener('click', () => selectEndpoint(el.dataset.id));
    });
});

