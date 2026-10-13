import { useState } from 'react'
import { cn } from '@/lib/utils'

interface UserAvatarProps {
  avatarUrl: string | null
  displayName: string | null
  className?: string
}

/** The user's avatar image, falling back to the first letter of their name when there is none or it fails to load. */
export function UserAvatar({ avatarUrl, displayName, className }: UserAvatarProps) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const showImage = avatarUrl !== null && avatarUrl !== failedUrl

  return (
    <div
      className={cn(
        'rounded-full overflow-hidden shrink-0 bg-fate-gold/15 border border-fate-gold/30 flex items-center justify-center font-semibold text-fate-gold',
        className,
      )}
    >
      {showImage ? (
        <img
          src={avatarUrl}
          alt={displayName ? `Аватар ${displayName}` : 'Аватар'}
          className="w-full h-full object-cover"
          onError={() => setFailedUrl(avatarUrl)}
        />
      ) : (
        displayName?.[0]?.toUpperCase()
      )}
    </div>
  )
}
