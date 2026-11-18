import {
  ref,
  get,
  set,
  update,
  remove,
  push,
  query,
  orderByChild,
  equalTo,
  onValue,
  Unsubscribe
} from 'firebase/database';
import { firebaseApp, firebaseConfig, firebaseDb } from './firebaseConfig';

/* ========================================================
   Firebase RTDB Configuration (Unified with User's Config)
   ======================================================== */
export const subscriptionFirebaseConfig = firebaseConfig;
export const subscriptionApp = firebaseApp;
export const subscriptionDb = firebaseDb;

export const HOME_URL = "go:paid"; // Redirect link on success for native WebView
export const RAZORPAY_KEY_ID = "rzp_live_T2PygvcB7moN7R";

/* ========================================================
   Subscription & Payment User Agent (Forces Real Android Chrome for UPI App Availability)
   ======================================================== */
export const SUBSCRIPTION_USER_AGENT =
  "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36";

export function applySubscriptionUserAgent(): void {
  try {
    if (typeof window !== 'undefined' && typeof navigator !== 'undefined') {
      try {
        Object.defineProperty(navigator, 'userAgent', {
          get: () => SUBSCRIPTION_USER_AGENT,
          configurable: true
        });
      } catch (e) {
        try {
          (navigator as any).__defineGetter__('userAgent', () => SUBSCRIPTION_USER_AGENT);
        } catch (err) {}
      }
      try {
        Object.defineProperty(navigator, 'appVersion', {
          get: () => "5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36",
          configurable: true
        });
      } catch (e) {}
      try {
        Object.defineProperty(navigator, 'platform', {
          get: () => "Linux armv8l",
          configurable: true
        });
      } catch (e) {}
    }
  } catch (err) {
    console.warn("Could not apply subscription user agent:", err);
  }

  try {
    if (typeof window !== 'undefined' && (window as any).Android) {
      if (typeof (window as any).Android.applySubscriptionUserAgent === 'function') {
        (window as any).Android.applySubscriptionUserAgent();
      }
    }
  } catch (e) {}
}

if (typeof window !== 'undefined') {
  applySubscriptionUserAgent();
}

/* ========================================================
   Device ID Generator (Single-Device Enforcement)
   ======================================================== */
export function getDeviceId(): string {
  let id = typeof localStorage !== 'undefined' ? localStorage.getItem("device_id") : null;
  if (!id) {
    id = "dev_" + Math.random().toString(36).slice(2, 12);
    try {
      localStorage.setItem("device_id", id);
    } catch {}
  }
  return id;
}

/* ========================================================
   Netflix-Style Pricing Plans
   ======================================================== */
export interface SubscriptionPlan {
  id: string;
  name: string;
  price: number;
  days: number;
  durationText: string;
  quality: string;
  resolution: string;
  deviceLimitText: string;
  popular?: boolean;
  features: string[];
  daysDuration?: number;
  text?: string;
  link?: string;
}

export const SUBSCRIPTION_PLANS: SubscriptionPlan[] = [
  {
    id: "basic",
    name: "Basic Plan",
    price: 20.00,
    days: 2,
    durationText: "2 Day(s)",
    quality: "Good",
    resolution: "720p (HD)",
    deviceLimitText: "Device Limit - 1",
    daysDuration: 2,
    features: [
      "All Movies & Web Series Unlocked",
      "720p HD Streaming",
      "100% Ad-Free Experience",
      "1 Supported Device"
    ],
    text: "Basic Plan — ₹20.00 (2 Days)"
  },
  {
    id: "standard",
    name: "Standard Plan",
    price: 70.99,
    days: 30,
    durationText: "1 Month(s)",
    quality: "Great",
    resolution: "1080p (Full HD)",
    deviceLimitText: "Device Limit - 1",
    popular: true,
    daysDuration: 30,
    features: [
      "All Movies, Web Series & Live Streams",
      "1080p Full HD Streaming",
      "100% Ad-Free + Priority Servers",
      "1 Supported Device"
    ],
    text: "Standard Plan — ₹70.99 (1 Month)"
  },
  {
    id: "platinum",
    name: "Platinum Plan",
    price: 500.00,
    days: 180,
    durationText: "6 Month(s)",
    quality: "Best",
    resolution: "4K (Ultra HD) + HDR",
    deviceLimitText: "Device Limit - 1",
    daysDuration: 180,
    features: [
      "All Movies, Series & Music Ad-Free",
      "4K Ultra HD + HDR Streaming",
      "All VIP Extra Tools Included",
      "6 Months Uninterrupted Access"
    ],
    text: "Platinum Plan — ₹500.00 (6 Months)"
  },
  {
    id: "diamond",
    name: "Diamond Plan",
    price: 800.00,
    days: 365,
    durationText: "1 Year(s)",
    quality: "Ultimate",
    resolution: "4K (Ultra HD) + HDR",
    deviceLimitText: "Device Limit - 1",
    daysDuration: 365,
    features: [
      "Full 1 Year VIP Membership",
      "4K Ultra HD + HDR Fast Servers",
      "100% Ad-Free Movies, Music & Tools",
      "Maximum Annual Savings"
    ],
    text: "Diamond Plan — ₹800.00 (1 Year)"
  }
];

