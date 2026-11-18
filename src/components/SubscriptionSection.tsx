import React, { useState, useEffect } from 'react';
import { ArrowLeft, Check, CheckCircle2, ShieldCheck, Ticket } from 'lucide-react';
import {
  SUBSCRIPTION_PLANS,
  SubscriptionPlan,
  checkActiveSubscription,
  subscribeToDeviceSubscription,
  redeemActivationKey,
  payAndActivate,
  getDeviceId,
  ActiveSubscriptionResult,
  applySubscriptionUserAgent
} from '../services/subscriptionService';

interface SubscriptionSectionProps {
  onBack: () => void;
  onSubscriptionStatusChange?: (isSubscribed: boolean) => void;
}

export const SubscriptionSection: React.FC<SubscriptionSectionProps> = ({
  onBack,
  onSubscriptionStatusChange
}) => {
  const [subStatus, setSubStatus] = useState<ActiveSubscriptionResult>({
    isSubscribed: false,
    expiryDate: null,
    daysRemaining: 0
  });
  const [processingPlanName, setProcessingPlanName] = useState<string | null>(null);
  const [activationCode, setActivationCode] = useState('');
  const [redeemFeedback, setRedeemFeedback] = useState<{ ok: boolean; text: string } | null>(null);
  const [isRedeeming, setIsRedeeming] = useState(false);

  const deviceId = getDeviceId();

  const loadStatus = async () => {
    try {
      const res = await checkActiveSubscription();
      setSubStatus(res);
      if (onSubscriptionStatusChange) {
        onSubscriptionStatusChange(res.isSubscribed);
      }
    } catch (e) {
      console.error('Error checking active subscription:', e);
    }
  };

  useEffect(() => {
    applySubscriptionUserAgent();
    loadStatus();
    const unsub = subscribeToDeviceSubscription((res) => {
      setSubStatus(res);
      onSubscriptionStatusChange?.(res.isSubscribed);
    });
    return () => unsub();
  }, []);

  const handleSelectPlan = (plan: SubscriptionPlan, e: React.MouseEvent<HTMLButtonElement>) => {
    applySubscriptionUserAgent();
    const buttonEl = e.currentTarget;
    setProcessingPlanName(plan.name);

    payAndActivate(plan.price, plan.days, plan.name, buttonEl, () => {
      setProcessingPlanName(null);
      loadStatus();
      onSubscriptionStatusChange?.(true);
    });
  };

  const handleRedeemCode = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activationCode.trim()) {
      setRedeemFeedback({ ok: false, text: 'Please enter an activation code.' });
      return;
    }
    setIsRedeeming(true);
    setRedeemFeedback(null);
    const res = await redeemActivationKey(activationCode);
    setIsRedeeming(false);
    setRedeemFeedback({ ok: res.success, text: res.message });
    if (res.success) {
      setActivationCode('');
      await loadStatus();
      onSubscriptionStatusChange?.(true);
    }
  };

  return (
    <div className="sub-user-theme-wrapper min-h-screen bg-black text-white px-4 py-6 font-['Poppins',sans-serif]">
      <div className="max-w-[1200px] mx-auto flex items-center justify-between mb-6 pb-3 border-b border-[#222] flex-wrap gap-3">
        <button
          onClick={onBack}
          className="flex items-center gap-2 text-sm font-semibold bg-[#1a1a1a] hover:bg-[#2a2a2a] text-white px-4 py-2 rounded-lg transition-colors border border-[#333] cursor-pointer"
        >
          <ArrowLeft size={16} />
          <span>Back to Movies</span>
        </button>

        <div className="text-xs text-[#888] flex items-center gap-2 font-mono">
          <ShieldCheck size={14} className="text-[#04AA6D]" />
          <span>Device ID: {deviceId}</span>
        </div>
      </div>

      {subStatus.isSubscribed && (
        <div className="max-w-[1200px] mx-auto mb-8 bg-gradient-to-r from-[#071f14] via-[#0d1412] to-[#111] border-2 border-[#10b981] rounded-xl p-5 shadow-[0_10px_30px_rgba(16,185,129,0.2)] flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-[#10b981]/20 border border-[#10b981] flex items-center justify-center text-[#34d399] flex-shrink-0">
              <CheckCircle2 size={28} />
            </div>
            <div>
              <div className="text-lg font-bold text-white flex items-center gap-2 flex-wrap">
                <span>{subStatus.planName || 'VIP Membership'} Active</span>
                <span className="text-[10px] bg-[#10b981] text-black font-bold px-2 py-0.5 rounded">
                  {subStatus.quality || '1080p HD'}
                </span>
                <span className="text-[10px] bg-[#e50914] text-white font-bold px-2 py-0.5 rounded">
                  100% AD-FREE
                </span>
              </div>
              <div className="text-xs text-[#cbd5e1] mt-1">
                {subStatus.daysRemaining} Day(s) remaining • All Movies & Web Series Unlocked
                {subStatus.expiryDate && (
                  <span className="ml-1 text-[#fff]">
                    (Expires: {subStatus.expiryDate.toLocaleDateString()})
                  </span>
                )}
              </div>
            </div>
          </div>

          <button
            onClick={onBack}
            className="w-full sm:w-auto bg-[#e50914] hover:bg-[#b80710] text-white font-bold px-6 py-2.5 rounded-lg text-sm transition-all shadow-md cursor-pointer"
          >
            Watch Movies Now
          </button>
        </div>
      )}

      <div className="container max-w-[1200px] mx-auto">
        <h1 className="text-2xl sm:text-4xl font-extrabold text-center mb-2 tracking-tight">
          Choose the plan that&apos;s right for you
        </h1>
        <p className="text-center text-[#bbb] text-sm sm:text-base mb-10 leading-relaxed">
          Unlimited Movies, Web Series &amp; Ad-Free Streaming • Synced with Firebase RTDB<br />
          <span className="text-[#04AA6D] font-medium text-xs sm:text-sm">Safe &amp; Secure UPI / Card Payment</span>
        </p>

        <div className="plans grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {SUBSCRIPTION_PLANS.map((plan) => {
            const isProcessing = processingPlanName === plan.name;
            return (
              <div
                key={plan.name}
                className={`relative bg-gradient-to-b from-[#16161c] to-[#09090c] rounded-xl overflow-hidden shadow-[0_12px_32px_rgba(0,0,0,0.65)] transition-all flex flex-col ${
                  plan.popular
                    ? 'border-2 border-[#e50914] shadow-[0_14px_40px_rgba(229,9,20,0.25)]'
                    : 'border border-[#2a2a35] hover:border-[#e50914]'
                }`}
              >
                <div className="bg-gradient-to-br from-[#e50914] to-[#99040b] py-3.5 px-4 text-center text-white">
                  <div className="font-bold text-base">{plan.name}</div>
                  <div className="text-[11px] opacity-90">{plan.resolution} • {plan.quality}</div>
                </div>
                <div className="p-6 text-center flex-grow flex flex-col justify-between">
                  <div>
                    <div className="text-3xl sm:text-4xl font-extrabold my-2 text-white">
                      ₹{plan.price.toFixed(2)}
                    </div>
                    <div className="text-[#a1a1aa] mb-4 text-xs font-medium">
                      {plan.durationText} • {plan.deviceLimitText}
                    </div>
                    <ul className="space-y-2 mb-6 text-left">
                      {plan.features.map((feat) => (
                        <li key={feat} className="flex items-center gap-2 text-xs text-zinc-300">
                          <Check size={13} className="text-[#e50914] shrink-0" />
                          <span>{feat}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                  <button
                    disabled={isProcessing}
                    onClick={(e) => handleSelectPlan(plan, e)}
                    className="block w-full bg-[#e50914] hover:bg-[#b80710] text-white py-3 px-6 rounded-lg font-bold text-sm transition-all disabled:opacity-60 disabled:cursor-not-allowed cursor-pointer uppercase tracking-wider"
                  >
                    {isProcessing ? 'Processing...' : subStatus.isSubscribed ? 'EXTEND PLAN' : 'SELECT PLAN'}
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        {/* Activation Key Redeem Box */}
        <form
          onSubmit={handleRedeemCode}
          className="max-w-xl mx-auto mt-10 bg-[#111116] border border-[#27272f] rounded-2xl p-5 text-center"
        >
          <div className="flex items-center justify-center gap-2 text-sm font-bold text-white">
            <Ticket size={16} className="text-amber-400" />
            <span>Have an Activation Code / Voucher?</span>
          </div>
          <p className="text-xs text-zinc-400 mt-1">
            Enter your VIP activation key from admin to unlock membership immediately.
          </p>
          <div className="flex flex-col sm:flex-row gap-2.5 mt-3">
            <input
              type="text"
              value={activationCode}
              onChange={(e) => setActivationCode(e.target.value.toUpperCase())}
              placeholder="e.g. NFLX-ABCD-1234"
              className="flex-1 bg-[#050507] border border-zinc-700 focus:border-[#e50914] rounded-lg px-3.5 py-2.5 text-sm font-mono text-white outline-none uppercase"
            />
            <button
              type="submit"
              disabled={isRedeeming}
              className="bg-[#04AA6D] hover:bg-emerald-600 disabled:opacity-60 text-white font-bold text-xs px-5 py-2.5 rounded-lg cursor-pointer"
            >
              {isRedeeming ? 'Verifying...' : 'Activate Key'}
            </button>
          </div>
          {redeemFeedback && (
            <p className={`text-xs mt-2.5 font-medium ${redeemFeedback.ok ? 'text-emerald-400' : 'text-red-400'}`}>
              {redeemFeedback.text}
            </p>
          )}
        </form>
      </div>

      <div id="customAlert" className="custom-alert-overlay">
        <div className="custom-alert-box">
          <div className="icon">✓</div>
          <h3>Subscription Activated!</h3>
          <p>Successfully processed. Redirecting...</p>
          <div className="alert-spinner"></div>
        </div>
      </div>
    </div>
  );
};
