import { Fragment, useState } from 'react'
import { useSettingsStore, type SettingsType } from '@/store/uiStore'
import { Button } from './ui/button'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import {
  Store,
  Utensils,
  ConciergeBell,
  Receipt,
  CreditCard,
  FileText,
  Printer,
  Server,
  Clock,
  Gift,
  Award,
  Menu,
  ChevronsUpDown,
  PanelLeftClose,
  PanelLeftOpen,
  type LucideIcon,
} from 'lucide-react';
import { dictionaries } from '@/locales'
import { useTranslation } from '@/lib/i18n'

type SettingsGroup = 'BILLING' | 'HARDWARE' | 'FIDELIZACION'
type LeafType = Exclude<SettingsType, null>
type AdminKey = keyof (typeof dictionaries)['es']['admin']

const GROUP_MEMBERS: Record<SettingsGroup, SettingsType[]> = {
  BILLING: ['BILLING', 'PAYMENT_GATEWAY', 'TICKET'],
  HARDWARE: ['HARDWARE', 'PRINTING'],
  FIDELIZACION: ['FIDELIZACION', 'LOYALTY_REWARDS'],
}

// Label + icon for every leaf section, keyed by SettingsType.
const LEAF: Record<LeafType, { labelKey: AdminKey; Icon: LucideIcon }> = {
  BRANDING: { labelKey: 'brandingAndBusinessLabel', Icon: Store },
  MENU: { labelKey: 'menuLabel', Icon: Utensils },
  BILLING: { labelKey: 'billingLabel', Icon: Receipt },
  PAYMENT_GATEWAY: { labelKey: 'paymentGatewayCardTitle', Icon: CreditCard },
  TICKET: { labelKey: 'ticketLabel', Icon: FileText },
  HARDWARE: { labelKey: 'hardwareGeneralLabel', Icon: Printer },
  PRINTING: { labelKey: 'printingLabel', Icon: Server },
  SPACE: { labelKey: 'spaceLabel', Icon: ConciergeBell },
  HORARIO: { labelKey: 'scheduleLabel', Icon: Clock },
  FIDELIZACION: { labelKey: 'loyaltyLabel', Icon: Gift },
  LOYALTY_REWARDS: { labelKey: 'rewardCatalogTitle', Icon: Award },
}

type NavNode =
  | { kind: 'leaf'; type: LeafType }
  | { kind: 'group'; group: SettingsGroup; labelKey: AdminKey; Icon: LucideIcon; members: LeafType[] }

// Single source of truth for the section tree — rendered both by the md+ sidebar and the
// mobile popover panel.
const SETTINGS_NAV: NavNode[] = [
  { kind: 'leaf', type: 'BRANDING' },
  { kind: 'leaf', type: 'MENU' },
  { kind: 'group', group: 'BILLING', labelKey: 'billingLabel', Icon: Receipt, members: ['BILLING', 'PAYMENT_GATEWAY', 'TICKET'] },
  { kind: 'group', group: 'HARDWARE', labelKey: 'hardwareLabel', Icon: Printer, members: ['HARDWARE', 'PRINTING'] },
  { kind: 'leaf', type: 'SPACE' },
  { kind: 'leaf', type: 'HORARIO' },
  { kind: 'group', group: 'FIDELIZACION', labelKey: 'loyaltyLabel', Icon: Gift, members: ['FIDELIZACION', 'LOYALTY_REWARDS'] },
]