export interface ActiveSubscriptionResult {
  isSubscribed: boolean;
  expiryDate: Date | null;
  daysRemaining: number;
  hoursRemaining?: number;
  planName?: string;
  quality?: string;
  subscription?: any;
}

const SUB_CACHE_KEY = "vdosky_subscription_cache";
const VIP_FLAG_KEY = "vdosky_is_vip";

function syncSubscriptionCache(result: ActiveSubscriptionResult): void {
  try {
    if (typeof localStorage !== 'undefined') {
      if (result.isSubscribed) {
        localStorage.setItem(VIP_FLAG_KEY, "true");
        localStorage.setItem(
          SUB_CACHE_KEY,
          JSON.stringify({
            isSubscribed: true,
            expireAt: result.expiryDate ? result.expiryDate.getTime() : 0,
            planName: result.planName || "VIP Plan",
            quality: result.quality || "1080p Full HD"
          })
        );
      } else {
        localStorage.removeItem(VIP_FLAG_KEY);
        localStorage.removeItem(SUB_CACHE_KEY);
      }
    }
  } catch {}

  if (typeof window !== 'undefined') {
    (window as any).__isVipSubscribed = result.isSubscribed;
    (window as any).__hasActiveSubscription = result.isSubscribed;
    (window as any).__vipSubscriptionDetails = result;
  }
}

function evaluateSubscriptionsSnapshot(data: Record<string, any> | null, deviceId: string): {
  result: ActiveSubscriptionResult;
  expiredIds: string[];
  activeIds: string[];
} {
  const now = Date.now();
  const expiredIds: string[] = [];
  const activeIds: string[] = [];
  let activeSub: any = null;

  if (data) {
    for (const id in data) {
      const s = data[id];
      if (!s) continue;
      if (s.device_id !== deviceId) continue;

      const expireAt = Number(s.expireAt || 0);
      if (!expireAt || now >= expireAt) {
        expiredIds.push(id);
        continue;
      }

      activeIds.push(id);
      if (!activeSub || expireAt > Number(activeSub.expireAt || 0)) {
        activeSub = { ...s, _key: id };
      }
    }
  }

  if (activeSub) {
    const matchedPlan = SUBSCRIPTION_PLANS.find((p) => p.days === Number(activeSub.validityDays));
    const msRemaining = Math.max(0, Number(activeSub.expireAt) - now);
    const daysRemaining = Math.max(1, Math.ceil(msRemaining / 86400000));
    const hoursRemaining = Math.max(1, Math.ceil(msRemaining / 3600000));
    const expiryDate = new Date(Number(activeSub.expireAt));
    const result: ActiveSubscriptionResult = {
      isSubscribed: true,
      expiryDate,
      daysRemaining,
      hoursRemaining,
      planName: activeSub.planName || matchedPlan?.name || (activeSub.validityDays ? `${activeSub.validityDays} Days Plan` : "Netflix VIP Plan"),
      quality: activeSub.quality || matchedPlan?.resolution || "1080p (Full HD)",
      subscription: activeSub
    };
    return { result, expiredIds, activeIds };
  }

  return {
    result: { isSubscribed: false, expiryDate: null, daysRemaining: 0 },
    expiredIds,
    activeIds
  };
}

