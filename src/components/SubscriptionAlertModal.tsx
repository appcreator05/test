import React from 'react';

interface SubscriptionAlertModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubscribeNow: () => void;
  title?: string;
  message?: string;
}

export const SubscriptionAlertModal: React.FC<SubscriptionAlertModalProps> = ({
  isOpen,
  onClose,
  onSubscribeNow,
  title = 'Membership Required',
  message = 'Subscribe to unlock unlimited Movies, Web Series & 100% Ad-Free HD/4K streaming!'
}) => {
  if (!isOpen) return null;

  return (
    <div
      id="subscriptionAlertModalOverlay"
      onClick={onClose}
      className="fixed inset-0 z-[9999999] bg-black/85 backdrop-blur-md flex items-center justify-center p-4 animate-fadeIn select-none"
    >
      <div
        id="subscriptionAlertModalCard"
        onClick={(e) => e.stopPropagation()}
        className="relative bg-[#11141b] border border-[#262c3a] rounded-2xl p-6 sm:p-7 max-w-[340px] w-full flex flex-col items-center text-center shadow-[0_25px_60px_rgba(0,0,0,0.95)] animate-fadeIn"
      >
        <div className="w-16 h-16 rounded-2xl bg-gradient-to-b from-[#e50914] to-[#a5060f] flex items-center justify-center shadow-lg shadow-red-950/70 mb-4">
          <span className="text-white text-2xl font-black font-sans leading-none select-none">
            VIP
          </span>
        </div>

        <h3 className="text-lg sm:text-xl font-bold text-white mb-2 tracking-wide font-sans">
          {title}
        </h3>

        <p className="text-xs sm:text-sm text-[#8fa0b5] font-normal leading-relaxed mb-6 px-1 max-w-[260px]">
          {message}
        </p>

        <div className="w-full flex items-center justify-center gap-3">
          <button
            type="button"
            id="btn-sub-alert-cancel"
            onClick={onClose}
            className="flex-1 py-2.5 sm:py-3 px-3 rounded-xl bg-[#1f2430] hover:bg-[#283040] active:scale-95 text-white font-bold text-xs sm:text-sm transition-all border border-[#303848] cursor-pointer"
          >
            Cancel
          </button>

          <button
            type="button"
            id="btn-sub-alert-subscribe"
            onClick={() => {
              onClose();
              onSubscribeNow();
            }}
            className="flex-1 py-2.5 sm:py-3 px-3 rounded-xl bg-[#e50914] hover:bg-[#b80710] active:scale-95 text-white font-bold text-xs sm:text-sm transition-all shadow-lg shadow-red-950/70 cursor-pointer whitespace-nowrap"
          >
            View Plans
          </button>
        </div>
      </div>
    </div>
  );
};
