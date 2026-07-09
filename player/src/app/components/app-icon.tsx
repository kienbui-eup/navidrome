import { SVGProps } from 'react'
import { cn } from '@/lib/utils'

type AppIconProps = SVGProps<SVGSVGElement> & {
  size?: number
}

export function AppIcon({ size = 48, className, ...props }: AppIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn('text-primary', className)}
      {...props}
    >
      <defs>
        <linearGradient id="iconGoldGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#F1E5AC" />
          <stop offset="50%" stopColor="#C5A880" />
          <stop offset="100%" stopColor="#8A6F48" />
        </linearGradient>
      </defs>
      {/* Luxurious outer golden disk border */}
      <circle
        cx="50"
        cy="50"
        r="44"
        stroke="url(#iconGoldGrad)"
        strokeWidth="4"
        fill="rgba(10, 10, 10, 0.9)"
      />
      {/* Decorative tracking ring */}
      <circle
        cx="50"
        cy="50"
        r="37"
        stroke="url(#iconGoldGrad)"
        strokeWidth="1.5"
        strokeDasharray="4 3"
        opacity="0.7"
      />
      <circle
        cx="50"
        cy="50"
        r="30"
        stroke="url(#iconGoldGrad)"
        strokeWidth="0.75"
        opacity="0.4"
      />

      {/* Modern u-shaped vector curves that form a stylized 'V' around the play core */}
      <path
        d="M26 34 C26 34, 32 70, 48 81 C49 82, 51 81, 52 80 C67 69, 72 34, 72 34"
        stroke="url(#iconGoldGrad)"
        strokeWidth="4"
        strokeLinecap="round"
        strokeLinejoin="round"
        opacity="0.95"
      />

      {/* Sleek central glossy gold triangle play button */}
      <path
        d="M43 36.5 L64 50 L43 63.5 Z"
        fill="url(#iconGoldGrad)"
        opacity="0.95"
      />
    </svg>
  )
}