async function deleteExpiredDeviceRecordsFromFirebase(
  deviceId: string,
  subIds: string[] = [],
  removeDeviceNode: boolean = false
): Promise<void> {
  const tasks: Promise<any>[] = [];

  for (const id of subIds) {
    if (id) {
      tasks.push(remove(ref(subscriptionDb, `subscriptions/${id}`)).catch(() => {}));
    }
  }

  if (removeDeviceNode && deviceId) {
    tasks.push(remove(ref(subscriptionDb, `devices/${deviceId}`)).catch(() => {}));
  }

  if (tasks.length > 0) {
    await Promise.all(tasks).catch(() => {});
  }
}

async function purgeAllExpiredSubscriptionsForDevice(deviceId: string): Promise<void> {
  try {
    const subsQuery = query(
      ref(subscriptionDb, "subscriptions"),
      orderByChild("device_id"),
      equalTo(deviceId)
    );
    const snap = await get(subsQuery);
    const data = snap.val();
    const now = Date.now();
    const toDelete: string[] = [];
    let hasStillValid = false;

    if (data) {
      for (const id of Object.keys(data)) {
        const s = data[id];
        if (!s || s.device_id !== deviceId) continue;
        const expireAt = Number(s.expireAt || 0);
        if (!expireAt || now >= expireAt) {
          toDelete.push(id);
        } else {
          hasStillValid = true;
        }
      }
    }

    await deleteExpiredDeviceRecordsFromFirebase(deviceId, toDelete, !hasStillValid);
  } catch {
    await remove(ref(subscriptionDb, `devices/${deviceId}`)).catch(() => {});
  }
}

/* ========================================================
   Check Active Subscription in Firebase RTDB
   - Queries /subscriptions where device_id == current device
   - Falls back to /devices/{deviceId} (enforces RTDB expireAt rule)
   - Auto-deletes expired items from /subscriptions and /devices
   ======================================================== */
export async function checkActiveSubscription(): Promise<ActiveSubscriptionResult> {
  const deviceId = getDeviceId();
  try {
    const subsQuery = query(
      ref(subscriptionDb, "subscriptions"),
      orderByChild("device_id"),
      equalTo(deviceId)
    );
    const snap = await get(subsQuery);
    const data = snap.val();

    const { result, expiredIds } = evaluateSubscriptionsSnapshot(data, deviceId);

    // Cleanup any expired subscription records for this device
    if (expiredIds.length > 0) {
      await deleteExpiredDeviceRecordsFromFirebase(deviceId, expiredIds, false);
    }

    if (result.isSubscribed) {
      syncSubscriptionCache(result);
      return result;
    }

    // Secondary verification from /devices/{deviceId} (protected by "now < data.child('expireAt').val()" rule)
    let shouldDeleteDeviceNode = expiredIds.length > 0;
    try {
      const devSnap = await get(ref(subscriptionDb, `devices/${deviceId}`));
      const devData = devSnap.val();
      const now = Date.now();
      if (devData) {
        if (Number(devData.expireAt || 0) > now) {
          const matchedPlan = SUBSCRIPTION_PLANS.find((p) => p.days === Number(devData.validityDays));
          const msRemaining = Number(devData.expireAt) - now;
          const devResult: ActiveSubscriptionResult = {
            isSubscribed: true,
            expiryDate: new Date(Number(devData.expireAt)),
            daysRemaining: Math.max(1, Math.ceil(msRemaining / 86400000)),
            hoursRemaining: Math.max(1, Math.ceil(msRemaining / 3600000)),
            planName: devData.planName || matchedPlan?.name || (devData.validityDays ? `${devData.validityDays} Days Plan` : "VIP Plan"),
            quality: devData.quality || matchedPlan?.resolution || "1080p (Full HD)",
            subscription: devData
          };
          syncSubscriptionCache(devResult);
          return devResult;
        }
        shouldDeleteDeviceNode = true;
      }
    } catch {
      // Permission denied on /devices/{deviceId} means expired or missing per Firebase RTDB rules
      shouldDeleteDeviceNode = true;
    }

    if (shouldDeleteDeviceNode) {
      await remove(ref(subscriptionDb, `devices/${deviceId}`)).catch(() => {});
    }

    syncSubscriptionCache({ isSubscribed: false, expiryDate: null, daysRemaining: 0 });
    return { isSubscribed: false, expiryDate: null, daysRemaining: 0 };
  } catch (error) {
    console.error("Subscription check error:", error);
    const cached = getCachedIsSubscribed();
    return { isSubscribed: cached, expiryDate: null, daysRemaining: 0 };
  }
}

