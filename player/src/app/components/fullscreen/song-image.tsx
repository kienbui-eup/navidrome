import { useState, TouchEvent } from 'react'
import clsx from 'clsx'
import { AnimatedCoverVideo } from '@/app/components/album/animated-cover-video'
import { ImageLoader } from '@/app/components/image-loader'
import { AspectRatio } from '@/app/components/ui/aspect-ratio'
import { usePlayerStore, usePlayerActions, usePlayerPrevAndNext } from '@/store/player.store'

export function FullscreenSongImage() {
  const { coverArt, artist, title, album } = usePlayerStore(({ songlist }) => {
    return songlist.currentSong
  })
  
  const { playNextSong, playPrevSong } = usePlayerActions()
  const { hasPrev, hasNext } = usePlayerPrevAndNext()

  // Touch gesture & Slide States
  const [touchStart, setTouchStart] = useState<number | null>(null)
  const [touchEnd, setTouchEnd] = useState<number | null>(null)
  const [slideState, setSlideState] = useState<'none' | 'left' | 'right'>('none')

  const handleTouchStart = (e: TouchEvent<HTMLDivElement>) => {
    setTouchStart(e.targetTouches[0].clientX)
  }

  const handleTouchMove = (e: TouchEvent<HTMLDivElement>) => {
    setTouchEnd(e.targetTouches[0].clientX)
  }

  const handleTouchEnd = () => {
    if (!touchStart || !touchEnd) return

    const distance = touchStart - touchEnd
    const minSwipeDistance = 75 // pixel threshold for swipe

    const isLeftSwipe = distance > minSwipeDistance
    const isRightSwipe = distance < -minSwipeDistance

    if (isLeftSwipe && hasNext) {
      setSlideState('left')
      setTimeout(() => {
        playNextSong()
        setSlideState('none')
      }, 250)
    } else if (isRightSwipe && hasPrev) {
      setSlideState('right')
      setTimeout(() => {
        playPrevSong()
        setSlideState('none')
      }, 250)
    }

    // Reset touch coordinates
    setTouchStart(null)
    setTouchEnd(null)
  }

  return (
    <div 
      className="w-full md:w-auto md:2xl:w-[33%] h-auto md:h-full max-w-[260px] sm:max-w-[320px] md:max-w-[450px] 2xl:max-w-[550px] max-h-[260px] sm:max-h-[320px] md:max-h-[450px] 2xl:max-h-[550px] items-end flex aspect-square mx-auto md:mx-0 select-none cursor-grab active:cursor-grabbing"
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
    >
      <AspectRatio
        ratio={1 / 1}
        className={clsx(
          "rounded-lg 2xl:rounded-2xl overflow-hidden bg-accent/60 shadow-custom-5 transition-all duration-300 ease-out transform",
          slideState === 'left' && "-translate-x-[110%] opacity-0 -rotate-6 scale-95",
          slideState === 'right' && "translate-x-[110%] opacity-0 rotate-6 scale-95",
          slideState === 'none' && "translate-x-0 opacity-100 rotate-0 scale-100"
        )}
      >
        <div className="relative w-full h-full">
          <ImageLoader id={coverArt} type="song" size={800}>
            {(src, isLoading) => (
              <img
                src={src}
                alt={`${artist} - ${title}`}
                className={clsx(
                  'aspect-square object-cover transition-opacity duration-300 opacity-0',
                  'relative after:absolute after:block after:inset-0 after:bg-accent after:text-transparent',
                  !isLoading && 'opacity-100',
                )}
                width="100%"
                height="100%"
                draggable={false}
              />
            )}
          </ImageLoader>

          <AnimatedCoverVideo
            artist={artist}
            album={album}
            screen="fullscreen"
          />
        </div>
      </AspectRatio>
    </div>
  )
}
