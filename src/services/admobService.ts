import { ref, get, set, onValue, Unsubscribe } from 'firebase/database';
import { firebaseDb, firebaseConfig } from './firebaseConfig';
import { AdMobConfig } from '../types';

const ADMOB_LOCAL_STORAGE_KEY = 'vdosky_admob_config';
const ADMOB_CLICK_COUNT_KEY = 'vdosky_admob_clicks';

// Default configuration structure for AdMob (Ad Units load live from Firebase RTDB /admob)
export const DEFAULT_ADMOB_CONFIG: AdMobConfig = {
  enabled: true,
  app_id: 'ca-app-pub-3440654346799700~9967202851',
  banner_ad_unit: '',
  interstitial_ad_unit: '',
  rewarded_ad_unit: '',
  banner_enabled: true,
  interstitial_enabled: true,
  rewarded_enabled: true,
  interstitial_frequency: 15, // 15 ta post open korar por por Rewarded Interstitial show hobe
  rewarded_interval_minutes: 11 // 10 theke 12 minute por por Rewarded video show hobe
};

const stripTestUnit = (unit?: string): string => {
  const clean = (unit || '').trim();
  return clean.startsWith('ca-app-pub-3940256099942544/') ? '' : clean;
};

let cachedAdMobConfig: AdMobConfig = (() => {
  try {
    const saved = localStorage.getItem(ADMOB_LOCAL_STORAGE_KEY);
    if (saved) {
      const parsed = JSON.parse(saved);
      return {
        ...DEFAULT_ADMOB_CONFIG,
        ...parsed,
        banner_ad_unit: stripTestUnit(parsed.banner_ad_unit),
        interstitial_ad_unit: stripTestUnit(parsed.interstitial_ad_unit),
        rewarded_ad_unit: stripTestUnit(parsed.rewarded_ad_unit),
      };
    }
  } catch (e) {
    console.warn('Failed to parse cached AdMob config', e);
  }
  return { ...DEFAULT_ADMOB_CONFIG };
})();

/**
 * Check if running inside Capacitor Android app with native bridge
 */
export function isAndroidNative(): boolean {
  return typeof window !== 'undefined' && Boolean(window.Android);
}

const toBool = (val: unknown, fallback = true): boolean => {
  if (val === undefined || val === null) return fallback;
  if (typeof val === 'boolean') return val;
  if (typeof val === 'string') return val.trim().toLowerCase() !== 'false' && val.trim() !== '0';
  return Boolean(val);
};

const pickString = (obj: Record<string, unknown>, keys: string[], fallback = ''): string => {
  for (const key of keys) {
    const val = obj[key];
    if (typeof val === 'string' && val.trim()) {
      return val.trim();
    }
  }
  return fallback;
};

/**
 * Normalize and apply remote AdMob configuration locally and to native bridge
 */
