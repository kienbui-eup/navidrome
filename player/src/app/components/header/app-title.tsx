import { AppIcon } from '@/app/components/app-icon'

export function AppTitle() {
  return (
    <div className="flex gap-3 items-center">
      <AppIcon size={36} />
      <div className="flex flex-col justify-center leading-none">
        <div className="text-base font-extrabold tracking-wider" style={{ fontFamily: "'Outfit', 'Inter', sans-serif" }}>
          <span className="bg-gradient-to-r from-[#F1E5AC] via-[#C5A880] to-[#8A6F48] bg-clip-text text-transparent">Vi2</span>
          <span className="text-white">Play</span>
        </div>
        <span className="text-[7px] tracking-[0.22em] font-bold text-[#C5A880] mt-1.5 uppercase opacity-85">
          VIP PLAY • VIỆT PLAYER
        </span>
      </div>
    </div>
  )
}
