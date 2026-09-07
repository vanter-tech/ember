import { useEffect, useRef, useState } from 'react'
import jsQR from 'jsqr'
import { tokenFromScannedValue } from '@/lib/qrToken'
import { useTranslation } from '@/lib/i18n'

type Status = 'requesting' | 'scanning' | 'denied'

const cameraCapable = () =>
  typeof navigator !== 'undefined' &&
  !!navigator.mediaDevices?.getUserMedia &&
  (typeof window.isSecureContext !== 'boolean' || window.isSecureContext)

/**
 * Live camera QR scanner for the "scan the table QR" option. Calls {@link onDecoded} with the
 * session token the moment it reads a valid Ember table QR (`${origin}/menu/join?token=…`); the
 * caller then routes to `/menu/join?token=…` to finish joining. Anything that isn't an Ember QR
 * is ignored and scanning continues.
 *
 * `getUserMedia` needs a secure context — fine on prod (HTTPS) and `localhost`.
 */
export const QrScanner = ({ onDecoded }: { onDecoded: (token: string) => void }) => {
  const { t } = useTranslation('customer')
  const videoRef = useRef<HTMLVideoElement>(null)
  const capable = cameraCapable()
  const [status, setStatus] = useState<Status>('requesting')

  useEffect(() => {
    if (!capable) return

    let stream: MediaStream | null = null
    let raf = 0
    let done = false
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d', { willReadFrequently: true })

    const tick = () => {
      const video = videoRef.current
      if (done || !video || !ctx || video.readyState < video.HAVE_ENOUGH_DATA) {
        raf = requestAnimationFrame(tick)
        return
      }
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
      const image = ctx.getImageData(0, 0, canvas.width, canvas.height)
      const result = jsQR(image.data, image.width, image.height, { inversionAttempts: 'dontInvert' })
      const token = result ? tokenFromScannedValue(result.data) : null
      if (token) {
        done = true
        onDecoded(token)
        return
      }
      raf = requestAnimationFrame(tick)
    }

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' } })
      .then((s) => {
        stream = s
        const video = videoRef.current
        if (!video) return
        video.srcObject = s
        void video.play()
        setStatus('scanning')
        raf = requestAnimationFrame(tick)
      })
      .catch(() => setStatus('denied'))

    return () => {
      done = true
      cancelAnimationFrame(raf)
      stream?.getTracks().forEach((track) => track.stop())
    }
  }, [capable, onDecoded])

  if (!capable) {
    return (
      <p className="py-8 text-center text-sm text-zinc-500">{t('qrScannerUnsupported')}</p>
    )
  }

  if (status === 'denied') {
    return <p className="py-8 text-center text-sm text-zinc-500">{t('qrScannerDenied')}</p>
  }

  return (
    <div className="flex flex-col items-center gap-3">
      <div className="relative aspect-square w-full max-w-[280px] overflow-hidden rounded-2xl bg-black">
        <video
          ref={videoRef}
          className="h-full w-full object-cover"
          muted
          playsInline
          autoPlay
        />
        <div className="pointer-events-none absolute inset-6 rounded-xl border-2 border-white/80" />
      </div>
      <p className="text-center text-sm text-zinc-500">
        {status === 'requesting' ? t('qrScannerRequesting') : t('qrScannerHint')}
      </p>
    </div>
  )
}
