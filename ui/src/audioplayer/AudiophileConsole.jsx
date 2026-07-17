import React, { useEffect, useRef, useState } from 'react'
import { makeStyles } from '@material-ui/core/styles'
import { RiCloseLine, RiHeartFill, RiHeartLine, RiStarFill, RiStarLine } from 'react-icons/ri'
import subsonic from '../subsonic'

const useStyles = makeStyles((theme) => ({
  consoleContainer: {
    position: 'fixed',
    bottom: 114,
    left: 24,
    right: 24,
    height: 320,
    backgroundColor: 'rgba(15, 14, 13, 0.88)',
    backdropFilter: 'blur(28px)',
    '-webkit-backdrop-filter': 'blur(28px)',
    border: '1px solid rgba(223, 177, 91, 0.22)',
    borderRadius: 16,
    boxShadow: '0 24px 60px rgba(0, 0, 0, 0.75), inset 0 1px 1px rgba(255, 255, 255, 0.1)',
    zIndex: 99,
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
    animation: '$slideUp 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
    color: '#ffffff',
    fontFamily: "'Outfit', sans-serif",
    '@media (max-width: 960px)': {
      height: 'auto',
      maxHeight: '70vh',
      overflowY: 'auto',
      bottom: 120,
    },
  },
  '@keyframes slideUp': {
    from: {
      transform: 'translateY(20px)',
      opacity: 0,
    },
    to: {
      transform: 'translateY(0)',
      opacity: 1,
    },
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '12px 24px',
    borderBottom: '1px solid rgba(223, 177, 91, 0.12)',
    background: 'rgba(26, 24, 23, 0.6)',
  },
  titleGroup: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
  },
  consoleTitle: {
    fontSize: '14px',
    fontWeight: 600,
    letterSpacing: '0.1em',
    color: '#dfb15b',
    textTransform: 'uppercase',
    textShadow: '0 0 8px rgba(223, 177, 91, 0.2)',
  },
  statusDot: {
    width: 6,
    height: 6,
    borderRadius: '50%',
    backgroundColor: '#00f3ff',
    boxShadow: '0 0 8px #00f3ff',
    animation: '$pulse 2s infinite',
  },
  '@keyframes pulse': {
    '0%': { transform: 'scale(0.95)', boxShadow: '0 0 0 0 rgba(0, 243, 255, 0.7)' },
    '70%': { transform: 'scale(1)', boxShadow: '0 0 0 6px rgba(0, 243, 255, 0)' },
    '100%': { transform: 'scale(0.95)', boxShadow: '0 0 0 0 rgba(0, 243, 255, 0)' },
  },
  closeBtn: {
    background: 'none',
    border: 'none',
    color: 'rgba(255, 255, 255, 0.6)',
    cursor: 'pointer',
    padding: 4,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 20,
    transition: 'all 0.2s',
    borderRadius: '50%',
    '&:hover': {
      color: '#dfb15b',
      backgroundColor: 'rgba(223, 177, 91, 0.1)',
      transform: 'rotate(90deg)',
    },
  },
  content: {
    flex: 1,
    display: 'grid',
    gridTemplateColumns: '1.2fr 1.3fr 1.1fr',
    padding: 24,
    gap: 24,
    overflow: 'hidden',
    '@media (max-width: 960px)': {
      gridTemplateColumns: '1fr',
      overflowY: 'visible',
      gap: 16,
      padding: 16,
    },
  },
  column: {
    display: 'flex',
    flexDirection: 'column',
    height: '100%',
    overflow: 'hidden',
    '@media (max-width: 960px)': {
      height: 'auto',
      overflow: 'visible',
    },
  },
  colTitle: {
    fontSize: '11px',
    fontWeight: 600,
    color: 'rgba(255, 255, 255, 0.4)',
    letterSpacing: '0.15em',
    textTransform: 'uppercase',
    marginBottom: 12,
  },
  vuCard: {
    flex: 1,
    backgroundColor: 'rgba(10, 10, 10, 0.6)',
    borderRadius: 8,
    border: '1px solid rgba(223, 177, 91, 0.1)',
    position: 'relative',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    '@media (max-width: 960px)': {
      height: 200,
      minHeight: 200,
    },
  },
  canvas: {
    width: '100%',
    height: '100%',
    display: 'block',
  },
  signalFlow: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
    backgroundColor: 'rgba(10, 10, 10, 0.4)',
    borderRadius: 8,
    border: '1px solid rgba(255, 255, 255, 0.05)',
    padding: '16px 20px',
    '@media (max-width: 960px)': {
      gap: 12,
    },
  },
  flowStep: {
    display: 'flex',
    alignItems: 'center',
    gap: 16,
    position: 'relative',
    zIndex: 1,
  },
  flowConnector: {
    position: 'absolute',
    left: 10,
    top: 14,
    width: 2,
    height: 'calc(100% - 10px)',
    background: 'linear-gradient(to bottom, #dfb15b 40%, rgba(255, 255, 255, 0.1) 100%)',
    zIndex: 0,
  },
  stepIndicator: {
    width: 22,
    height: 22,
    borderRadius: '50%',
    border: '2px solid rgba(255, 255, 255, 0.1)',
    backgroundColor: 'rgba(15, 14, 13, 0.9)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '9px',
    fontWeight: 700,
    color: 'rgba(255, 255, 255, 0.5)',
  },
  stepIndicatorActive: {
    borderColor: '#dfb15b',
    color: '#dfb15b',
    boxShadow: '0 0 10px rgba(223, 177, 91, 0.4)',
  },
  stepDetails: {
    display: 'flex',
    flexDirection: 'column',
  },
  stepTitle: {
    fontSize: '11px',
    fontWeight: 600,
    color: 'rgba(255, 255, 255, 0.9)',
  },
  stepDesc: {
    fontSize: '10px',
    color: 'rgba(255, 255, 255, 0.45)',
    marginTop: 2,
  },
  ledBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    padding: '2px 8px',
    borderRadius: 12,
    fontSize: '9px',
    fontWeight: 700,
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
    marginTop: 4,
  },
  reviewPanel: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    backgroundColor: 'rgba(10, 10, 10, 0.4)',
    borderRadius: 8,
    border: '1px solid rgba(255, 255, 255, 0.05)',
    padding: 16,
    gap: 12,
  },
  ratingRow: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  stars: {
    display: 'flex',
    gap: 6,
  },
  starIcon: {
    cursor: 'pointer',
    fontSize: 18,
    color: 'rgba(255, 255, 255, 0.2)',
    transition: 'all 0.15s ease',
    '&:hover': {
      transform: 'scale(1.15)',
      color: '#dfb15b',
    },
  },
  starActive: {
    color: '#dfb15b',
    filter: 'drop-shadow(0 0 4px rgba(223, 177, 91, 0.5))',
  },
  loveBtn: {
    background: 'none',
    border: 'none',
    color: 'rgba(255, 255, 255, 0.3)',
    cursor: 'pointer',
    padding: 4,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 20,
    transition: 'all 0.15s ease',
    '&:hover': {
      transform: 'scale(1.15)',
      color: '#f50057',
    },
  },
  loved: {
    color: '#f50057',
    filter: 'drop-shadow(0 0 5px rgba(245, 0, 87, 0.5))',
  },
  notesArea: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    borderRadius: 6,
    padding: 10,
    color: '#ffffff',
    fontSize: '11px',
    resize: 'none',
    outline: 'none',
    fontFamily: "'Outfit', sans-serif",
    transition: 'border-color 0.2s',
    '&:focus': {
      borderColor: 'rgba(223, 177, 91, 0.4)',
    },
  },
}))

