import { useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import {
  Banknote,
  History,
  FileBarChart,
  Menu,
  ChevronsUpDown,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-react'
import { useTranslation } from '@/lib/i18n'

export type CashRegisterSection = 'history' | 'daily-report'

const SECTIONS = [
  { key: 'history', labelKey: 'shiftHistoryTab', Icon: History },
  { key: 'daily-report', labelKey: 'dailyReportTab', Icon: FileBarChart },
] as const

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
  const [menuOpen, setMenuOpen] = useState(false)
  const current = SECTIONS.find((s) => s.key === section)

  return (
    <>
      {/* Mobile / tablet: a single button that opens the section list in a popover. */}
      <div className="md:hidden">
        <Popover open={menuOpen} onOpenChange={setMenuOpen}>
          <PopoverTrigger asChild>
            <Button variant="outline" className="w-full justify-between">
              <span className="flex items-center">
                {current ? (
                  <current.Icon className="mr-2 h-4 w-4" />
                ) : (
                  <Menu className="mr-2 h-4 w-4" />
                )}
                {current ? t(current.labelKey) : t('sectionsMenuLabel')}
              </span>
              <ChevronsUpDown className="h-4 w-4 opacity-60" />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="flex w-[var(--radix-popover-trigger-width)] flex-col gap-1 p-2">
            {SECTIONS.map((s) => (
              <Button
                key={s.key}
                variant={section === s.key ? 'destructive' : 'ghost'}
                size="sm"
                className="justify-start"
                onClick={() => {
                  onSectionChange(s.key)
                  setMenuOpen(false)
                }}
              >
                <s.Icon className="mr-2 h-4 w-4" />
                {t(s.labelKey)}
              </Button>
            ))}
          </PopoverContent>
        </Popover>
      </div>

      <nav className={`hidden md:flex flex-col gap-2 ${collapsed ? 'w-fit' : 'w-64'}`}>
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
    </>
  )
}
