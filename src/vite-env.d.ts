/// <reference types="vite/client" />

interface Window {
  Android?: {
    initAdMob?: (appId: string) => void;
    showAdMobBanner?: (adUnitId: string) => void;
    hideAdMobBanner?: () => void;
    showAdMobInterstitial?: (adUnitId: string) => void;
    preloadAdMobInterstitial?: (adUnitId: string) => void;
    showAdMobRewardedInterstitial?: (adUnitId: string) => void;
    preloadAdMobRewardedInterstitial?: (adUnitId: string) => void;
    showAdMobRewarded?: (adUnitId: string) => void;
    preloadAdMobRewarded?: (adUnitId: string) => void;
    showRewardedVideo?: () => void;
    showInterstitial?: () => void;
    showBanner?: () => void;
    openUrl?: (url: string) => void;
    openCustomTab?: (url: string) => void;
    openExternalUrl?: (url: string) => void;
    setLandscape?: () => void;
    setPortrait?: () => void;
    unlockOrientation?: () => void;
    keepScreenOn?: (enable: boolean) => void;
    hideSystemBars?: () => void;
    shareApp?: (text: string, url: string) => void;
    openUpiIntent?: (url: string) => boolean;
    isAndroidNative?: () => boolean;
    downloadAndInstallApk?: (url: string, fileName: string) => void;
  };
  onAndroidRewardedVideoCompleted?: () => void;
}
