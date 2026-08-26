import { useEffect, useState } from 'react'
import { Outlet, useMatch } from 'react-router-dom'
import { InventoryHubBar } from './components/InventoryHubBar'
import { useUIStore, type InventoryHubSection } from '@/store/uiStore'
import { useTranslation } from '@/lib/i18n'

export const InventoryHub = () => {
  const { t } = useTranslation('admin')
  const setActiveInventoryHubSection = useUIStore((state) => state.setActiveInventoryHubSection)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)

  const isCategoriesRoute = useMatch('/admin/inventory/categories/*')
  const isModifiersRoute = useMatch('/admin/inventory/modifiers')
  const isStockRoute = useMatch('/admin/inventory')

  const activeSection: InventoryHubSection = isCategoriesRoute
    ? 'categories'
    : isModifiersRoute
      ? 'modifiers'
      : isStockRoute
        ? 'stock'
        : null

  useEffect(() => {
    setActiveInventoryHubSection(activeSection)
    return () => setActiveInventoryHubSection(null)
  }, [activeSection, setActiveInventoryHubSection])

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">{t('inventoryHubTitle')}</h1>
        <p className="text-sm text-muted-foreground">{t('inventoryHubSubtitle')}</p>
      </div>

      <div className="flex flex-col md:flex-row gap-8">
        <div className={`w-full shrink-0 ${sidebarCollapsed ? 'md:w-fit' : 'md:w-64'}`}>
          <InventoryHubBar
            activeSection={activeSection}
            collapsed={sidebarCollapsed}
            onToggleCollapsed={() => setSidebarCollapsed((prev) => !prev)}
          />
        </div>
        <div className="flex-1 min-w-0">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