/* ========================================================
   Live Firebase RTDB Subscription Listener (onValue)
   - Immediately notifies app when subscription is added, extended, or revoked
   - Schedules exact auto-expiry watcher so when expireAt is reached,
     user data is auto-deleted from Firebase RTDB and subscription UI shows again
   ======================================================== */
export function subscribeToDeviceSubscription(
  callback: (result: ActiveSubscriptionResult) => void
): Unsubscribe {
  const deviceId = getDeviceId();
  const subsQuery = query(
    ref(subscriptionDb, "subscriptions"),
    orderByChild("device_id"),
    equalTo(deviceId)
  );

  let expiryTimer: ReturnType<typeof setTimeout> | null = null;
  let trackedExpireAt = 0;
  let trackedActiveIds: string[] = [];
  let hadActiveSub = false;
  let isDisposed = false;
  let isHandlingExpiry = false;

  const clearExpiryTimer = () => {
    if (expiryTimer !== null) {
      clearTimeout(expiryTimer);
      expiryTimer = null;
    }
  };

  const triggerLiveExpiryCleanup = async () => {
    if (isDisposed || isHandlingExpiry) return;
    isHandlingExpiry = true;
    clearExpiryTimer();
    const idsToCleanup = [...trackedActiveIds];
    trackedExpireAt = 0;
    trackedActiveIds = [];
    hadActiveSub = false;

    const expiredResult: ActiveSubscriptionResult = {
      isSubscribed: false,
      expiryDate: null,
      daysRemaining: 0
    };

    try {
      syncSubscriptionCache(expiredResult);
      callback(expiredResult);

      if (idsToCleanup.length > 0) {
        await deleteExpiredDeviceRecordsFromFirebase(deviceId, idsToCleanup, true);
      }
      await purgeAllExpiredSubscriptionsForDevice(deviceId);
    } finally {
      isHandlingExpiry = false;
    }
  };

  const scheduleExpiryWatcher = (expireAt: number, activeIds: string[]) => {
    clearExpiryTimer();
    trackedExpireAt = expireAt;
    trackedActiveIds = activeIds;

    const remainingMs = expireAt - Date.now();
    if (remainingMs <= 0) {
      void triggerLiveExpiryCleanup();
      return;
    }

    // JS setTimeout caps at 2^31 - 1 ms (~24.8 days); re-arm cleanly for longer plans
    const MAX_TIMEOUT_MS = 2147483647;
    const delay = Math.min(remainingMs + 50, MAX_TIMEOUT_MS);

    expiryTimer = setTimeout(() => {
      if (isDisposed) return;
      if (Date.now() >= expireAt) {
        void triggerLiveExpiryCleanup();
      } else {
        scheduleExpiryWatcher(expireAt, activeIds);
      }
    }, delay);
  };

  const handleWakeCheck = () => {
    if (isDisposed) return;
    if (trackedExpireAt > 0 && Date.now() >= trackedExpireAt) {
      void triggerLiveExpiryCleanup();
    }
  };

  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', handleWakeCheck);
  }
  if (typeof window !== 'undefined') {
    window.addEventListener('focus', handleWakeCheck);
  }

  const unsubOnValue = onValue(
    subsQuery,
    async (snapshot) => {
      if (isDisposed) return;
      const data = snapshot.val();
      const { result, expiredIds, activeIds } = evaluateSubscriptionsSnapshot(data, deviceId);

      if (expiredIds.length > 0) {
        void deleteExpiredDeviceRecordsFromFirebase(deviceId, expiredIds, !result.isSubscribed);
      }

      if (result.isSubscribed && result.expiryDate) {
        hadActiveSub = true;
        syncSubscriptionCache(result);
        scheduleExpiryWatcher(result.expiryDate.getTime(), activeIds);
        callback(result);
        return;
      }

      clearExpiryTimer();
      const justExpiredOrRemoved = hadActiveSub || expiredIds.length > 0;
      trackedExpireAt = 0;
      trackedActiveIds = [];
      hadActiveSub = false;

      if (justExpiredOrRemoved) {
        void deleteExpiredDeviceRecordsFromFirebase(deviceId, [], true);
      } else {
        // Fallback: check /devices/{deviceId} if no /subscriptions record was active yet
        try {
          const devSnap = await get(ref(subscriptionDb, `devices/${deviceId}`));
          if (isDisposed) return;
          const devData = devSnap.val();
          const now = Date.now();
          if (devData) {
            if (Number(devData.expireAt || 0) > now) {
              const matchedPlan = SUBSCRIPTION_PLANS.find((p) => p.days === Number(devData.validityDays));
              const msRemaining = Number(devData.expireAt) - now;
              const devResult: ActiveSubscriptionResult = {
                isSubscribed: true,
                expiryDate: new Date(Number(devData.expireAt)),
                daysRemaining: Math.max(1, Math.ceil(msRemaining / 86400000)),
                hoursRemaining: Math.max(1, Math.ceil(msRemaining / 3600000)),
                planName: devData.planName || matchedPlan?.name || (devData.validityDays ? `${devData.validityDays} Days Plan` : "VIP Plan"),
                quality: devData.quality || matchedPlan?.resolution || "1080p (Full HD)",
                subscription: devData
              };
              hadActiveSub = true;
              syncSubscriptionCache(devResult);
              scheduleExpiryWatcher(Number(devData.expireAt), []);
              callback(devResult);
              return;
            }
            void remove(ref(subscriptionDb, `devices/${deviceId}`)).catch(() => {});
          }
        } catch {
          // Permission denied means expired or missing per RTDB rules
          void remove(ref(subscriptionDb, `devices/${deviceId}`)).catch(() => {});
        }
      }

      syncSubscriptionCache(result);
      callback(result);
    },
    (error) => {
      console.warn("[Subscription] Live RTDB listener error:", error);
    }
  );

  return () => {
    isDisposed = true;
    clearExpiryTimer();
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', handleWakeCheck);
    }
    if (typeof window !== 'undefined') {
      window.removeEventListener('focus', handleWakeCheck);
    }
    unsubOnValue();
  };
}

