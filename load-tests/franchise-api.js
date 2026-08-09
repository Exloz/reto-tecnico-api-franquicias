import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const requestErrors = new Rate('request_errors');

export const options = {
    scenarios: {
        mixedApiTraffic: {
            executor: 'constant-vus',
            vus: Number(__ENV.VUS || 20),
            duration: __ENV.DURATION || '2m',
            gracefulStop: '10s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
        request_errors: ['rate<0.01'],
    },
};

let branchId;
let productId;
let productVersion;
let stock = 1;
let renameSequence = 0;

export function setup() {
    const response = http.post(
        `${baseUrl}/api/v1/franchises`,
        JSON.stringify({ name: `Load Franchise ${Date.now()}` }),
        jsonParams('setup'),
    );
    requireStatus(response, 201, 'create franchise');
    return { franchiseId: response.json('id') };
}

export default function (data) {
    ensureResources(data.franchiseId);
    const operation = Math.random();

    if (operation < 0.5) {
        readTopStock(data.franchiseId);
    } else if (operation < 0.75) {
        updateStock(data.franchiseId);
    } else if (operation < 0.9) {
        renameProduct(data.franchiseId);
    } else {
        createAndDeleteProduct(data.franchiseId);
    }
}

function ensureResources(franchiseId) {
    if (branchId) {
        return;
    }

    const branchResponse = http.post(
        `${baseUrl}/api/v1/franchises/${franchiseId}/branches`,
        JSON.stringify({ name: `Load Branch ${__VU}` }),
        jsonParams('branch'),
    );
    requireStatus(branchResponse, 201, 'create branch');
    branchId = branchResponse.json('id');

    const productResponse = http.post(
        `${baseUrl}/api/v1/franchises/${franchiseId}/branches/${branchId}/products`,
        JSON.stringify({ name: `Load Product ${__VU}`, stock }),
        jsonParams('product'),
    );
    requireStatus(productResponse, 201, 'create product');
    productId = productResponse.json('id');
    productVersion = productResponse.json('version');
}

function readTopStock(franchiseId) {
    const response = http.get(
        `${baseUrl}/api/v1/franchises/${franchiseId}/branches/top-stock-products?limit=100`,
        requestParams('top-stock'),
    );
    requireStatus(response, 200, 'read top stock');
}

function updateStock(franchiseId) {
    stock += 1;
    const response = http.patch(
        `${baseUrl}/api/v1/franchises/${franchiseId}/branches/${branchId}/products/${productId}/stock`,
        JSON.stringify({ stock }),
        versionedParams('stock', productVersion),
    );
    requireStatus(response, 200, 'update stock');
    productVersion = response.json('version');
}

function renameProduct(franchiseId) {
    renameSequence += 1;
    const response = http.patch(
        `${baseUrl}/api/v1/franchises/${franchiseId}/branches/${branchId}/products/${productId}`,
        JSON.stringify({ name: `Load Product ${__VU} ${renameSequence}` }),
        versionedParams('rename', productVersion),
    );
    requireStatus(response, 200, 'rename product');
    productVersion = response.json('version');
}

function createAndDeleteProduct(franchiseId) {
    const response = http.post(
        `${baseUrl}/api/v1/franchises/${franchiseId}/branches/${branchId}/products`,
        JSON.stringify({ name: `Transient ${__VU} ${__ITER}`, stock: 0 }),
        jsonParams('transient-create'),
    );
    requireStatus(response, 201, 'create transient product');

    const deletion = http.del(
        `${baseUrl}/api/v1/franchises/${franchiseId}/branches/${branchId}/products/${response.json('id')}`,
        null,
        versionedParams('transient-delete', response.json('version')),
    );
    requireStatus(deletion, 204, 'delete transient product');
}

function requireStatus(response, status, operation) {
    const accepted = check(response, {
        [`${operation} returns ${status}`]: result => result.status === status,
    });
    requestErrors.add(!accepted);
}

function jsonParams(operation) {
    return {
        headers: {
            'Content-Type': 'application/json',
            'X-Correlation-ID': correlationId(operation),
        },
    };
}

function versionedParams(operation, version) {
    const params = jsonParams(operation);
    params.headers['If-Match'] = `"${version}"`;
    return params;
}

function requestParams(operation) {
    return { headers: { 'X-Correlation-ID': correlationId(operation) } };
}

function correlationId(operation) {
    const virtualUser = typeof __VU === 'undefined' ? 0 : __VU;
    const iteration = typeof __ITER === 'undefined' ? 0 : __ITER;
    return `load-${virtualUser}-${iteration}-${operation}`;
}
