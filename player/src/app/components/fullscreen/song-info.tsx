import { memo } from 'react'
import { Dot } from '@/app/components/dot'
import { MarqueeTitle } from '@/app/components/fullscreen/marquee-title'
import { SongQualityBadge } from '@/app/components/song/quality-badge'
import { Badge } from '@/app/components/ui/badge'
import { usePlayerStore } from '@/store/player.store'
import { ISong } from '@/types/responses/song'
import { ALBUM_ARTISTS_MAX_NUMBER } from '@/utils/multipleArtists'
import { FullscreenSongImage } from './song-image'

const MemoFullscreenSongImage = memo(FullscreenSongImage)

export function SongInfo() {
  const currentSong = usePlayerStore((state) => state.songlist.currentSong)

  return (
    <div className="flex flex-col md:flex-row items-center justify-center md:justify-start h-full min-h-full max-h-full gap-4 2xl:gap-6 flex-1 pt-2 overflow-hidden">
      <MemoFullscreenSongImage />

      <div className="flex flex-col w-full md:w-[66%] max-w-full h-auto md:h-full max-h-[450px] 2xl:max-h-[550px] justify-center md:justify-end text-center md:text-left overflow-hidden">
        <MarqueeTitle gap="mr-6">
          <h2 className="scroll-m-20 text-3xl md:text-4xl 2xl:text-5xl font-bold tracking-tight py-2 2xl:py-3 text-shadow-md text-center md:text-left w-full">
            {currentSong.title}
          </h2>
        </MarqueeTitle>
        <div className="text-sm md:text-base 2xl:text-lg flex justify-center md:justify-start gap-1 text-foreground/70 truncate maskImage-marquee-fade-finished w-full">
          <p className="truncate text-shadow-lg text-foreground">
            {currentSong.album}
          </p>
          <Dot className="text-foreground/70" />
          <ArtistNames song={currentSong} />
        </div>
        <div className="flex justify-center md:justify-start gap-2 mt-2 2xl:mt-3 mb-[1px] w-full">
          {currentSong.genre && (
            <Badge variant="neutral">{currentSong.genre}</Badge>
          )}
          {currentSong.year && (
            <Badge variant="neutral">{currentSong.year}</Badge>
          )}
          <SongQualityBadge song={currentSong} variant="neutral" />
        </div>
      </div>
    </div>
  )
}

function ArtistNames({ song }: { song: ISong }) {
  const { artist, artists } = song

  if (artists && artists.length > 1) {
    const data = artists.slice(0, ALBUM_ARTISTS_MAX_NUMBER)

    return (
      <div className="flex items-center gap-1">
        {data.map(({ id, name }, index) => (
          <div key={id} className="flex">
            <p className="truncate text-shadow-lg">{name}</p>
            {index < data.length - 1 && ','}
          </div>
        ))}
      </div>
    )
  }

  return <p className="truncate text-shadow-lg">{artist}</p>
}
