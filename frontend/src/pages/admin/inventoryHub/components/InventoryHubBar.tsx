import { NavLink } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Boxes, Package, SlidersHorizontal, Warehouse, PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { useTranslation } from '@/lib/i18n'
import type { InventoryHubSection } from '@/store/uiStore'

export const InventoryHubBar = ({
  activeSection,
  collapsed,
  onToggleCollapsed,
}: {
  activeSection: InventoryHubSection
  collapsed: boolean
  onToggleCollapsed: () => void
}) => {
  const { t } = useTranslation('admin')

  return (
    <nav id="inventory-hub-sidebar" className={`flex flex-col gap-2 ${collapsed ? 'w-fit' : 'w-64'}`}>
      <Button
        variant="default"
        className={collapsed ? 'justify-center px-2' : 'justify-start'}
        title={collapsed ? t('catalogLabel') : undefined}
      >
        <Boxes className={collapsed ? 'h-6 w-6' : 'mr-2 h-6 w-6'} />
        {!collapsed && t('catalogLabel')}
      </Button>

      {!collapsed && (
        <div className="flex flex-col gap-1 pl-6">
          <Button
            asChild
            variant={activeSection === 'categories' ? 'destructive' : 'ghost'}
            size="sm"
            className="justify-start"
          >
            <NavLink to="/admin/inventory/categories">
              <Package className="mr-2 h-6 w-6" />
              {t('categoriesTitle')}
            </NavLink>
          </Button>
          <Button
            asChild
            variant={activeSection === 'modifiers' ? 'destructive' : 'ghost'}
            size="sm"
            className="justify-start"
          >
            <NavLink to="/admin/inventory/modifiers">
              <SlidersHorizontal className="mr-2 h-6 w-6" />
              {t('modifierGroupsTitle')}
            </NavLink>
          </Button>
          <Button
            asChild
            variant={activeSection === 'stock' ? 'destructive' : 'ghost'}
            size="sm"
            className="justify-start"
          >
            <NavLink to="/admin/inventory">
              <Warehouse className="mr-2 h-6 w-6" />
              {t('inventoryTitle')}
            </NavLink>
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
