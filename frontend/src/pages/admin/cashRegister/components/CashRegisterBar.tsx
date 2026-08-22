import { Button } from '@/components/ui/button'
import { Banknote, History, FileBarChart, PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { useTranslation } from '@/lib/i18n'

export type CashRegisterSection = 'history' | 'daily-report'

export const CashRegisterBar = ({
  section,
  onSectionChange,
  collapsed,
  onToggleCollapsed,
}: {
  section: CashRegisterSection
  onSectionChange: (section: CashRegisterSection) => void
  collapsed: boolean
  onToggleCollapsed: () => void
}) => {
  const { t } = useTranslation('admin')

  return (
    <nav className={`flex flex-col gap-2 ${collapsed ? 'w-fit' : 'w-64'}`}>
      <Button
        variant="default"
        className={collapsed ? 'justify-center px-2' : 'justify-start'}
        title={collapsed ? t('cashRegisterTab') : undefined}
      >
        <Banknote className={collapsed ? 'h-6 w-6' : 'mr-2 h-6 w-6'} />
        {!collapsed && t('cashRegisterTab')}
      </Button>

      {!collapsed && (
        <div className="flex flex-col gap-1 pl-6">
          <Button
            variant={section === 'history' ? 'destructive' : 'ghost'}
            size="sm"
            onClick={() => onSectionChange('history')}
            className="justify-start"
          >
            <History className="mr-2 h-6 w-6" />
            {t('shiftHistoryTab')}
          </Button>
          <Button
            variant={section === 'daily-report' ? 'destructive' : 'ghost'}
            size="sm"
            onClick={() => onSectionChange('daily-report')}
            className="justify-start"
          >
            <FileBarChart className="mr-2 h-6 w-6" />
            {t('dailyReportTab')}
          </Button>
        </div>
      )}

      <Button
        variant="ghost"
        size={collapsed ? 'icon' : 'default'}
        onClick={onToggleCollapsed}
        className="fixed left-6 bottom-4 sm:bottom-8 z-50 rounded-full bg-white dark:bg-zinc-900 shadow-2xl border border-zinc-200 dark:border-zinc-800"
        title={collapsed ? t('expandSidebarLabel') : t('collapseSidebarLabel')}
      >
        {collapsed ? <PanelLeftOpen className="h-6 w-6" /> : <PanelLeftClose className="mr-2 h-6 w-6" />}
        {!collapsed && t('collapseSidebarLabel')}
      </Button>
    </nav>
  )
}