/* ========================================================
   Save Subscription to Firebase RTDB (/subscriptions & /devices)
   ======================================================== */
export interface SaveSubscriptionOptions {
  planName?: string;
  price?: number;
  quality?: string;
  paymentMethod?: 'razorpay' | 'activation_key' | 'admin';
  paymentId?: string;
  customerContact?: string;
}

export async function saveSubscriptionToFirebase(
  days: number,
  options: SaveSubscriptionOptions = {}
): Promise<ActiveSubscriptionResult> {
  const now = Date.now();
  const deviceId = getDeviceId();
  const matchedPlan = SUBSCRIPTION_PLANS.find((p) => p.days === days);

  const planName = options.planName || matchedPlan?.name || `${days} Days VIP Plan`;
  const quality = options.quality || matchedPlan?.resolution || "1080p (Full HD)";
  const expireAt = now + days * 86400000;
  const activationPassword = Math.random().toString(36).slice(2, 13);

  // Remove any previous expired/old entries for this device before writing fresh active record
  try {
    const existingQuery = query(
      ref(subscriptionDb, "subscriptions"),
      orderByChild("device_id"),
      equalTo(deviceId)
    );
    const existingSnap = await get(existingQuery);
    const existingData = existingSnap.val();
    if (existingData) {
      for (const oldKey of Object.keys(existingData)) {
        const oldEntry = existingData[oldKey];
        if (!oldEntry || Number(oldEntry.expireAt || 0) <= now) {
          await remove(ref(subscriptionDb, "subscriptions/" + oldKey)).catch(() => {});
        }
      }
    }
  } catch {}

  // Compact 6-field schema to keep Firebase Realtime Database minimal & clean
  const payload = {
    createdAt: now,
    device_id: deviceId,
    expireAt,
    password: activationPassword,
    used: true,
    validityDays: days
  };

  const newRef = push(ref(subscriptionDb, "subscriptions"));
  await update(newRef, payload);

  // Minimal /devices/{deviceId} entry for RTDB expiry rule ("now < data.child('expireAt').val()")
  await set(ref(subscriptionDb, `devices/${deviceId}`), {
    expireAt,
    validityDays: days
  }).catch(() => {});

  const activeResult: ActiveSubscriptionResult = {
    isSubscribed: true,
    expiryDate: new Date(expireAt),
    daysRemaining: days,
    hoursRemaining: days * 24,
    planName,
    quality,
    subscription: { ...payload, _key: newRef.key }
  };

  syncSubscriptionCache(activeResult);
  return activeResult;
}

