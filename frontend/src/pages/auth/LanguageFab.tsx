import { LanguageFabButton } from '@/components/LanguageFabButton'

/** Circular language picker pinned to the bottom-right corner of the auth screens. */
export const LanguageFab = () => (
  <div className="fixed bottom-6 right-6 z-20">
    <LanguageFabButton side="top" />
  </div>
)
