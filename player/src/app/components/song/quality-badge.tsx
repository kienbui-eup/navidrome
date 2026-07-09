import { TooltipPortal } from '@radix-ui/react-tooltip'
import { ReactNode, useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Badge } from '@/app/components/ui/badge'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/app/components/ui/tooltip'
import { Drawer, DrawerContent, DrawerTitle, DrawerTrigger } from '@/app/components/ui/drawer'
import { useIsMobile } from '@/app/hooks/use-mobile'
import { ISong } from '@/types/responses/song'
import { formatBitrate, formatSamplingRate } from '@/utils/audioInfo'
import { formatBytes } from '@/utils/formatBytes'

interface SongQualityBadgeProps {
  variant?: 'neutral' | 'secondary'
  song: ISong
}

// Roon Inspired Color categorization & details
const getQualityCategory = (suffix: string, samplingRate?: number, bitRate?: number) => {
  const ext = suffix.toUpperCase()
  
  // 1. Bit-Perfect / Hi-Res (Màu Tím): DSF, DFF, MQA, AIFF, TTA hoặc samplingRate >= 48kHz hoặc bitRate > 1411 (24-bit lossless)
  if (
    ['DSF', 'DFF', 'MQA', 'AIFF', 'AIF', 'TTA'].includes(ext) ||
    (samplingRate && samplingRate >= 48000) ||
    (bitRate && bitRate > 1411)
  ) {
    return {
      label: 'Bit-Perfect Hi-Res',
      color: 'bg-purple-600/90 hover:bg-purple-700/90 text-white border-none shadow-[0_0_8px_rgba(147,51,234,0.4)]',
      dotColor: 'bg-purple-400 shadow-[0_0_6px_rgba(168,85,247,0.8)]',
      textColor: 'text-purple-400',
      description: 'Luồng âm thanh phòng thu nguyên bản tuyệt đối',
      type: 'purple',
    }
  }
  
  // 2. High Quality Lossless (Màu Xanh): FLAC, WAV, ALAC, APE và các thông số CD thông thường (44.1kHz 16-bit)
  if (['FLAC', 'WAV', 'ALAC', 'APE'].includes(ext)) {
    return {
      label: 'Lossless CD-Quality',
      color: 'bg-emerald-600/90 hover:bg-emerald-700/90 text-white border-none shadow-[0_0_8px_rgba(16,185,129,0.3)]',
      dotColor: 'bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.8)]',
      textColor: 'text-emerald-400',
      description: 'Âm thanh chất lượng cao không nén hao hụt',
      type: 'emerald',
    }
  }
  
  // 3. Lossy (Màu Xám/Vàng): MP3, AAC, OGG, WMA, M4A
  return {
    label: 'Lossy Compression',
    color: 'bg-zinc-600/80 hover:bg-zinc-700/80 text-zinc-100 border-none',
    dotColor: 'bg-yellow-400 shadow-[0_0_6px_rgba(250,204,21,0.8)]',
    textColor: 'text-yellow-400',
    description: 'Âm thanh nén suy hao để tiết kiệm băng thông',
    type: 'yellow',
  }
}

export function SongQualityBadge({
  song,
  variant = 'secondary',
}: SongQualityBadgeProps) {
  const isMobile = useIsMobile()
  const { t } = useTranslation()
  const [openDrawer, setOpenDrawer] = useState(false)

  if (!song?.suffix) {
    return null
  }

  const quality = song.suffix.toUpperCase()
  const category = getQualityCategory(song.suffix, song.samplingRate, song.bitRate)
  const bitrate = formatBitrate(song.bitRate)
  const samplingRate = formatSamplingRate(song.samplingRate)
  const size = formatBytes(song.size ?? 0)

  const lines = [
    { id: 'quality', label: t('table.columns.quality'), value: quality },
    { id: 'bitrate', label: t('table.columns.bitrate'), value: bitrate },
    {
      id: 'samplingRate',
      label: t('table.columns.samplingRate'),
      value: samplingRate,
    },
    { id: 'size', label: t('table.columns.size'), value: size },
  ]

  if (isMobile) {
    return (
      <Drawer open={openDrawer} onOpenChange={setOpenDrawer}>
        <DrawerTrigger asChild>
          <Badge 
            variant="neutral" 
            className={`cursor-pointer transition-all duration-200 active:scale-95 py-0.5 px-2.5 rounded-full select-none text-xs font-bold leading-none ${category.color}`}
          >
            {quality}
          </Badge>
        </DrawerTrigger>
        <DrawerContent className="pb-8 max-h-[85vh] bg-background">
          <DrawerTitle className="sr-only">Signal Path</DrawerTitle>
          <SignalPathDetail song={song} />
        </DrawerContent>
      </Drawer>
    )
  }

  return (
    <TooltipProvider>
      <Tooltip delayDuration={100}>
        <TooltipTrigger className="cursor-default flex">
          <Badge 
            variant={variant} 
            className={`transition-all py-0.5 px-2.5 rounded-full text-xs font-bold leading-none ${category.color}`}
          >
            {quality}
          </Badge>
        </TooltipTrigger>
        <TooltipPortal>
          <TooltipContent className="flex flex-col items-center p-0 divide-y">
            {lines.map((line) => (
              <ContentLine key={line.id} label={line.label}>
                {line.value}
              </ContentLine>
            ))}
          </TooltipContent>
        </TooltipPortal>
      </Tooltip>
    </TooltipProvider>
  )
}

