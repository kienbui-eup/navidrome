import { useIsMobile } from '@/app/hooks/use-mobile'
import { CloseFullscreenButton } from './buttons'
import { FullscreenControls } from './controls'
import { LikeButton } from './like-button'
import { FullscreenProgress } from './progress'
import { FullscreenSettings } from './settings'
import { VolumeContainer } from './volume-container'

export function FullscreenPlayer() {
  const isMobile = useIsMobile()

  if (isMobile) {
    return (
      <div className="w-full flex flex-col gap-4">
        {/* Progress Bar (full width) */}
        <FullscreenProgress />

        {/* Buttons Row 1: Sub-controls (Close, Settings, Like) */}
        <div className="flex items-center justify-between px-2">
          <div className="flex items-center gap-4">
            <CloseFullscreenButton />
            <FullscreenSettings />
          </div>
          <LikeButton />
        </div>

        {/* Buttons Row 2: Main Playback Controls (extended wide) */}
        <div className="flex justify-center items-center w-full py-2">
          <div className="flex items-center justify-between w-full max-w-[340px] px-4">
            <FullscreenControls />
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="w-full">
      <FullscreenProgress />

      <div className="flex items-center justify-between gap-4 mt-5">
        <div className="w-[200px] flex items-center gap-2 justify-start">
          <CloseFullscreenButton />
          <FullscreenSettings />
        </div>

        <div className="flex flex-1 justify-center items-center gap-2">
          <FullscreenControls />
        </div>

        <div className="w-[200px] flex items-center gap-4 justify-end">
          <LikeButton />
          <VolumeContainer />
        </div>
      </div>
    </div>
  )
}
