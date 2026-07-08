import { memo, useCallback, useEffect, useMemo, useRef } from 'react'
import { Play, Pause } from 'lucide-react'
import { getSongStreamUrl } from '@/api/httpClient'
import { getProxyURL } from '@/api/podcastClient'
import { MiniPlayerButton } from '@/app/components/mini-player/button'
import { RadioInfo } from '@/app/components/player/radio-info'
import { TrackInfo } from '@/app/components/player/track-info'
import { Button } from '@/app/components/ui/button'
import { useIsMobile } from '@/app/hooks/use-mobile'
import { podcasts } from '@/service/podcasts'
import {
  getVolume,
  usePlayerActions,
  usePlayerIsPlaying,
  usePlayerLoop,
  usePlayerMediaType,
  usePlayerRef,
  usePlayerSonglist,
  usePlayerStore,
  useReplayGainState,
  usePlayerProgress,
  usePlayerDuration,
  usePlayerFullscreen,
} from '@/store/player.store'
import { LoopState } from '@/types/playerContext'
import { cn } from '@/lib/utils'
import { hasPiPSupport } from '@/utils/browser'
import { logger } from '@/utils/logger'
import { ReplayGainParams } from '@/utils/replayGain'
import { AudioPlayer } from './audio'
import { PlayerClearQueueButton } from './clear-queue-button'
import { PlayerControls } from './controls'
import { PlayerExpandButton } from './expand-button'
import { PlayerLikeButton } from './like-button'
import { PlayerLyricsButton } from './lyrics-button'
import { PodcastInfo } from './podcast-info'
import { PodcastPlaybackRate } from './podcast-playback-rate'
import { PlayerProgress } from './progress'
import { PlayerQueueButton } from './queue-button'
import { PlayerVolume } from './volume'

const MemoTrackInfo = memo(TrackInfo)
const MemoRadioInfo = memo(RadioInfo)
const MemoPodcastInfo = memo(PodcastInfo)
const MemoPlayerControls = memo(PlayerControls)
const MemoPlayerProgress = memo(PlayerProgress)
const MemoPlayerLikeButton = memo(PlayerLikeButton)
const MemoPlayerQueueButton = memo(PlayerQueueButton)
const MemoPlayerClearQueueButton = memo(PlayerClearQueueButton)
const MemoPlayerVolume = memo(PlayerVolume)
const MemoPlayerExpandButton = memo(PlayerExpandButton)
const MemoPodcastPlaybackRate = memo(PodcastPlaybackRate)
const MemoLyricsButton = memo(PlayerLyricsButton)
const MemoMiniPlayerButton = memo(MiniPlayerButton)

