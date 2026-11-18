import React, { useState, useEffect } from 'react';
import { ArrowLeft, ShieldCheck, Lock, Check, DollarSign, RefreshCw, Save, CheckCircle2, Tv, Film, PlayCircle, ToggleLeft, ToggleRight } from 'lucide-react';
import { saveSubscriptionToFirebase } from '../services/subscriptionService';
import { fetchAdMobConfig, saveAdMobConfig, showAdMobBanner, hideAdMobBanner, showAdMobInterstitial, showAdMobRewarded, isAndroidNative } from '../services/admobService';
import { AdMobConfig } from '../types';

interface AdminSectionProps {
  onBack: () => void;
  onSubscriptionStatusChange?: (status: boolean) => void;
}

export const AdminSection: React.FC<AdminSectionProps> = ({
  onBack,
  onSubscriptionStatusChange
}) => {
  const [password, setPassword] = useState('');
  const [isUnlocked, setIsUnlocked] = useState(false);
  const [errorMsg, setErrorMsg] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [showSuccessAlert, setShowSuccessAlert] = useState(false);
  const [dbError, setDbError] = useState<string | null>(null);

  // Active Admin Tab
  const [activeTab, setActiveTab] = useState<'admob' | 'subscription'>('admob');

  // AdMob Config State
  const [admobConfig, setAdmobConfig] = useState<AdMobConfig>({
    enabled: true,
    app_id: '',
    banner_ad_unit: '',
    interstitial_ad_unit: '',
    rewarded_ad_unit: '',
    banner_enabled: true,
    interstitial_enabled: true,
    rewarded_enabled: true,
    interstitial_frequency: 1
  });
  const [isLoadingAdMob, setIsLoadingAdMob] = useState(false);
  const [isSavingAdMob, setIsSavingAdMob] = useState(false);
  const [admobSaveSuccess, setAdmobSaveSuccess] = useState(false);

  useEffect(() => {
    if (isUnlocked) {
      loadAdMob();
    }
  }, [isUnlocked]);

  const loadAdMob = async () => {
    setIsLoadingAdMob(true);
    try {
      const cfg = await fetchAdMobConfig();
      setAdmobConfig(cfg);
    } catch (e) {
      console.error('Failed to load AdMob config in admin:', e);
    } finally {
      setIsLoadingAdMob(false);
    }
  };

  const handleSaveAdMob = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setIsSavingAdMob(true);
    setAdmobSaveSuccess(false);
    try {
      const success = await saveAdMobConfig(admobConfig);
      if (success) {
        setAdmobSaveSuccess(true);
        setTimeout(() => setAdmobSaveSuccess(false), 3500);
      } else {
        alert('Could not save to Firebase Database. Check your connection.');
      }
    } catch (err: any) {
      console.error('Save AdMob failed:', err);
      alert('Error saving AdMob: ' + err.message);
    } finally {
      setIsSavingAdMob(false);
    }
  };

  const handleCheckPassword = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (password.trim() === 's2026kp') {
      setIsUnlocked(true);
      setErrorMsg(false);
      setDbError(null);
    } else {
      setErrorMsg(true);
    }
  };

  const handleActivatePlan = async (days: number) => {
    setIsProcessing(true);
    setDbError(null);
    try {
      await saveSubscriptionToFirebase(days);
      onSubscriptionStatusChange?.(true);
      setShowSuccessAlert(true);
      setTimeout(() => {
        setShowSuccessAlert(false);
        onBack();
      }, 2000);
    } catch (err: any) {
      console.error('Admin activation error:', err);
      setDbError('Database Error! Unable to activate plan at this moment.');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <main className="min-h-screen bg-black text-white pb-24 font-['Poppins',sans-serif] animate-fadeIn select-none">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 pt-5">
        
        {/* Top Header Bar */}
        <div className="bg-gradient-to-r from-[#1c1917] via-[#292524] to-[#451a03] border border-amber-500/40 rounded-2xl p-4 sm:p-5 mb-6 flex flex-wrap items-center justify-between gap-4 shadow-xl">
          <div className="flex items-center gap-3.5">
            <div className="w-12 h-12 rounded-2xl bg-amber-500/15 border-2 border-amber-500 flex items-center justify-center text-amber-400 shadow-[0_0_18px_rgba(245,158,11,0.35)] shrink-0">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl sm:text-2xl font-black text-white tracking-wide">
                  Admin Panel
                </h1>
                <span className="text-[11px] font-bold bg-amber-500 text-black px-2 py-0.5 rounded-full uppercase">
                  Protected
                </span>
              </div>
              <p className="text-xs text-stone-400 mt-0.5">
                Authorized administrator configuration &amp; AdMob manager
              </p>
            </div>
          </div>

          <button
            onClick={onBack}
            className="flex items-center gap-1.5 bg-white/10 hover:bg-white/20 active:scale-95 text-white text-xs font-bold py-2 px-4 rounded-xl border border-white/20 transition-all cursor-pointer shadow"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Home</span>
          </button>
        </div>

        {/* Locked Content Password Box */}
        {!isUnlocked ? (
          <div className="flex items-center justify-center min-h-[60vh]">
            <div className="max-w-[400px] w-full p-7 sm:p-8 rounded-2xl border border-gray-800 bg-[#111111] text-center shadow-2xl">
              <div className="w-14 h-14 rounded-2xl bg-red-600/15 border border-red-500/40 flex items-center justify-center text-red-500 mx-auto mb-4">
                <Lock className="w-7 h-7" />
              </div>
              <h3 className="text-xl font-bold text-white mb-2">🔒 Locked Content</h3>
              <p className="text-xs sm:text-sm text-gray-400 mb-5">
                To view this admin panel, enter the password below:
              </p>

              <form onSubmit={handleCheckPassword}>
                <input
                  type="password"
                  id="post-password"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (errorMsg) setErrorMsg(false);
                  }}
                  placeholder="Enter Password Here"
                  className="w-full p-3 text-base border border-gray-700 bg-black text-white rounded-lg mb-3 outline-none text-center focus:border-red-600 transition-colors"
                />

                <button
                  type="submit"
                  className="w-full p-3 bg-[#e50914] hover:bg-[#b80710] active:scale-95 text-white font-bold text-base rounded-lg transition-all cursor-pointer shadow-lg shadow-red-950/60"
                >
                  Unlock Content
                </button>
              </form>

              {errorMsg && (
                <p id="error-msg" className="text-[#e50914] text-xs font-bold mt-3 animate-shake">
                  ❌ Only Admin Access
                </p>
              )}
            </div>
          </div>
        ) : (
          /* Unlocked Admin Section */
          <div className="space-y-6 animate-fadeIn">
            {/* Tab Navigation */}
            <div className="flex items-center gap-3 border-b border-white/10 pb-4">
              <button
                onClick={() => setActiveTab('admob')}
                className={`flex items-center gap-2 px-5 py-2.5 rounded-xl font-bold text-sm transition-all cursor-pointer ${
                  activeTab === 'admob'
                    ? 'bg-[#e50914] text-white shadow-lg shadow-red-950/50'
                    : 'bg-white/5 text-gray-400 hover:text-white hover:bg-white/10'
                }`}
              >
                <DollarSign className="w-4 h-4" />
                <span>Google AdMob Settings</span>
              </button>
              <button
                onClick={() => setActiveTab('subscription')}
                className={`flex items-center gap-2 px-5 py-2.5 rounded-xl font-bold text-sm transition-all cursor-pointer ${
                  activeTab === 'subscription'
                    ? 'bg-[#e50914] text-white shadow-lg shadow-red-950/50'
                    : 'bg-white/5 text-gray-400 hover:text-white hover:bg-white/10'
                }`}
              >
                <ShieldCheck className="w-4 h-4" />
                <span>VIP Subscription Plans</span>
              </button>
            </div>

            {/* TAB 1: AdMob Settings */}
            {activeTab === 'admob' && (
              <div className="bg-[#111111] border border-gray-800 rounded-2xl p-6 sm:p-8 shadow-2xl">
                <div className="flex flex-wrap items-center justify-between gap-4 mb-6 pb-4 border-b border-gray-800">
                  <div>
                    <h2 className="text-xl font-black text-white flex items-center gap-2">
                      <span className="text-emerald-400">Google AdMob</span> Real Production Ads
                    </h2>
                    <p className="text-xs text-gray-400 mt-1">
                      Ad Unit IDs are saved dynamically in Firebase RTDB (<code className="text-amber-400">/admob</code>) and serve real revenue ads.
                    </p>
                  </div>
                  <button
                    onClick={loadAdMob}
                    disabled={isLoadingAdMob}
                    className="flex items-center gap-1.5 px-3 py-1.5 bg-white/10 hover:bg-white/20 text-xs font-semibold rounded-lg transition-all"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${isLoadingAdMob ? 'animate-spin' : ''}`} />
                    <span>Reload from Firebase</span>
                  </button>
                </div>

                {admobSaveSuccess && (
                  <div className="mb-6 p-4 rounded-xl bg-emerald-950/80 border border-emerald-500/50 flex items-center gap-3 text-emerald-200 text-sm animate-fadeIn">
                    <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />
                    <span>AdMob Ad Units successfully saved to Firebase Database! Live production ads will update immediately.</span>
                  </div>
                )}

                <form onSubmit={handleSaveAdMob} className="space-y-6">
                  {/* Master Switch */}
                  <div className="flex items-center justify-between p-4 rounded-xl bg-black/60 border border-gray-800">
                    <div>
                      <div className="font-bold text-sm text-white">AdMob Master Enable</div>
                      <div className="text-xs text-gray-400">Toggle all AdMob ads ON or OFF</div>
                    </div>
                    <button
                      type="button"
                      onClick={() => setAdmobConfig({ ...admobConfig, enabled: !admobConfig.enabled })}
                      className="cursor-pointer"
                    >
                      {admobConfig.enabled ? (
                        <ToggleRight className="w-9 h-9 text-emerald-500" />
                      ) : (
                        <ToggleLeft className="w-9 h-9 text-gray-600" />
                      )}
                    </button>
                  </div>

                  {/* AdMob App ID */}
                  <div>
                    <label className="block text-xs font-bold uppercase tracking-wider text-gray-300 mb-2">
                      Google AdMob App ID
                    </label>
                    <input
                      type="text"
                      value={admobConfig.app_id || ''}
                      onChange={(e) => setAdmobConfig({ ...admobConfig, app_id: e.target.value.trim() })}
                      placeholder="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY"
                      className="w-full p-3.5 bg-black border border-gray-800 rounded-xl text-white text-sm font-mono focus:border-red-600 outline-none transition-colors"
                    />
                    <p className="text-[11px] text-gray-500 mt-1">Example: ca-app-pub-3440654346799700~9967202851</p>
                  </div>

                  {/* Banner Ad Unit */}
                  <div className="p-4 rounded-xl bg-black/40 border border-gray-800/80 space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Tv className="w-4 h-4 text-amber-400" />
                        <span className="font-bold text-sm text-white">Banner Ad Unit ID</span>
                      </div>
                      <button
                        type="button"
                        onClick={() => setAdmobConfig({ ...admobConfig, banner_enabled: !admobConfig.banner_enabled })}
                        className="cursor-pointer"
                      >
                        {admobConfig.banner_enabled ? (
                          <ToggleRight className="w-7 h-7 text-emerald-500" />
                        ) : (
                          <ToggleLeft className="w-7 h-7 text-gray-600" />
                        )}
                      </button>
                    </div>
                    <input
                      type="text"
                      value={admobConfig.banner_ad_unit || ''}
                      onChange={(e) => setAdmobConfig({ ...admobConfig, banner_ad_unit: e.target.value.trim() })}
                      placeholder="ca-app-pub-3440654346799700/9005154179"
                      className="w-full p-3 bg-black border border-gray-800 rounded-lg text-white text-sm font-mono focus:border-red-600 outline-none transition-colors"
                    />
                  </div>

                  {/* Interstitial Ad Unit */}
                  <div className="p-4 rounded-xl bg-black/40 border border-gray-800/80 space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Film className="w-4 h-4 text-sky-400" />
                        <span className="font-bold text-sm text-white">Rewarded Interstitial Ad Unit ID</span>
                      </div>
                      <button
                        type="button"
                        onClick={() => setAdmobConfig({ ...admobConfig, interstitial_enabled: !admobConfig.interstitial_enabled })}
                        className="cursor-pointer"
                      >
                        {admobConfig.interstitial_enabled ? (
                          <ToggleRight className="w-7 h-7 text-emerald-500" />
                        ) : (
                          <ToggleLeft className="w-7 h-7 text-gray-600" />
                        )}
                      </button>
                    </div>
                    <input
                      type="text"
                      value={admobConfig.interstitial_ad_unit || ''}
                      onChange={(e) => setAdmobConfig({ ...admobConfig, interstitial_ad_unit: e.target.value.trim() })}
                      placeholder="ca-app-pub-3440654346799700/3601105057"
                      className="w-full p-3 bg-black border border-gray-800 rounded-lg text-white text-sm font-mono focus:border-red-600 outline-none transition-colors"
                    />
                    <div className="flex items-center gap-3 pt-1">
                      <span className="text-xs text-gray-400">Show frequency:</span>
                      <select
                        value={admobConfig.interstitial_frequency || 15}
                        onChange={(e) => setAdmobConfig({ ...admobConfig, interstitial_frequency: parseInt(e.target.value, 10) || 15 })}
                        className="bg-black border border-gray-700 text-xs text-white rounded px-2.5 py-1 outline-none"
                      >
                        <option value={15}>Every 15 posts (User Setting)</option>
                        <option value={1}>Every 1 post click</option>
                        <option value={3}>Every 3 post clicks</option>
                        <option value={5}>Every 5 post clicks</option>
                        <option value={10}>Every 10 post clicks</option>
                      </select>
                    </div>
                  </div>

                  {/* Rewarded Video Ad Unit */}
                  <div className="p-4 rounded-xl bg-black/40 border border-gray-800/80 space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <PlayCircle className="w-4 h-4 text-emerald-400" />
                        <span className="font-bold text-sm text-white">Rewarded Video Ad Unit ID</span>
                      </div>
                      <button
                        type="button"
                        onClick={() => setAdmobConfig({ ...admobConfig, rewarded_enabled: !admobConfig.rewarded_enabled })}
                        className="cursor-pointer"
                      >
                        {admobConfig.rewarded_enabled ? (
                          <ToggleRight className="w-7 h-7 text-emerald-500" />
                        ) : (
                          <ToggleLeft className="w-7 h-7 text-gray-600" />
                        )}
                      </button>
                    </div>
                    <input
                      type="text"
                      value={admobConfig.rewarded_ad_unit || ''}
                      onChange={(e) => setAdmobConfig({ ...admobConfig, rewarded_ad_unit: e.target.value.trim() })}
                      placeholder="ca-app-pub-3440654346799700/6608400593"
                      className="w-full p-3 bg-black border border-gray-800 rounded-lg text-white text-sm font-mono focus:border-red-600 outline-none transition-colors"
                    />
                    <div className="flex items-center gap-3 pt-1">
                      <span className="text-xs text-gray-400">Show Timer interval:</span>
                      <select
                        value={admobConfig.rewarded_interval_minutes || 11}
                        onChange={(e) => setAdmobConfig({ ...admobConfig, rewarded_interval_minutes: parseInt(e.target.value, 10) || 11 })}
                        className="bg-black border border-gray-700 text-xs text-white rounded px-2.5 py-1 outline-none"
                      >
                        <option value={11}>Every 11 minutes (10-12m range)</option>
                        <option value={10}>Every 10 minutes</option>
                        <option value={12}>Every 12 minutes</option>
                      </select>
                    </div>
                  </div>

                  {/* Save Button */}
                  <div className="flex flex-wrap items-center gap-3 pt-2">
                    <button
                      type="submit"
                      disabled={isSavingAdMob}
                      className="flex items-center gap-2 px-6 py-3 bg-[#e50914] hover:bg-[#b80710] disabled:opacity-50 text-white font-bold text-sm rounded-xl transition-all cursor-pointer shadow-lg shadow-red-950/60 active:scale-95"
                    >
                      <Save className="w-4 h-4" />
                      <span>{isSavingAdMob ? 'Saving to Firebase RTDB...' : 'Save AdMob Units to Firebase'}</span>
                    </button>

                    {/* Test Native Buttons (only active on Android) */}
                    {isAndroidNative() && (
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => showAdMobBanner()}
                          className="px-3 py-2 bg-white/10 hover:bg-white/20 text-xs font-semibold rounded-lg"
                        >
                          Show Banner
                        </button>
                        <button
                          type="button"
                          onClick={() => hideAdMobBanner()}
                          className="px-3 py-2 bg-white/10 hover:bg-white/20 text-xs font-semibold rounded-lg"
                        >
                          Hide Banner
                        </button>
                        <button
                          type="button"
                          onClick={() => showAdMobInterstitial()}
                          className="px-3 py-2 bg-sky-900/60 hover:bg-sky-900 text-sky-200 text-xs font-semibold rounded-lg"
                        >
                          Show Interstitial
                        </button>
                        <button
                          type="button"
                          onClick={() => showAdMobRewarded(() => alert('Reward completed!'))}
                          className="px-3 py-2 bg-emerald-900/60 hover:bg-emerald-900 text-emerald-200 text-xs font-semibold rounded-lg"
                        >
                          Show Rewarded
                        </button>
                      </div>
                    )}
                  </div>
                </form>
              </div>
            )}

            {/* TAB 2: VIP Subscription Plans */}
            {activeTab === 'subscription' && (
              <div className="py-2 animate-fadeIn">
                <h2 className="text-xl sm:text-2xl font-bold text-center text-white mb-6">
                  VIP Ad-Free Subscription Plan Management
                </h2>

                {dbError && (
                  <div className="max-w-md mx-auto mb-6 p-3.5 bg-red-950/80 border border-red-500 rounded-xl text-center text-red-200 text-sm font-semibold">
                    {dbError}
                  </div>
                )}

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 max-w-6xl mx-auto">
                  {/* Basic Plan */}
                  <div className="bg-gradient-to-b from-[#111111] to-black rounded-xl overflow-hidden border border-gray-800 hover:border-[#e50914] hover:-translate-y-1.5 transition-all shadow-xl flex flex-col">
                    <div className="bg-[#e50914] text-white p-3.5 text-center font-bold text-base">
                      Basic Plan
                    </div>
                    <div className="p-6 text-center flex-1 flex flex-col justify-between">
                      <div>
                        <div className="text-3xl font-black text-white my-2">₹49.00</div>
                        <div className="text-white/80 text-sm mb-2">1 Month(s)</div>
                        <div className="text-white/80 text-xs mb-5">Device Limit - 1</div>
                      </div>
                      <button
                        disabled={isProcessing}
                        onClick={() => handleActivatePlan(30)}
                        className="w-full py-3 px-5 rounded-md bg-[#e50914] hover:bg-[#b80710] disabled:opacity-50 text-white font-bold text-sm transition-all cursor-pointer shadow-md"
                      >
                        SELECT PLAN
                      </button>
                    </div>
                  </div>

                  {/* Standard Plan */}
                  <div className="bg-gradient-to-b from-[#111111] to-black rounded-xl overflow-hidden border border-gray-800 hover:border-[#e50914] hover:-translate-y-1.5 transition-all shadow-xl flex flex-col">
                    <div className="bg-[#e50914] text-white p-3.5 text-center font-bold text-base">
                      Standard Plan
                    </div>
                    <div className="p-6 text-center flex-1 flex flex-col justify-between">
                      <div>
                        <div className="text-3xl font-black text-white my-2">₹129.00</div>
                        <div className="text-white/80 text-sm mb-2">3 Month(s)</div>
                        <div className="text-white/80 text-xs mb-5">Device Limit - 1</div>
                      </div>
                      <button
                        disabled={isProcessing}
                        onClick={() => handleActivatePlan(90)}
                        className="w-full py-3 px-5 rounded-md bg-[#e50914] hover:bg-[#b80710] disabled:opacity-50 text-white font-bold text-sm transition-all cursor-pointer shadow-md"
                      >
                        SELECT PLAN
                      </button>
                    </div>
                  </div>

                  {/* Platinum Plan */}
                  <div className="bg-gradient-to-b from-[#111111] to-black rounded-xl overflow-hidden border border-gray-800 hover:border-[#e50914] hover:-translate-y-1.5 transition-all shadow-xl flex flex-col">
                    <div className="bg-[#e50914] text-white p-3.5 text-center font-bold text-base">
                      Platinum Plan
                    </div>
                    <div className="p-6 text-center flex-1 flex flex-col justify-between">
                      <div>
                        <div className="text-3xl font-black text-white my-2">₹299.00</div>
                        <div className="text-white/80 text-sm mb-2">6 Month(s)</div>
                        <div className="text-white/80 text-xs mb-5">Device Limit - 1</div>
                      </div>
                      <button
                        disabled={isProcessing}
                        onClick={() => handleActivatePlan(180)}
                        className="w-full py-3 px-5 rounded-md bg-[#e50914] hover:bg-[#b80710] disabled:opacity-50 text-white font-bold text-sm transition-all cursor-pointer shadow-md"
                      >
                        SELECT PLAN
                      </button>
                    </div>
                  </div>

                  {/* Diamond Plan */}
                  <div className="bg-gradient-to-b from-[#111111] to-black rounded-xl overflow-hidden border border-gray-800 hover:border-[#e50914] hover:-translate-y-1.5 transition-all shadow-xl flex flex-col">
                    <div className="bg-[#e50914] text-white p-3.5 text-center font-bold text-base">
                      Diamond Plan
                    </div>
                    <div className="p-6 text-center flex-1 flex flex-col justify-between">
                      <div>
                        <div className="text-3xl font-black text-white my-2">₹579.00</div>
                        <div className="text-white/80 text-sm mb-2">1 Year(s)</div>
                        <div className="text-white/80 text-xs mb-5">Device Limit - 1</div>
                      </div>
                      <button
                        disabled={isProcessing}
                        onClick={() => handleActivatePlan(365)}
                        className="w-full py-3 px-5 rounded-md bg-[#e50914] hover:bg-[#b80710] disabled:opacity-50 text-white font-bold text-sm transition-all cursor-pointer shadow-md"
                      >
                        SELECT PLAN
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

      </div>

      {/* Success Notification Alert */}
      {showSuccessAlert && (
        <div className="fixed inset-0 z-[9999999] bg-black/85 backdrop-blur-sm flex items-center justify-center p-4 animate-fadeIn">
          <div className="bg-[#111111] border-2 border-[#e50914] p-8 rounded-2xl text-center max-w-[400px] w-full shadow-[0_10px_40px_rgba(229,9,20,0.3)] animate-scaleUp">
            <div className="w-16 h-16 rounded-full bg-emerald-500/20 text-[#04AA6D] flex items-center justify-center text-3xl mx-auto mb-4 border border-emerald-500/40">
              <Check className="w-9 h-9 stroke-[3]" />
            </div>
            <h3 className="text-xl font-bold text-white mb-2">Subscription Activated!</h3>
            <p className="text-sm text-gray-300">Successfully processed. Redirecting to Home page...</p>
            <div className="w-6 h-6 border-3 border-white/30 border-t-[#e50914] rounded-full animate-spin mx-auto mt-4" />
          </div>
        </div>
      )}
    </main>
  );
};