/* ========================================================
   Activation Key / OTP Pool System (/otp_pool)
   - Allows Admin to generate keys and users to redeem them
   ======================================================== */
export async function generateActivationKey(
  days: number,
  planName?: string
): Promise<{ otp: string; days: number; planName: string }> {
  const randomCode =
    "NFLX-" +
    Math.random().toString(36).substring(2, 6).toUpperCase() +
    "-" +
    Math.random().toString(36).substring(2, 6).toUpperCase();

  const resolvedPlan =
    planName ||
    SUBSCRIPTION_PLANS.find((p) => p.days === days)?.name ||
    `${days} Days VIP Plan`;

  const keyRef = push(ref(subscriptionDb, "otp_pool"));
  await set(keyRef, {
    otp: randomCode,
    days,
    planName: resolvedPlan,
    used: false,
    createdAt: Date.now(),
    usedBy: "",
    usedAt: 0
  });

  return { otp: randomCode, days, planName: resolvedPlan };
}

export async function redeemActivationKey(rawCode: string): Promise<{
  success: boolean;
  message: string;
  result?: ActiveSubscriptionResult;
}> {
  const code = String(rawCode || "").trim().toUpperCase();
  if (!code || code.length < 4) {
    return { success: false, message: "Please enter a valid activation code." };
  }

  try {
    const otpQuery = query(
      ref(subscriptionDb, "otp_pool"),
      orderByChild("otp"),
      equalTo(code)
    );
    const snap = await get(otpQuery);
    const data = snap.val();

    if (!data) {
      return { success: false, message: "Invalid activation code. Please check and try again." };
    }

    const keys = Object.keys(data);
    const matchedKey = keys[0];
    const record = data[matchedKey];

    if (!record) {
      return { success: false, message: "Activation code not found." };
    }
    if (record.used === true) {
      return { success: false, message: "This activation code has already been redeemed." };
    }

    const days = Number(record.days || 30);
    const planName = record.planName || `${days} Days VIP Plan`;
    const deviceId = getDeviceId();

    await update(ref(subscriptionDb, `otp_pool/${matchedKey}`), {
      used: true,
      usedBy: deviceId,
      usedAt: Date.now()
    });

    const result = await saveSubscriptionToFirebase(days, {
      planName,
      paymentMethod: 'activation_key',
      paymentId: code
    });

    return {
      success: true,
      message: `${planName} (${days} days) activated!`,
      result
    };
  } catch (err: any) {
    console.error("Redeem activation key failed:", err);
    return {
      success: false,
      message: err?.message || "Could not verify activation code. Check your internet connection."
    };
  }
}

/* ========================================================
   Admin Subscription Management Helpers
   ======================================================== */
export async function listAllSubscriptions(): Promise<Array<{ id: string; [key: string]: any }>> {
  try {
    const snap = await get(ref(subscriptionDb, "subscriptions"));
    const data = snap.val();
    if (!data) return [];
    const now = Date.now();
    const list: Array<{ id: string; [key: string]: any }> = [];
    for (const id of Object.keys(data)) {
      const item = data[id];
      if (!item) continue;
      if (Number(item.expireAt || 0) <= now) {
        remove(ref(subscriptionDb, `subscriptions/${id}`)).catch(() => {});
        continue;
      }
      list.push({ id, ...item });
    }
    list.sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
    return list;
  } catch (err) {
    console.warn("Could not list subscriptions:", err);
    return [];
  }
}