const AudiophileConsole = ({ open, onClose, analyser, isPlaying, currentSong }) => {
  const classes = useStyles()
  const canvasRef = useRef(null)
  const [rating, setRating] = useState(0)
  const [isLoved, setIsLoved] = useState(false)
  const [notes, setNotes] = useState('')

  // Needle Physics State
  const needleLeftPos = useRef(0)
  const needleRightPos = useRef(0)
  const needleLeftVel = useRef(0)
  const needleRightVel = useRef(0)

  // Sync Ratings & Notes from local storage and song data
  useEffect(() => {
    if (currentSong) {
      setRating(currentSong.rating || 0)
      setIsLoved(!!currentSong.starred)

      const savedNotes = localStorage.getItem(`navidrome_notes_${currentSong.id}`) || ''
      setNotes(savedNotes)
    }
  }, [currentSong])

  // Save Notes with local cache
  const handleNotesChange = (e) => {
    const val = e.target.value
    setNotes(val)
    if (currentSong) {
      localStorage.setItem(`navidrome_notes_${currentSong.id}`, val)
    }
  }

  // Handle rating change
  const handleRate = async (val) => {
    if (!currentSong) return
    const newVal = rating === val ? 0 : val
    setRating(newVal)
    try {
      await subsonic.setRating(currentSong.id, newVal)
    } catch (err) {
      console.error('Error setting rating in subsonic:', err)
    }
  }

  // Handle love toggle
  const handleToggleLove = async () => {
    if (!currentSong) return
    const newLove = !isLoved
    setIsLoved(newLove)
    try {
      const toggle = newLove ? subsonic.star : subsonic.unstar
      await toggle(currentSong.id)
    } catch (err) {
      console.error('Error toggling love status in subsonic:', err)
    }
  }

  // Double VU Meters Canvas Rendering
  useEffect(() => {
    if (!open) return

    const canvas = canvasRef.current
    if (!canvas) return

    const ctx = canvas.getContext('2d')
    let animationId

    const dataArray = analyser ? new Uint8Array(analyser.frequencyBinCount) : null

    // Canvas sizing setup
    const resizeCanvas = () => {
      const rect = canvas.getBoundingClientRect()
      canvas.width = rect.width * window.devicePixelRatio
      canvas.height = rect.height * window.devicePixelRatio
    }
    resizeCanvas()
    window.addEventListener('resize', resizeCanvas)

    const drawMeter = (cx, cy, radius, needleVal, title, isClip) => {
      // Scale canvas operations by device pixel ratio
      const dpr = window.devicePixelRatio
      ctx.save()
      ctx.scale(dpr, dpr)

      // Draw background dial face
      ctx.beginPath()
      ctx.arc(cx, cy + radius * 0.1, radius, Math.PI * 1.15, Math.PI * 1.85)
      const dialGrad = ctx.createRadialGradient(cx, cy, radius * 0.2, cx, cy, radius)
      dialGrad.addColorStop(0, '#0a0d14')
      dialGrad.addColorStop(0.7, '#111724')
      dialGrad.addColorStop(1, '#070a10')
      ctx.fillStyle = dialGrad
      ctx.fill()
      ctx.lineWidth = 1.5
      ctx.strokeStyle = 'rgba(223, 177, 91, 0.25)'
      ctx.stroke()

      // Draw beautiful McIntosh Blue soft glow
      ctx.beginPath()
      ctx.arc(cx, cy + radius * 0.1, radius * 0.95, Math.PI * 1.15, Math.PI * 1.85)
      ctx.strokeStyle = isPlaying ? 'rgba(0, 168, 255, 0.18)' : 'rgba(0, 168, 255, 0.04)'
      ctx.lineWidth = 8
      ctx.stroke()

      // Draw scale grid ticks
      const startAngle = Math.PI * 1.2
      const endAngle = Math.PI * 1.8
      const steps = 14

      ctx.save()
      for (let i = 0; i <= steps; i++) {
        const angle = startAngle + (endAngle - startAngle) * (i / steps)
        const innerRad = radius * 0.82
        const outerRad = radius * (i % 2 === 0 ? 0.9 : 0.86)

        const x1 = cx + innerRad * Math.cos(angle)
        const y1 = cy + innerRad * Math.sin(angle)
        const x2 = cx + outerRad * Math.cos(angle)
        const y2 = cy + outerRad * Math.sin(angle)

        ctx.beginPath()
        ctx.moveTo(x1, y1)
        ctx.lineTo(x2, y2)
        ctx.lineWidth = i % 2 === 0 ? 1.5 : 0.75
        // Highlight critical +dB zone in warm gold/red
        ctx.strokeStyle = i >= steps - 2 ? '#ff4d4d' : 'rgba(255, 255, 255, 0.35)'
        ctx.stroke()
      }
      ctx.restore()

      // Draw simple dB text labels
      ctx.fillStyle = 'rgba(255, 255, 255, 0.4)'
      ctx.font = '7px Outfit'
      ctx.textAlign = 'center'
      
      const lblAngle1 = startAngle + (endAngle - startAngle) * 0.1
      const lblAngle2 = startAngle + (endAngle - startAngle) * 0.5
      const lblAngle3 = startAngle + (endAngle - startAngle) * 0.88
      
      ctx.fillText('-20dB', cx + radius * 0.73 * Math.cos(lblAngle1), cy + radius * 0.73 * Math.sin(lblAngle1))
      ctx.fillText('0dB', cx + radius * 0.73 * Math.cos(lblAngle2), cy + radius * 0.73 * Math.sin(lblAngle2))
      ctx.fillText('+3dB', cx + radius * 0.73 * Math.cos(lblAngle3), cy + radius * 0.73 * Math.sin(lblAngle3))

      // Draw Meter Title (L/R)
      ctx.fillStyle = 'rgba(223, 177, 91, 0.75)'
      ctx.font = 'bold 8px Outfit'
      ctx.fillText(title, cx, cy - radius * 0.3)

      // Draw clip LED
      ctx.beginPath()
      ctx.arc(cx, cy - radius * 0.55, 3, 0, Math.PI * 2)
      ctx.fillStyle = isClip ? 'rgba(255, 77, 77, 0.95)' : 'rgba(255, 77, 77, 0.15)'
      if (isClip) {
        ctx.shadowColor = '#ff4d4d'
        ctx.shadowBlur = 6
      }
      ctx.fill()
      ctx.shadowBlur = 0 // reset shadow

      // Draw the mechanical Needle pointer
      const targetAngle = startAngle + (endAngle - startAngle) * needleVal
      const nLen = radius * 0.86
      const nx = cx + nLen * Math.cos(targetAngle)
      const ny = cy + nLen * Math.sin(targetAngle)

      ctx.beginPath()
      ctx.moveTo(cx, cy + radius * 0.08)
      ctx.lineTo(nx, ny)
      ctx.lineWidth = 1.2
      ctx.strokeStyle = '#dfb15b'
      ctx.shadowColor = 'rgba(223, 177, 91, 0.5)'
      ctx.shadowBlur = 3
      ctx.stroke()
      ctx.shadowBlur = 0 // reset

      // Draw metallic pivot centerpiece
      ctx.beginPath()
      ctx.arc(cx, cy + radius * 0.08, radius * 0.12, 0, Math.PI * 2)
      const metGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius * 0.12)
      metGrad.addColorStop(0, '#555555')
      metGrad.addColorStop(0.7, '#1c1c1c')
      metGrad.addColorStop(1, '#0e0e0e')
      ctx.fillStyle = metGrad
      ctx.fill()
      ctx.lineWidth = 1
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)'
      ctx.stroke()

      ctx.restore()
    }

    const renderLoop = () => {
      // Clear with full frame reset
      ctx.clearRect(0, 0, canvas.width, canvas.height)

      let lTarget = 0
      let rTarget = 0

      if (isPlaying) {
        if (analyser && dataArray) {
          analyser.getByteFrequencyData(dataArray)
          
          // Compute Left (Bass/Lower bands)
          let lSum = 0
          for (let i = 0; i < 8; i++) lSum += dataArray[i]
          lTarget = lSum / 8 / 255

          // Compute Right (Mid/Higher bands)
          let rSum = 0
          for (let i = 8; i < 24; i++) rSum += dataArray[i]
          rTarget = rSum / 16 / 255

          // Boost the dynamic range slightly for better needle visuals
          lTarget = Math.pow(lTarget, 1.2) * 1.15
          rTarget = Math.pow(rTarget, 1.2) * 1.15

          // Clamp inputs safely to [0, 1]
          lTarget = Math.min(Math.max(lTarget, 0), 1.05)
          rTarget = Math.min(Math.max(rTarget, 0), 1.05)
        } else {
          // Subtle organic breathing simulation if Web Audio hasn't loaded yet
          const time = Date.now() * 0.003
          lTarget = 0.35 + Math.sin(time) * 0.15 + Math.cos(time * 2.1) * 0.06
          rTarget = 0.38 + Math.cos(time * 0.9) * 0.14 + Math.sin(time * 1.7) * 0.05
        }
      } else {
        // Drop smoothly to rest pin when paused
        lTarget = 0
        rTarget = 0
      }

      // Spring-Damper Mechanical Physics Integration
      const K = 0.14  // Spring constant
      const D = 0.72  // Damping coefficient

      // Left Needle mechanics
      needleLeftVel.current += (lTarget - needleLeftPos.current) * K
      needleLeftVel.current *= D
      needleLeftPos.current += needleLeftVel.current

      // Right Needle mechanics
      needleRightVel.current += (rTarget - needleRightPos.current) * K
      needleRightVel.current *= D
      needleRightPos.current += needleRightVel.current

      // Visual dimensions
      const dpr = window.devicePixelRatio
      const logicalWidth = canvas.width / dpr
      const logicalHeight = canvas.height / dpr

      const halfWidth = logicalWidth / 2
      const radius = Math.min(halfWidth * 0.75, logicalHeight * 0.85)

      // Center anchors for left & right channels
      const cy = logicalHeight * 0.95
      const cxLeft = halfWidth * 0.5
      const cxRight = halfWidth * 1.5

      // Clip indicator flash state
      const isLeftClip = needleLeftPos.current > 0.88
      const isRightClip = needleRightPos.current > 0.88

      drawMeter(cxLeft, cy, radius, needleLeftPos.current, 'LEFT CHANNEL', isLeftClip)
      drawMeter(cxRight, cy, radius, needleRightPos.current, 'RIGHT CHANNEL', isRightClip)

      animationId = requestAnimationFrame(renderLoop)
    }

    renderLoop()

    return () => {
      cancelAnimationFrame(animationId)
      window.removeEventListener('resize', resizeCanvas)
    }
  }, [open, analyser, isPlaying])

  if (!open) return null

  // Extract Song Metadata Details
  const codec = currentSong?.suffix?.toUpperCase() || 'PCM'
  const bitRate = currentSong?.bitRate ? `${currentSong.bitRate} kbps` : ''
  const isLossless = currentSong?.bitDepth && currentSong?.sampleRate
  const resText = isLossless 
    ? `${currentSong.bitDepth}-bit / ${(currentSong.sampleRate / 1000).toFixed(1)} kHz` 
    : bitRate

  // Map Audio Quality to matching gorgeous LED indicators and colors
  let ledColor = '#888888' // Default standard PCM
  let ledLabel = 'STANDARD PCM'
  let ledGlow = 'rgba(136, 136, 136, 0.2)'

  const isDSD = currentSong?.suffix?.toLowerCase() === 'dsf' || currentSong?.suffix?.toLowerCase() === 'dff'
  const isHiRes = currentSong?.sampleRate > 48000 || currentSong?.bitDepth > 16

  if (isDSD) {
    ledColor = '#dfb15b' // Gold Champagne
    ledLabel = 'STUDIO DSD'
    ledGlow = 'rgba(223, 177, 91, 0.45)'
  } else if (isHiRes) {
    ledColor = '#00f3ff' // Glowing Cyan
    ledLabel = 'HI-RES LOSSLESS'
    ledGlow = 'rgba(0, 243, 255, 0.45)'
  } else if (isLossless) {
    ledColor = '#e5e9f0' // Platinum Silver
    ledLabel = 'CD LOSSLESS'
    ledGlow = 'rgba(229, 233, 240, 0.45)'
  }

  return (
    <div className={classes.consoleContainer}>
      <div className={classes.header}>
        <div className={classes.titleGroup}>
          <div className={classes.statusDot} />
          <span className={classes.consoleTitle}>Audiophile Analog Console</span>
        </div>
        <button className={classes.closeBtn} onClick={onClose}>
          <RiCloseLine />
        </button>
      </div>

      <div className={classes.content}>
        {/* LEFT COLUMN: Twin VU Meters Canvas */}
        <div className={classes.column}>
          <span className={classes.colTitle}>System Analog Monitor</span>
          <div className={classes.vuCard}>
            <canvas ref={canvasRef} className={classes.canvas} />
          </div>
        </div>

        {/* CENTER COLUMN: Roon-Style Signal Path */}
        <div className={classes.column}>
          <span className={classes.colTitle}>Digital Audio Signal Path</span>
          <div className={classes.signalFlow}>
            {/* Step 1: Source */}
            <div className={classes.flowStep}>
              <div className={`${classes.stepIndicator} ${classes.stepIndicatorActive}`}>1</div>
              <div className={classes.stepDetails}>
                <span className={classes.stepTitle}>Source File</span>
                <span className={classes.stepDesc}>
                  {codec} • {resText}
                </span>
                <div 
                  className={classes.ledBadge}
                  style={{ 
                    backgroundColor: `rgba(${ledColor === '#dfb15b' ? '223, 177, 91' : ledColor === '#00f3ff' ? '0, 243, 255' : '229, 233, 240'}, 0.08)`,
                    color: ledColor,
                    border: `1px solid ${ledColor}40`,
                    boxShadow: `0 0 8px ${ledGlow}`
                  }}
                >
                  <span style={{ width: 5, height: 5, borderRadius: '50%', backgroundColor: ledColor, boxShadow: `0 0 6px ${ledColor}` }} />
                  {ledLabel}
                </div>
              </div>
            </div>

            {/* Step 2: Stream Transmission */}
            <div className={classes.flowStep}>
              <div className={classes.flowConnector} />
              <div className={`${classes.stepIndicator} ${classes.stepIndicatorActive}`}>2</div>
              <div className={classes.stepDetails}>
                <span className={classes.stepTitle}>Transmission Protocol</span>
                <span className={classes.stepDesc}>Direct Play (Low-Latency Stream Buffer)</span>
              </div>
            </div>

            {/* Step 3: Web Audio DSP */}
            <div className={classes.flowStep}>
              <div className={classes.flowConnector} />
              <div className={`${classes.stepIndicator} ${classes.stepIndicatorActive}`}>3</div>
              <div className={classes.stepDetails}>
                <span className={classes.stepTitle}>Web Audio DSP</span>
                <span className={classes.stepDesc}>Gain Compensation • Volume Gain Engine</span>
              </div>
            </div>

            {/* Step 4: System Output */}
            <div className={classes.flowStep}>
              <div className={`${classes.stepIndicator} ${classes.stepIndicatorActive}`}>4</div>
              <div className={classes.stepDetails}>
                <span className={classes.stepTitle}>Browser Audio Destination</span>
                <span className={classes.stepDesc}>System Hardware Interface</span>
              </div>
            </div>
          </div>
        </div>

        {/* RIGHT COLUMN: Review, Note, and Rating Panel */}
        <div className={classes.column}>
          <span className={classes.colTitle}>Mastering Quality Review</span>
          <div className={classes.reviewPanel}>
            <div className={classes.ratingRow}>
              <div className={classes.stars}>
                {[1, 2, 3, 4, 5].map((star) => (
                  <span key={star} onClick={() => handleRate(star)}>
                    {star <= rating ? (
                      <RiStarFill className={`${classes.starIcon} ${classes.starActive}`} />
                    ) : (
                      <RiStarLine className={classes.starIcon} />
                    )}
                  </span>
                ))}
              </div>
              <button 
                className={`${classes.loveBtn} ${isLoved ? classes.loved : ''}`}
                onClick={handleToggleLove}
              >
                {isLoved ? <RiHeartFill /> : <RiHeartLine />}
              </button>
            </div>
            
            <textarea
              className={classes.notesArea}
              placeholder="Nhập ghi chú cảm âm phòng thu (ví dụ: Độ chi tiết dải cao xuất sắc, dải trầm căng tròn lực nảy...)"
              value={notes}
              onChange={handleNotesChange}
            />
          </div>
        </div>
      </div>
    </div>
  )
}

export default AudiophileConsole
