import * as React from 'react'
import { cleanup, render, screen } from '@testing-library/react'
import { AudioQualityInfo } from './AudioQualityInfo'

describe('<AudioQualityInfo />', () => {
  afterEach(cleanup)

  it('renders full spec breakdown for a hi-res FLAC', () => {
    render(
      <AudioQualityInfo
        record={{
          suffix: 'flac',
          codec: 'flac',
          bitRate: 2304,
          sampleRate: 96000,
          bitDepth: 24,
          channels: 2,
        }}
      />,
    )
    expect(screen.getByText('Hi-Res 24/96')).toBeInTheDocument()
    expect(screen.getByText('96 kHz')).toBeInTheDocument()
    expect(screen.getByText('24-bit')).toBeInTheDocument()
    expect(screen.getByText('Stereo')).toBeInTheDocument()
    expect(screen.getByText('2304 kbps')).toBeInTheDocument()
    expect(screen.getByText('FLAC')).toBeInTheDocument()
  })

  it('renders DSD badge and MHz rate for DSF files', () => {
    render(
      <AudioQualityInfo
        record={{
          suffix: 'dsf',
          codec: 'dsd',
          bitRate: 5644,
          sampleRate: 2822400,
          bitDepth: 1,
          channels: 2,
        }}
      />,
    )
    expect(screen.getByText('DSD64')).toBeInTheDocument()
    expect(screen.getByText('2.8224 MHz')).toBeInTheDocument()
    expect(screen.getByText('1-bit (DSD)')).toBeInTheDocument()
  })

  it('shows container and codec when they differ', () => {
    render(
      <AudioQualityInfo
        record={{
          suffix: 'm4a',
          codec: 'alac',
          bitRate: 890,
          sampleRate: 44100,
          bitDepth: 16,
          channels: 2,
        }}
      />,
    )
    expect(screen.getByText('M4A · ALAC')).toBeInTheDocument()
    expect(screen.getByText('CD 16/44.1')).toBeInTheDocument()
  })

  it('renders ReplayGain values in dB and dBFS', () => {
    render(
      <AudioQualityInfo
        record={{
          suffix: 'flac',
          codec: 'flac',
          bitRate: 1008,
          sampleRate: 44100,
          bitDepth: 16,
          channels: 2,
          rgTrackGain: -2.3,
          rgTrackPeak: 0.5,
          rgAlbumGain: -5,
          rgAlbumPeak: 1,
        }}
      />,
    )
    expect(screen.getByText('-2.30 dB')).toBeInTheDocument()
    expect(screen.getByText('-6.02 dBFS')).toBeInTheDocument()
    expect(screen.getByText('-5.00 dB')).toBeInTheDocument()
    expect(screen.getByText('0.00 dBFS')).toBeInTheDocument()
  })

  it('hides missing specs without breaking', () => {
    render(<AudioQualityInfo record={{ suffix: 'mp3', bitRate: 128 }} />)
    expect(screen.getByText('MP3 128 kbps')).toBeInTheDocument()
    expect(screen.queryByText(/kHz/)).not.toBeInTheDocument()
    expect(screen.queryByText(/-bit/)).not.toBeInTheDocument()
  })

  it('does not break with an empty record', () => {
    render(<AudioQualityInfo record={{}} />)
    expect(screen.getByText('N/A')).toBeInTheDocument()
  })
})