export async function revokeSubscription(subId: string, targetDeviceId?: string): Promise<void> {
  if (subId) {
    await remove(ref(subscriptionDb, `subscriptions/${subId}`));
  }
  if (targetDeviceId) {
    await remove(ref(subscriptionDb, `devices/${targetDeviceId}`)).catch(() => {});
    if (targetDeviceId === getDeviceId()) {
      syncSubscriptionCache({ isSubscribed: false, expiryDate: null, daysRemaining: 0 });
    }
  }
}

/* ========================================================
   Custom Alert Popup Triggers
   ======================================================== */
export function showCustomAlert(): void {
  const alertBox = document.getElementById("customAlert");
  if (alertBox) {
    alertBox.classList.add("show");
  }
}

export function hideCustomAlert(): void {
  const alertBox = document.getElementById("customAlert");
  if (alertBox) {
    alertBox.classList.remove("show");
  }
}

/* ========================================================
   Razorpay Payment & Activation
   ======================================================== */
export function payAndActivate(
  amount: number,
  days: number,
  planName: string,
  buttonEl?: HTMLElement | null,
  onSuccess?: () => void
): void {
  applySubscriptionUserAgent();

  if (buttonEl) {
    buttonEl.setAttribute("disabled", "true");
    buttonEl.textContent = "Processing...";
  }

  function launchRazorpay() {
    const RazorpayConstructor = (window as any).Razorpay;
    if (!RazorpayConstructor) {
      if (typeof (window as any).showToast === 'function') {
        (window as any).showToast('Payment system is loading, please try again in a moment.');
      } else {
        console.warn('Razorpay SDK is loading, please try again in a moment.');
      }
      if (buttonEl) {
        buttonEl.removeAttribute("disabled");
        buttonEl.textContent = "SELECT PLAN";
      }
      return;
    }

    const matchedPlan = SUBSCRIPTION_PLANS.find((p) => p.name === planName || p.days === days);

    const options = {
      key: RAZORPAY_KEY_ID,
      amount: Math.round(amount * 100),
      currency: "INR",
      name: planName,
      description: `Netflix VIP Membership • ${days} Days (${matchedPlan?.resolution || 'HD'})`,
      handler: async function (response: any) {
        if (response && response.razorpay_payment_id) {
          try {
            await saveSubscriptionToFirebase(days, {
              planName,
              price: amount,
              quality: matchedPlan?.resolution || "1080p (Full HD)",
              paymentMethod: 'razorpay',
              paymentId: response.razorpay_payment_id
            });
            showCustomAlert();

            if (typeof (window as any).updateVipAdSuppression === 'function') {
              (window as any).updateVipAdSuppression();
            }

            if (onSuccess) {
              onSuccess();
            }

            setTimeout(() => {
              hideCustomAlert();
              try {
                if (typeof window !== 'undefined' && window.location.href.startsWith("http")) {
                  if (typeof (window as any).goHome === 'function') {
                    (window as any).goHome();
                  } else if (typeof (window as any).handleMenuOrBack === 'function') {
                    (window as any).handleMenuOrBack();
                  } else {
                    window.location.href = HOME_URL;
                  }
                } else {
                  window.location.href = HOME_URL;
                }
              } catch (e) {
                if (typeof (window as any).handleMenuOrBack === 'function') {
                  (window as any).handleMenuOrBack();
                }
              }
            }, 1800);
          } catch (e) {
            console.error("Database Error:", e);
            if (typeof (window as any).showToast === 'function') {
              (window as any).showToast("Database Error! Contact Support.");
            }
            if (buttonEl) {
              buttonEl.removeAttribute("disabled");
              buttonEl.textContent = "SELECT PLAN";
            }
          }
        }
      },
      modal: {
        ondismiss: function () {
          if (buttonEl) {
            buttonEl.removeAttribute("disabled");
            buttonEl.textContent = "SELECT PLAN";
          }
        }
      },
      theme: {
        color: "#e50914"
      }
    };

    const rzp = new RazorpayConstructor(options);
    rzp.open();
  }

  if (typeof (window as any).Razorpay === 'undefined') {
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => launchRazorpay();
    script.onerror = () => {
      if (typeof (window as any).showToast === 'function') {
        (window as any).showToast("Failed to load Razorpay payment gateway. Please check your network connection.");
      } else {
        console.warn("Failed to load Razorpay payment gateway. Please check your network connection.");
      }
      if (buttonEl) {
        buttonEl.removeAttribute("disabled");
        buttonEl.textContent = "SELECT PLAN";
      }
    };
    document.head.appendChild(script);
  } else {
    launchRazorpay();
  }
}

