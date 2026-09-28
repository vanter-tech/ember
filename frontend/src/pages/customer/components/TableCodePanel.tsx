import { useTranslation } from '@/lib/i18n'

interface TableCodePanelProps {
  code: string
}

export const TableCodePanel = ({ code }: TableCodePanelProps) => {
  const { t } = useTranslation('customer')
  return (
    <>
      <div className="px-2 pb-2 mb-2 border-b border-zinc-100 flex justify-between items-center">
        <span className="text-xs font-bold text-zinc-400 uppercase tracking-wider">
          {t('tableCodePanelTitle')}
        </span>
      </div>
      <div className="flex flex-col items-center gap-1 py-4">
        <span className="text-3xl font-bold tracking-widest text-[#8c1717]">
          {code}
        </span>
      </div>
    </>
  )
}