export function Player() {
  const isMobile = useIsMobile()
  const progress = usePlayerProgress()
  const duration = usePlayerDuration()
  const { setIsFullscreen } = usePlayerFullscreen()
  const audioRef = useRef<HTMLAudioElement>(null)
  const radioRef = useRef<HTMLAudioElement>(null)
  const podcastRef = useRef<HTMLAudioElement>(null)
  const {
    setAudioPlayerRef,
    setCurrentDuration,
    setProgress,
    setPlayingState,
    handleSongEnded,
    getCurrentProgress,
    getCurrentPodcastProgress,
    togglePlayPause,
  } = usePlayerActions()
  const { currentList, currentSongIndex, radioList, podcastList } =
    usePlayerSonglist()
  const isPlaying = usePlayerIsPlaying()
  const { isSong, isRadio, isPodcast } = usePlayerMediaType()
  const loopState = usePlayerLoop()
  const audioPlayerRef = usePlayerRef()
  const currentPlaybackRate = usePlayerStore().playerState.currentPlaybackRate
  const { replayGainType, replayGainPreAmp, replayGainDefaultGain } =
    useReplayGainState()

  const song = currentList[currentSongIndex]
  const radio = radioList[currentSongIndex]
  const podcast = podcastList[currentSongIndex]

  const getAudioRef = useCallback(() => {
    if (isRadio) return radioRef
    if (isPodcast) return podcastRef

    return audioRef
  }, [isPodcast, isRadio])

  // biome-ignore lint/correctness/useExhaustiveDependencies: audioRef needed
  useEffect(() => {
    if (!isSong && !song) return

    if (audioPlayerRef === null && audioRef.current)
      setAudioPlayerRef(audioRef.current)
  }, [audioPlayerRef, audioRef, isSong, setAudioPlayerRef, song])

  useEffect(() => {
    const audio = podcastRef.current
    if (!audio || !isPodcast) return

    audio.playbackRate = currentPlaybackRate
  }, [currentPlaybackRate, isPodcast])

  const setupDuration = useCallback(() => {
    const audio = getAudioRef().current
    if (!audio) return

    const audioDuration = Math.floor(audio.duration)
    const infinityDuration = audioDuration === Infinity

    if (!infinityDuration) {
      setCurrentDuration(audioDuration)
    }

    if (isPodcast && infinityDuration && podcast) {
      setCurrentDuration(podcast.duration)
    }

    if (isPodcast) {
      const podcastProgress = getCurrentPodcastProgress()

      logger.info('[Player] - Resuming episode from:', {
        seconds: podcastProgress,
      })

      setProgress(podcastProgress)
      audio.currentTime = podcastProgress
    } else {
      const progress = getCurrentProgress()
      audio.currentTime = progress
    }
  }, [
    getAudioRef,
    isPodcast,
    podcast,
    setCurrentDuration,
    getCurrentPodcastProgress,
    setProgress,
    getCurrentProgress,
  ])

  const setupProgress = useCallback(() => {
    const audio = getAudioRef().current
    if (!audio) return

    const currentProgress = Math.floor(audio.currentTime)
    setProgress(currentProgress)
  }, [getAudioRef, setProgress])

  const setupInitialVolume = useCallback(() => {
    const audio = getAudioRef().current
    if (!audio) return

    audio.volume = getVolume() / 100
  }, [getAudioRef])

  const sendFinishProgress = useCallback(() => {
    if (!isPodcast || !podcast) return

    podcasts
      .saveEpisodeProgress(podcast.id, podcast.duration)
      .then(() => {
        logger.info('Complete progress sent:', podcast.duration)
      })
      .catch((error) => {
        logger.error('Error sending complete progress', error)
      })
  }, [isPodcast, podcast])

  const trackReplayGain = useMemo<ReplayGainParams>(() => {
    const preAmp = replayGainPreAmp
    const defaultGain = replayGainDefaultGain

    if (!song || !song.replayGain) {
      return { gain: defaultGain, peak: 1, preAmp }
    }

    if (replayGainType === 'album') {
      let { albumGain = defaultGain, albumPeak = 1 } = song.replayGain

      if (albumGain === 0) {
        albumGain = defaultGain
      }

      return { gain: albumGain, peak: albumPeak, preAmp }
    }

    let { trackGain = defaultGain, trackPeak = 1 } = song.replayGain

    if (trackGain === 0) {
      trackGain = defaultGain
    }
    return { gain: trackGain, peak: trackPeak, preAmp }
  }, [song, replayGainDefaultGain, replayGainPreAmp, replayGainType])

  if (isMobile) {
    const percentage = duration > 0 ? (progress / duration) * 100 : 0

    return (
      <footer 
        className="border-t h-[72px] w-full flex items-center fixed bottom-0 left-0 right-0 z-40 bg-background/85 backdrop-blur-md cursor-pointer select-none"
        onClick={() => setIsFullscreen(true)}
      >
        <div className="w-full h-full flex items-center justify-between px-4 relative">
          {/* Thin Progress bar */}
          <div className="absolute top-0 left-0 right-0 h-[2.5px] bg-secondary/30">
            <div 
              className="h-full bg-primary transition-all duration-300" 
              style={{ width: `${percentage}%` }} 
            />
          </div>

          {/* Left: Track/Media Info scaled down on mobile */}
          <div className="flex items-center gap-2 min-w-0 max-w-[70%] flex-1 [&_img]:w-12 [&_img]:h-12 [&_.min-w-\[70px\]]:min-w-12 [&_.max-w-\[70px\]]:max-w-12 [&_.min-w-\[70px\]]:h-12 [&_.w-\[70px\]]:w-12 [&_.h-\[70px\]]:h-12">
            {isSong && <MemoTrackInfo song={song} />}
            {isRadio && <MemoRadioInfo radio={radio} />}
            {isPodcast && <MemoPodcastInfo podcast={podcast} />}
          </div>

          {/* Right: Quick Controls */}
          <div onClick={(e) => e.stopPropagation()} className="flex items-center gap-1">
            {isSong && <MemoPlayerLikeButton disabled={!song} />}
            <Button
              variant="ghost"
              size="icon"
              className="h-10 w-10 [&_svg]:h-6 [&_svg]:w-6"
              onClick={togglePlayPause}
            >
              {isPlaying ? (
                <Pause className="fill-foreground text-foreground" />
              ) : (
                <Play className="fill-foreground text-foreground" />
              )}
            </Button>
          </div>
        </div>

        {/* Hidden Audio Player components to drive background play */}
        {isSong && song && (
          <AudioPlayer
            replayGain={trackReplayGain}
            src={getSongStreamUrl(song.id)}
            autoPlay={isPlaying}
            audioRef={audioRef}
            loop={loopState === LoopState.One}
            onPlay={() => setPlayingState(true)}
            onPause={() => setPlayingState(false)}
            onLoadedMetadata={setupDuration}
            onTimeUpdate={setupProgress}
            onEnded={handleSongEnded}
            onLoadStart={setupInitialVolume}
            data-testid="player-song-audio"
          />
        )}

        {isRadio && radio && (
          <AudioPlayer
            src={radio.streamUrl}
            autoPlay={isPlaying}
            audioRef={radioRef}
            onPlay={() => setPlayingState(true)}
            onPause={() => setPlayingState(false)}
            onLoadStart={setupInitialVolume}
            data-testid="player-radio-audio"
          />
        )}

        {isPodcast && podcast && (
          <AudioPlayer
            src={getProxyURL(podcast.audio_url)}
            autoPlay={isPlaying}
            audioRef={podcastRef}
            preload="auto"
            onPlay={() => setPlayingState(true)}
            onPause={() => setPlayingState(false)}
            onLoadedMetadata={setupDuration}
            onTimeUpdate={setupProgress}
            onEnded={() => {
              sendFinishProgress()
              handleSongEnded()
            }}
            onLoadStart={setupInitialVolume}
            data-testid="player-podcast-audio"
          />
        )}
      </footer>
    )
  }

  return (
    <footer className="border-t h-[--player-height] w-full flex items-center fixed bottom-0 left-0 right-0 z-40 bg-background">
      <div className="w-full h-full grid grid-cols-player gap-2 px-4">
        {/* Track Info */}
        <div className="flex items-center gap-2 w-full">
          {isSong && <MemoTrackInfo song={song} />}
          {isRadio && <MemoRadioInfo radio={radio} />}
          {isPodcast && <MemoPodcastInfo podcast={podcast} />}
        </div>
        {/* Main Controls */}
        <div className="col-span-2 flex flex-col justify-center items-center px-4 gap-1">
          <MemoPlayerControls
            song={song}
            radio={radio}
            podcast={podcast}
            audioRef={getAudioRef()}
          />

          {(isSong || isPodcast) && (
            <MemoPlayerProgress audioRef={getAudioRef()} />
          )}
        </div>
        {/* Remain Controls and Volume */}
        <div className="flex items-center w-full justify-end">
          <div className="flex items-center gap-1">
            {isSong && (
              <>
                <MemoPlayerLikeButton disabled={!song} />
                <MemoLyricsButton disabled={!song} />
                <MemoPlayerQueueButton disabled={!song} />
              </>
            )}
            {isPodcast && <MemoPodcastPlaybackRate />}
            {(isRadio || isPodcast) && (
              <MemoPlayerClearQueueButton disabled={!radio && !podcast} />
            )}

            <MemoPlayerVolume
              audioRef={getAudioRef()}
              disabled={!song && !radio && !podcast}
            />

            {isSong && <MemoPlayerExpandButton disabled={!song} />}
            {isSong && hasPiPSupport && <MemoMiniPlayerButton />}
          </div>
        </div>
      </div>

      {isSong && song && (
        <AudioPlayer
          replayGain={trackReplayGain}
          src={getSongStreamUrl(song.id)}
          autoPlay={isPlaying}
          audioRef={audioRef}
          loop={loopState === LoopState.One}
          onPlay={() => setPlayingState(true)}
          onPause={() => setPlayingState(false)}
          onLoadedMetadata={setupDuration}
          onTimeUpdate={setupProgress}
          onEnded={handleSongEnded}
          onLoadStart={setupInitialVolume}
          data-testid="player-song-audio"
        />
      )}

      {isRadio && radio && (
        <AudioPlayer
          src={radio.streamUrl}
          autoPlay={isPlaying}
          audioRef={radioRef}
          onPlay={() => setPlayingState(true)}
          onPause={() => setPlayingState(false)}
          onLoadStart={setupInitialVolume}
          data-testid="player-radio-audio"
        />
      )}

      {isPodcast && podcast && (
        <AudioPlayer
          src={getProxyURL(podcast.audio_url)}
          autoPlay={isPlaying}
          audioRef={podcastRef}
          preload="auto"
          onPlay={() => setPlayingState(true)}
          onPause={() => setPlayingState(false)}
          onLoadedMetadata={setupDuration}
          onTimeUpdate={setupProgress}
          onEnded={() => {
            sendFinishProgress()
            handleSongEnded()
          }}
          onLoadStart={setupInitialVolume}
          data-testid="player-podcast-audio"
        />
      )}
    </footer>
  )
}