// Attach globally for inline HTML onclick handlers
if (typeof window !== 'undefined') {
  (window as any).payAndActivate = payAndActivate;
  (window as any).checkActiveSubscription = checkActiveSubscription;
  (window as any).subscribeToDeviceSubscription = subscribeToDeviceSubscription;
  (window as any).saveSubscriptionToFirebase = saveSubscriptionToFirebase;
  (window as any).redeemActivationKey = redeemActivationKey;
  (window as any).generateActivationKey = generateActivationKey;
  (window as any).showCustomAlert = showCustomAlert;
  (window as any).hideCustomAlert = hideCustomAlert;
  (window as any).applySubscriptionUserAgent = applySubscriptionUserAgent;
  (window as any).SUBSCRIPTION_USER_AGENT = SUBSCRIPTION_USER_AGENT;
}

/* ========================================================
   Backward Compatibility & Cache Helpers
   ======================================================== */
export function getCachedIsSubscribed(): boolean {
  if (typeof localStorage === 'undefined') return false;
  try {
    const cacheRaw = localStorage.getItem(SUB_CACHE_KEY);
    if (cacheRaw) {
      const parsed = JSON.parse(cacheRaw);
      if (parsed && parsed.expireAt) {
        if (Date.now() < Number(parsed.expireAt)) {
          return true;
        }
        // Expired locally — clear flags immediately
        localStorage.removeItem(VIP_FLAG_KEY);
        localStorage.removeItem(SUB_CACHE_KEY);
        return false;
      }
    }
  } catch {}
  return localStorage.getItem(VIP_FLAG_KEY) === "true";
}

export function getStoredSafeId(): string {
  return getDeviceId();
}

export function getStoredUserIdentifier(): string | null {
  return getDeviceId();
}

export function sanitizeSubscriptionId(rawId: string): string {
  return rawId ? rawId.trim().replace(/[^a-zA-Z0-9_-]/g, '_') : getDeviceId();
}

export async function checkAndHandleSubscriptionExpiry(_safeId?: string) {
  const res = await checkActiveSubscription();
  return {
    isSubscribed: res.isSubscribed,
    userData: null,
    expiredJustNow: false,
    daysRemaining: res.daysRemaining,
    expiryDate: res.expiryDate,
    deviceMismatched: false,
    sessionMismatched: false
  };
}

export function listenToUserBalance(_safeId: string, callback: (balance: number) => void): Unsubscribe {
  callback(0);
  return () => {};
}

export async function authenticateSubscriptionUser(
  _type: 'email' | 'mobile',
  _rawId: string,
  _pin: string
) {
  return { success: true, user: { identifier: getDeviceId(), balance: 0 } };
}

export async function loginOrRegisterSubscriptionUser(
  _rawId: string,
  _pin: string
) {
  return { success: true, user: { identifier: getDeviceId(), balance: 0 } };
}

export async function paySubscriptionFromWallet(
  _safeId: string,
  plan: SubscriptionPlan
) {
  await saveSubscriptionToFirebase(plan.days, {
    planName: plan.name,
    price: plan.price,
    quality: plan.resolution
  });
  return { success: true, plan };
}

export async function logoutSubscriptionUser() {
  syncSubscriptionCache({ isSubscribed: false, expiryDate: null, daysRemaining: 0 });
}

export function logoutSubscriptionUserLocalOnly() {
  logoutSubscriptionUser();
}

export function listenToActiveSession(_safeId: string, _onKicked: (reason: string) => void): Unsubscribe {
  return () => {};
}