function SignalPathDetail({ song }: { song: ISong }) {
  const quality = song.suffix.toUpperCase()
  const bitrate = formatBitrate(song.bitRate)
  const samplingRate = formatSamplingRate(song.samplingRate)
  const size = formatBytes(song.size ?? 0)
  
  const category = useMemo(() => {
    return getQualityCategory(song.suffix, song.samplingRate, song.bitRate)
  }, [song])

  return (
    <div className="flex flex-col gap-6 p-5 max-w-md mx-auto w-full text-foreground select-none">
      {/* Header Info */}
      <div className="flex items-center gap-3 border-b pb-4 border-border/40">
        <div className={`h-3.5 w-3.5 rounded-full ${category.dotColor}`} />
        <div>
          <h3 className="font-bold text-lg leading-tight">Đường Truyền Tín Hiệu</h3>
          <p className="text-xs text-muted-foreground mt-0.5 font-medium uppercase tracking-wider">Signal Path (Roon Inspired)</p>
        </div>
      </div>

      {/* Sơ đồ tuyến tính */}
      <div className="relative pl-6 border-l border-dashed border-border/80 ml-2 flex flex-col gap-8">
        
        {/* Step 1: Source */}
        <div className="relative">
          <div className={`absolute -left-[32px] top-1 h-3.5 w-3.5 rounded-full border-2 border-background ${category.dotColor}`} />
          <div className="flex flex-col">
            <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Nguồn phát (Source File)</span>
            <span className="font-bold text-base mt-0.5 text-foreground">{quality} Audio</span>
            <div className="grid grid-cols-2 gap-y-1 gap-x-4 mt-2 p-2.5 rounded-xl bg-secondary/20 text-xs border border-border/10">
              <div className="flex flex-col">
                <span className="text-[9px] text-muted-foreground font-semibold uppercase">Tần số lấy mẫu:</span>
                <span className="font-semibold text-foreground/90 mt-0.5">{samplingRate || 'Mặc định (44.1 kHz)'}</span>
              </div>
              <div className="flex flex-col">
                <span className="text-[9px] text-muted-foreground font-semibold uppercase">Tốc độ bit:</span>
                <span className="font-semibold text-foreground/90 mt-0.5">{bitrate || 'Không nén'}</span>
              </div>
              <div className="flex flex-col col-span-2 border-t border-border/10 pt-1.5 mt-1.5">
                <span className="text-[9px] text-muted-foreground font-semibold uppercase">Dung lượng tệp tin:</span>
                <span className="font-semibold text-foreground/90 mt-0.5">{size}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Step 2: Processing */}
        <div className="relative">
          <div className="absolute -left-[32px] top-1 h-3.5 w-3.5 rounded-full border-2 border-background bg-blue-400 shadow-[0_0_6px_rgba(96,165,250,0.8)]" />
          <div className="flex flex-col">
            <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Xử lý (DSP Engine)</span>
            <span className="font-bold text-base mt-0.5 text-foreground">Aonsoku Audio Engine</span>
            <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
              Áp dụng thuật toán cân bằng âm lượng ReplayGain tự động và giải mã không suy hao tín hiệu âm thanh gốc.
            </p>
          </div>
        </div>

        {/* Step 3: Output */}
        <div className="relative">
          <div className="absolute -left-[32px] top-1 h-3.5 w-3.5 rounded-full border-2 border-background bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.8)]" />
          <div className="flex flex-col">
            <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Ngõ ra (Output Device)</span>
            <span className="font-bold text-base mt-0.5 text-foreground">Web Audio API Output</span>
            <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
              Truyền tải dòng dữ liệu âm thanh trực tiếp đến thiết bị phần cứng DAC hoặc ngõ ra Bluetooth của điện thoại di động.
            </p>
          </div>
        </div>

      </div>

      {/* Kết luận đánh giá */}
      <div className="mt-2 p-3.5 rounded-2xl bg-secondary/30 border border-border/10 flex flex-col items-center text-center">
        <span className={`font-bold text-sm tracking-wide ${category.textColor}`}>{category.label}</span>
        <p className="text-xs text-muted-foreground mt-1 leading-relaxed font-medium">{category.description}</p>
      </div>
    </div>
  )
}

interface ContentLineProps {
  label: string
  children: ReactNode
}

function ContentLine({ label, children }: ContentLineProps) {
  return (
    <div className="flex items-center justify-between w-full p-2 gap-6 text-sm">
      <p className="text-muted-foreground">{label}</p>
      <span className="font-medium">{children}</span>
    </div>
  )
}