function applyRemoteAdMobConfig(remoteData: Partial<AdMobConfig> | Record<string, unknown>): {
  config: AdMobConfig;
  interstitialChanged: boolean;
  rewardedChanged: boolean;
  intervalChanged: boolean;
} {
  const prev = cachedAdMobConfig;
  const raw = remoteData as Record<string, unknown>;

  const next: AdMobConfig = {
    ...DEFAULT_ADMOB_CONFIG,
    ...(remoteData as Partial<AdMobConfig>),
    app_id: pickString(raw, ['app_id', 'appId', 'admob_app_id'], DEFAULT_ADMOB_CONFIG.app_id),
    banner_ad_unit: pickString(raw, ['banner_ad_unit', 'bannerAdUnit', 'banner_id', 'bannerId', 'banner']),
    interstitial_ad_unit: pickString(raw, [
      'interstitial_ad_unit',
      'interstitialAdUnit',
      'rewarded_interstitial_ad_unit',
      'rewarded_interstitial',
      'interstitial_id',
      'interstitialId',
      'interstitial'
    ]),
    rewarded_ad_unit: pickString(raw, ['rewarded_ad_unit', 'rewardedAdUnit', 'rewarded_id', 'rewardedId', 'rewarded']),
    enabled: toBool(raw.enabled, true),
    banner_enabled: toBool(raw.banner_enabled ?? raw.bannerEnabled, true),
    interstitial_enabled: toBool(raw.interstitial_enabled ?? raw.interstitialEnabled, true),
    rewarded_enabled: toBool(raw.rewarded_enabled ?? raw.rewardedEnabled, true),
    interstitial_frequency:
      Number(raw.interstitial_frequency ?? raw.interstitialFrequency) || DEFAULT_ADMOB_CONFIG.interstitial_frequency,
    rewarded_interval_minutes:
      Number(raw.rewarded_interval_minutes ?? raw.rewardedIntervalMinutes) ||
      DEFAULT_ADMOB_CONFIG.rewarded_interval_minutes,
  };

  const interstitialChanged =
    prev.interstitial_ad_unit !== next.interstitial_ad_unit ||
    prev.interstitial_enabled !== next.interstitial_enabled ||
    prev.enabled !== next.enabled;

  const rewardedChanged =
    prev.rewarded_ad_unit !== next.rewarded_ad_unit ||
    prev.rewarded_enabled !== next.rewarded_enabled ||
    prev.enabled !== next.enabled;

  const intervalChanged = prev.rewarded_interval_minutes !== next.rewarded_interval_minutes;

  cachedAdMobConfig = next;

  try {
    localStorage.setItem(ADMOB_LOCAL_STORAGE_KEY, JSON.stringify(cachedAdMobConfig));
  } catch (e) {
    console.warn('Failed to cache AdMob config locally', e);
  }

  // Initialize native AdMob with fetched App ID if available
  if (isAndroidNative() && cachedAdMobConfig.app_id) {
    try {
      window.Android?.initAdMob?.(cachedAdMobConfig.app_id);
    } catch (e) {
      console.warn('Failed to init AdMob with native bridge', e);
    }
  }

  return { config: cachedAdMobConfig, interstitialChanged, rewardedChanged, intervalChanged };
}

/**
 * Fetch AdMob configuration from Firebase Realtime Database
 * Path: /admob
 */
export async function fetchAdMobConfig(): Promise<AdMobConfig> {
  let remoteData: (Partial<AdMobConfig> & Record<string, unknown>) | null = null;

  // 1. First attempt: Direct Firebase RTDB REST API
  try {
    const rtdbUrl = `${firebaseConfig.databaseURL}/admob.json`;
    const res = await fetch(rtdbUrl, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      cache: 'no-store'
    });
    if (res.ok) {
      remoteData = await res.json();
    } else if (res.status === 401 || res.status === 403) {
      console.warn('[AdMob] Firebase RTDB /admob is not publicly readable yet (status ' + res.status + ').');
    }
  } catch (err) {
    console.warn('REST AdMob config fetch failed, attempting Firebase SDK ref:', err);
  }

  // 2. Second attempt: Firebase Realtime Database SDK
  if (!remoteData) {
    try {
      const configRef = ref(firebaseDb, 'admob');
      const snapshot = await get(configRef);
      if (snapshot.exists()) {
        remoteData = snapshot.val();
      }
    } catch (sdkErr) {
      console.warn('Firebase SDK AdMob config fetch failed:', sdkErr);
    }
  }

  if (remoteData && typeof remoteData === 'object') {
    applyRemoteAdMobConfig(remoteData);
  } else if (isAndroidNative() && cachedAdMobConfig.app_id) {
    try {
      window.Android?.initAdMob?.(cachedAdMobConfig.app_id);
    } catch (e) {
      console.warn('Failed to init AdMob with native bridge', e);
    }
  }

  return cachedAdMobConfig;
}

/**
 * Subscribe to live real-time AdMob configuration updates via Firebase RTDB onValue()
 * Any change in Firebase /admob immediately updates ad units & preloads new ads
 */
export function subscribeToAdMobConfig(onUpdate?: (config: AdMobConfig) => void): Unsubscribe {
  const configRef = ref(firebaseDb, 'admob');

  return onValue(
    configRef,
    (snapshot) => {
      if (snapshot.exists()) {
        const remoteData = snapshot.val() as Partial<AdMobConfig> & Record<string, unknown>;
        if (remoteData && typeof remoteData === 'object') {
          const { config, interstitialChanged, rewardedChanged, intervalChanged } =
            applyRemoteAdMobConfig(remoteData);

          if (interstitialChanged || config.interstitial_enabled) {
            preloadAdMobInterstitial();
          }
          if (rewardedChanged || config.rewarded_enabled) {
            preloadAdMobRewarded();
          }
          if (intervalChanged || !periodicRewardedTimerId) {
            startPeriodicRewardedTimer();
          }

          if (onUpdate) {
            onUpdate(config);
          }
          return;
        }
      }

      if (onUpdate) {
        onUpdate(cachedAdMobConfig);
      }
    },
    (error) => {
      console.warn('[AdMob] Live onValue listener error:', error);
    }
  );
}

