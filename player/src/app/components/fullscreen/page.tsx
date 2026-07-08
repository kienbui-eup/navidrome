import { memo } from 'react'
import { ChevronDown } from 'lucide-react'
import { Drawer, DrawerContent, DrawerTitle } from '@/app/components/ui/drawer'
import { Button } from '@/app/components/ui/button'
import { useAppWindow } from '@/app/hooks/use-app-window'
import { useIsMobile } from '@/app/hooks/use-mobile'
import { usePlayerFullscreen } from '@/store/player.store'
import { FullscreenBackdrop } from './backdrop'
import { FullscreenDragHandler } from './drag-handler'
import { FullscreenPlayer } from './player'
import { FullscreenTabs } from './tabs'

const MemoFullscreenBackdrop = memo(FullscreenBackdrop)

export function FullscreenMode() {
  const { handleDrawerAnimationEnd } = useAppWindow()
  const { isFullscreen, setIsFullscreen } = usePlayerFullscreen()
  const isMobile = useIsMobile()

  return (
    <Drawer
      open={isFullscreen}
      onOpenChange={setIsFullscreen}
      fixed={true}
      handleOnly={true}
      disablePreventScroll={true}
      dismissible={true}
      modal={false}
    >
      <DrawerTitle className="sr-only">Big Player</DrawerTitle>
      <DrawerContent
        onAnimationEnd={handleDrawerAnimationEnd}
        className="h-screen w-screen rounded-t-none border-none select-none cursor-default mt-0"
        showHandle={false}
        aria-describedby={undefined}
      >
        <MemoFullscreenBackdrop />
        <FullscreenDragHandler />

        {isMobile ? (
          <div className="absolute inset-0 flex flex-col p-4 pt-10 pb-6 w-full h-full gap-3 bg-black/0 z-10 overflow-hidden">
            {/* Quick Close Button for mobile */}
            <Button
              variant="ghost"
              size="icon"
              className="absolute top-4 left-4 z-20 h-10 w-10 text-foreground/80 hover:text-foreground hover:bg-background/20 rounded-full"
              onClick={() => setIsFullscreen(false)}
            >
              <ChevronDown className="h-6 w-6" />
            </Button>

            {/* First Row */}
            <div className="flex-1 w-full overflow-hidden px-2 pt-4">
              <div className="h-full max-h-full">
                <FullscreenTabs />
              </div>
            </div>

            {/* Second Row */}
            <div className="h-auto min-h-[140px] px-2 py-2">
              <div className="flex items-center justify-center">
                <FullscreenPlayer />
              </div>
            </div>
          </div>
        ) : (
          <div className="absolute inset-0 flex flex-col p-0 2xl:p-8 pt-10 2xl:pt-12 w-full h-full gap-4 bg-black/0 z-10">
            {/* First Row */}
            <div className="w-full max-h-[calc(100%-180px)] min-h-[calc(100%-180px)] px-8 2xl:px-16 pt-4 2xl:pt-8">
              <div className="min-h-[300px] h-full max-h-full">
                <FullscreenTabs />
              </div>
            </div>

            {/* Second Row */}
            <div className="h-[150px] min-h-[150px] px-8 2xl:px-16 py-2">
              <div className="flex items-center">
                <FullscreenPlayer />
              </div>
            </div>
          </div>
        )}
      </DrawerContent>
    </Drawer>
  )
}
