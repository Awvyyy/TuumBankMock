import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || "30s";
const COUNTRY = __ENV.COUNTRY || "EE";
const CURRENCY = __ENV.CURRENCY || "EUR";
const DIRECTION = __ENV.DIRECTION || "IN";
const AMOUNT = Number(__ENV.AMOUNT || 1.0);
const INITIAL_BALANCE = Number(__ENV.INITIAL_BALANCE || 100000.0);
const SLEEP_MS = Number(__ENV.SLEEP_MS || 0);

const successfulTransactions = new Counter("successful_transactions");
const failedTransactions = new Counter("failed_transactions");

export const options = {
  scenarios: {
    tps_test: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
    },
  },
};

function jsonHeaders() {
  return {
    headers: {
      "Content-Type": "application/json",
    },
  };
}

function parseDurationToSeconds(duration) {
  if (duration.endsWith("ms")) return Number(duration.replace("ms", "")) / 1000;
  if (duration.endsWith("s")) return Number(duration.replace("s", ""));
  if (duration.endsWith("m")) return Number(duration.replace("m", "")) * 60;
  if (duration.endsWith("h")) return Number(duration.replace("h", "")) * 3600;
  return Number(duration);
}

function uniqueKey(prefix) {
  return `${prefix}-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function createAccount(customerId) {
  const payload = JSON.stringify({
    customerId,
    country: COUNTRY,
    currencies: [CURRENCY],
  });

  const res = http.post(`${BASE_URL}/accounts`, payload, jsonHeaders());

  check(res, {
    "create account status 200": (r) => r.status === 200,
  });

  if (res.status !== 200) {
    throw new Error(`Failed to create account: ${res.status} ${res.body}`);
  }

  const body = res.json();
  return body.accountId;
}

function createTransaction(accountId, idempotencyKey, amount, direction, description) {
  const payload = JSON.stringify({
    accountId,
    idempotencyKey,
    amount,
    currency: CURRENCY,
    direction,
    description,
  });

  return http.post(`${BASE_URL}/transactions`, payload, jsonHeaders());
}

export function setup() {
  const accountIds = [];

  for (let i = 0; i < VUS; i++) {
    const customerId = 900000 + i;
    const accountId = createAccount(customerId);
    accountIds.push(accountId);

    if (DIRECTION === "OUT") {
      const depositRes = createTransaction(
        accountId,
        uniqueKey("setup-deposit"),
        INITIAL_BALANCE,
        "IN",
        "k6 setup deposit"
      );

      if (depositRes.status !== 200) {
        throw new Error(`Failed to seed balance for account ${accountId}: ${depositRes.status} ${depositRes.body}`);
      }
    }
  }

  return { accountIds };
}

export default function (data) {
  const accountId = data.accountIds[(__VU - 1) % data.accountIds.length];

  const res = createTransaction(
    accountId,
    uniqueKey("k6"),
    AMOUNT,
    DIRECTION,
    "k6 load test"
  );

  const ok = check(res, {
    "transaction status 200": (r) => r.status === 200,
  });

  if (ok) {
    successfulTransactions.add(1);
  } else {
    failedTransactions.add(1);
  }

  if (SLEEP_MS > 0) {
    sleep(SLEEP_MS / 1000);
  }
}

export function handleSummary(data) {
  const durationSeconds = parseDurationToSeconds(DURATION);
  const successCount = data.metrics.successful_transactions?.values?.count || 0;
  const failedCount = data.metrics.failed_transactions?.values?.count || 0;
  const tps = durationSeconds > 0 ? successCount / durationSeconds : 0;

  const lines = [
    "",
    "===== K6 TPS SUMMARY =====",
    `Base URL: ${BASE_URL}`,
    `VUs: ${VUS}`,
    `Duration: ${DURATION}`,
    `Direction: ${DIRECTION}`,
    `Amount: ${AMOUNT}`,
    `Currency: ${CURRENCY}`,
    `Successful transactions: ${successCount}`,
    `Failed transactions: ${failedCount}`,
    `Approx TPS: ${tps.toFixed(2)}`,
    "==========================",
    "",
  ];

  return {
    stdout: lines.join("\n"),
  };
}