/**
 * Save updated AdMob configuration to Firebase Realtime Database
 */
export async function saveAdMobConfig(newConfig: AdMobConfig): Promise<boolean> {
  let saved = false;

  // 1. Update Firebase RTDB via REST API
  try {
    const rtdbUrl = `${firebaseConfig.databaseURL}/admob.json`;
    const res = await fetch(rtdbUrl, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(newConfig)
    });
    if (res.ok) {
      saved = true;
    }
  } catch (err) {
    console.warn('REST AdMob save failed, falling back to SDK:', err);
  }

  // 2. Fallback: Firebase SDK set
  if (!saved) {
    try {
      const configRef = ref(firebaseDb, 'admob');
      await set(configRef, newConfig);
      saved = true;
    } catch (sdkErr) {
      console.warn('Firebase SDK AdMob save failed:', sdkErr);
    }
  }

  if (saved) {
    applyRemoteAdMobConfig(newConfig);

    // Refresh native AdMob if banner is visible
    if (isAndroidNative()) {
      if (newConfig.enabled && newConfig.banner_enabled && newConfig.banner_ad_unit) {
        window.Android?.showAdMobBanner?.(newConfig.banner_ad_unit);
      } else {
        window.Android?.hideAdMobBanner?.();
      }
    }
  }

  return saved;
}

/**
 * Get current cached AdMob config synchronously
 */
export function getCachedAdMobConfig(): AdMobConfig {
  return cachedAdMobConfig;
}

/**
 * Show AdMob Banner at bottom of screen
 */
export function showAdMobBanner(): void {
  if (!isAndroidNative()) return;
  const config = cachedAdMobConfig;
  if (!config.enabled || !config.banner_enabled || !config.banner_ad_unit?.trim()) {
    hideAdMobBanner();
    return;
  }
  try {
    window.Android?.showAdMobBanner?.(config.banner_ad_unit.trim());
  } catch (err) {
    console.error('Failed to show AdMob Banner', err);
  }
}

/**
 * Hide AdMob Banner
 */
export function hideAdMobBanner(): void {
  if (!isAndroidNative()) return;
  try {
    window.Android?.hideAdMobBanner?.();
  } catch (err) {
    console.error('Failed to hide AdMob Banner', err);
  }
}

/**
 * Trigger AdMob Rewarded Interstitial Ad (every 15 post clicks)
 */
export function showAdMobInterstitial(): boolean {
  if (!isAndroidNative()) return false;
  const config = cachedAdMobConfig;
  if (!config.enabled || !config.interstitial_enabled || !config.interstitial_ad_unit?.trim()) {
    return false;
  }

  try {
    if (window.Android?.showAdMobRewardedInterstitial) {
      window.Android.showAdMobRewardedInterstitial(config.interstitial_ad_unit.trim());
    } else if (window.Android?.showAdMobInterstitial) {
      window.Android.showAdMobInterstitial(config.interstitial_ad_unit.trim());
    }
    return true;
  } catch (err) {
    console.error('Failed to show AdMob Rewarded Interstitial', err);
    return false;
  }
}

/**
 * Record post open click and trigger Rewarded Interstitial on every 15th post
 */
export function recordPostClickAndMaybeShowAd(): boolean {
  // If user is VIP subscribed, do not show ads
  if ((window as any).__isVipSubscribed || (window as any).__hasActiveSubscription) {
    return false;
  }

  const config = cachedAdMobConfig;
  const freq = config.interstitial_frequency || 15;
  const currentClicks = parseInt(localStorage.getItem(ADMOB_CLICK_COUNT_KEY) || '0', 10) + 1;
  localStorage.setItem(ADMOB_CLICK_COUNT_KEY, currentClicks.toString());

  // Proactively preload
  preloadAdMobInterstitial();

  // Trigger ad on 1st click (for immediate verification when tested) and every 15th click
  if (currentClicks === 1 || currentClicks % freq === 0) {
    return showAdMobInterstitial();
  }

  return false;
}