export const SettingsBar = ({
  collapsed,
  onToggleCollapsed,
}: {
  collapsed: boolean
  onToggleCollapsed: () => void
}) => {
  const { activeSettings, openSettings } = useSettingsStore()
  const { t } = useTranslation('admin')
  const [expandedGroup, setExpandedGroup] = useState<SettingsGroup | null>(null)
  const [menuOpen, setMenuOpen] = useState(false)

  const handleGroupClick = (group: SettingsGroup) => {
    if (expandedGroup === group) {
      setExpandedGroup(null)
      return
    }
    setExpandedGroup(group)
    if (!GROUP_MEMBERS[group].includes(activeSettings)) {
      openSettings(GROUP_MEMBERS[group][0])
    }
  }

  const handleFlatClick = (settings: SettingsType) => {
    setExpandedGroup(null)
    openSettings(settings)
  }

  // Full (non-collapsed) section tree, shared by the desktop sidebar and the mobile panel.
  // `onNavigate` lets the mobile popover close itself once a section is picked.
  const renderNavList = (onNavigate?: () => void) =>
    SETTINGS_NAV.map((node) => {
      if (node.kind === 'leaf') {
        const { labelKey, Icon } = LEAF[node.type]
        return (
          <Button
            key={node.type}
            variant={activeSettings === node.type ? 'default' : 'ghost'}
            onClick={() => {
              handleFlatClick(node.type)
              onNavigate?.()
            }}
            className="justify-start"
          >
            <Icon className="mr-2 h-4 w-4" />
            {t(labelKey)}
          </Button>
        )
      }

      const groupActive = GROUP_MEMBERS[node.group].includes(activeSettings)
      return (
        <Fragment key={node.group}>
          <Button
            variant={groupActive ? 'default' : 'ghost'}
            className="justify-start"
            onClick={() => handleGroupClick(node.group)}
          >
            <node.Icon className="mr-2 h-4 w-4" />
            {t(node.labelKey)}
          </Button>
          {expandedGroup === node.group && (
            <div className="flex flex-col gap-1 pl-6">
              {node.members.map((m) => {
                const { labelKey, Icon } = LEAF[m]
                return (
                  <Button
                    key={m}
                    variant={activeSettings === m ? 'destructive' : 'ghost'}
                    size="sm"
                    onClick={() => {
                      openSettings(m)
                      onNavigate?.()
                    }}
                    className="justify-start"
                  >
                    <Icon className="mr-2 h-4 w-4" />
                    {t(labelKey)}
                  </Button>
                )
              })}
            </div>
          )}
        </Fragment>
      )
    })

  const active = activeSettings ? LEAF[activeSettings] : null

  return (
    <>
      {/* Mobile / tablet: a single button that opens the section tree in a popover. */}
      <div className="md:hidden">
        <Popover open={menuOpen} onOpenChange={setMenuOpen}>
          <PopoverTrigger asChild>
            <Button variant="outline" className="w-full justify-between">
              <span className="flex items-center">
                {active ? (
                  <active.Icon className="mr-2 h-4 w-4" />
                ) : (
                  <Menu className="mr-2 h-4 w-4" />
                )}
                {active ? t(active.labelKey) : t('sectionsMenuLabel')}
              </span>
              <ChevronsUpDown className="h-4 w-4 opacity-60" />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="flex max-h-[70vh] w-[var(--radix-popover-trigger-width)] flex-col gap-2 overflow-y-auto p-2">
            {renderNavList(() => setMenuOpen(false))}
          </PopoverContent>
        </Popover>
      </div>

      <nav
        id="settings-tour-sidebar"
        className={`hidden md:flex flex-col gap-2 ${collapsed ? 'w-fit' : 'w-64'}`}
      >
        {collapsed
          ? SETTINGS_NAV.map((node) => {
              const { labelKey, Icon } =
                node.kind === 'leaf'
                  ? LEAF[node.type]
                  : { labelKey: node.labelKey, Icon: node.Icon }
              const isActive =
                node.kind === 'leaf'
                  ? activeSettings === node.type
                  : GROUP_MEMBERS[node.group].includes(activeSettings)
              return (
                <Button
                  key={node.kind === 'leaf' ? node.type : node.group}
                  variant={isActive ? 'default' : 'ghost'}
                  className="justify-center px-2"
                  title={t(labelKey)}
                  onClick={() =>
                    node.kind === 'leaf'
                      ? handleFlatClick(node.type)
                      : handleGroupClick(node.group)
                  }
                >
                  <Icon className="h-4 w-4" />
                </Button>
              )
            })
          : renderNavList()}

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