/**
 * Preload AdMob Rewarded Interstitial Ad in background
 */
export function preloadAdMobInterstitial(): void {
  if (!isAndroidNative()) return;
  const config = cachedAdMobConfig;
  if (!config.enabled || !config.interstitial_enabled || !config.interstitial_ad_unit?.trim()) {
    return;
  }
  try {
    if (window.Android?.preloadAdMobRewardedInterstitial) {
      window.Android.preloadAdMobRewardedInterstitial(config.interstitial_ad_unit.trim());
    } else if (window.Android?.preloadAdMobInterstitial) {
      window.Android.preloadAdMobInterstitial(config.interstitial_ad_unit.trim());
    }
  } catch (err) {
    console.error('Failed to preload AdMob Rewarded Interstitial', err);
  }
}

/**
 * Show AdMob Rewarded Video Ad (with callback on reward granted / completed)
 */
export function showAdMobRewarded(onCompleted?: () => void): boolean {
  if (!isAndroidNative()) {
    // If running in browser preview, invoke callback directly
    if (onCompleted) onCompleted();
    return false;
  }

  const config = cachedAdMobConfig;
  if (!config.enabled || !config.rewarded_enabled || !config.rewarded_ad_unit?.trim()) {
    if (onCompleted) onCompleted();
    return false;
  }

  if (onCompleted) {
    const originalCallback = window.onAndroidRewardedVideoCompleted;
    window.onAndroidRewardedVideoCompleted = () => {
      if (originalCallback) originalCallback();
      onCompleted();
    };
  }

  try {
    window.Android?.showAdMobRewarded?.(config.rewarded_ad_unit.trim());
    return true;
  } catch (err) {
    console.error('Failed to show AdMob Rewarded Ad', err);
    if (onCompleted) onCompleted();
    return false;
  }
}

/**
 * Preload AdMob Rewarded Ad in background
 */
export function preloadAdMobRewarded(): void {
  if (!isAndroidNative()) return;
  const config = cachedAdMobConfig;
  if (!config.enabled || !config.rewarded_enabled || !config.rewarded_ad_unit?.trim()) {
    return;
  }
  try {
    window.Android?.preloadAdMobRewarded?.(config.rewarded_ad_unit.trim());
  } catch (err) {
    console.error('Failed to preload AdMob Rewarded Ad', err);
  }
}

let periodicRewardedTimerId: any = null;

/**
 * Start periodic Rewarded Video Ad timer (every 10 to 12 minutes, default 11 minutes)
 */
export function startPeriodicRewardedTimer(onCompleted?: () => void): void {
  if (periodicRewardedTimerId) {
    clearInterval(periodicRewardedTimerId);
    periodicRewardedTimerId = null;
  }

  const config = cachedAdMobConfig;
  // Interval between 10 and 12 minutes (default 11 minutes in ms)
  const intervalMinutes = config.rewarded_interval_minutes || 11;
  const intervalMs = Math.max(10, Math.min(12, intervalMinutes)) * 60 * 1000;

  // Preload rewarded ad 60 seconds before trigger
  const preloadLeadTimeMs = 60 * 1000;
  setTimeout(() => {
    preloadAdMobRewarded();
  }, Math.max(5000, intervalMs - preloadLeadTimeMs));

  periodicRewardedTimerId = setInterval(() => {
    // If user is VIP subscribed, do not show ads
    if ((window as any).__isVipSubscribed || (window as any).__hasActiveSubscription) {
      return;
    }

    if (isAndroidNative()) {
      showAdMobRewarded(onCompleted);
      // Preload next cycle
      setTimeout(() => {
        preloadAdMobRewarded();
      }, preloadLeadTimeMs);
    }
  }, intervalMs);
}

/**
 * Stop periodic timer if needed
 */
export function stopPeriodicRewardedTimer(): void {
  if (periodicRewardedTimerId) {
    clearInterval(periodicRewardedTimerId);
    periodicRewardedTimerId = null;
  }
}
