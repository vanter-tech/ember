import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { categoryService } from '@/lib/api'
import { Button } from '../../components/ui/button'
import { Pencil, Trash2 } from 'lucide-react'

import { useUIStore } from '@/store/uiStore'

import { NewCategoryModal } from '@/pages/admin/components/NewCategoryModal'
import { EditCategoryModal } from '@/pages/admin/components/EditCategoryModal'
import { Link } from 'react-router-dom'
import { GlobalDeleteModal } from '@/components/GlobalDeleteModal'
import { PaginationControls } from '@/components/PaginationControls'
import { SectionTour } from '@/components/tours/SectionTour'
import { useTranslation } from '@/lib/i18n'

export const Category = () => {
  const { openModal } = useUIStore()
  const [page, setPage] = useState(0)
  const { t } = useTranslation('admin')

  const tourSteps = [
    {
      target: '#category-tour-grid',
      title: t('tourCategoriesGridTitle'),
      content: t('tourCategoriesGridContent'),
      skipBeacon: true,
    },
    {
      target: '#topnav-create-button',
      title: t('tourCategoriesCreateTitle'),
      content: t('tourCategoriesCreateContent'),
    },
  ]

  const {
    data: categoriesPage,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['categories', page],
    queryFn: () => categoryService.getAll(page),
  })
  const categories = categoriesPage?.content ?? []

  if (isLoading) {
    return (
      <div className="p-6 text-zinc-500">{t('loadingCategories', { brand: 'Ember' })}</div>
    )
  }

  if (isError) {
    return (
      <div className="p-6 text-red-500">{t('loadingCategoriesError')}</div>
    )
  }

  return (
    <div>
      <div id="category-tour-grid" className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {categories.map((Category) => (
          <Link to={Category.id + '/items'}>
            <div
              key={Category.id}
              className="bg-white rounded-2xl shadow-sm overflow-hidden border
            border-zinc-100 flex flex-col "
            >
              <div className="relative h-48 bg-zinc-200">
                <img
                  src={Category.imgUrl || 'https://via.placeholder.com/400'}
                  className="w-full h-full object-cover"
                />
                <span
                  className="absolute top-4 right-4 bg-white/90 px-3 py-1 text-xs
                font-semibold text-green-700 rounded-full"
                >
                  {t('activeStatus')}
                </span>
              </div>

              <div className="p-5 pb-1 flex-1 flex flex-col">
                <div className="flex justify-between items-center mb-2">
                  <h3 className="text-xl font-bold text-zinc-800">
                    {Category.name}
                  </h3>
                  <div className="flex gap-2 text-zinc-400">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="hover-text-zinc-600 transition-colors"
                      onClick={(e) => {
                        openModal('EDIT_CATEGORY', Category)
                        e.preventDefault()
                        e.stopPropagation()
                      }}
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="hover-text-zinc-500 transition-colors"
                      onClick={(e) => {
                        openModal('DELETE_CATEGORY', Category.id)
                        e.preventDefault()
                        e.stopPropagation()
                      }}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </div>

              <p className="text-zinc-500 text-sm flex-1 m-4">
                {Category.description ||
                  t('categoryDescriptionPlaceholder')}
              </p>

              <div
                className="flex items-center p-4 border-t 
              border-zinc-100"
              >
                <span
                  className="bg-zinc-100 text-zinc-600 text-xs px-3 py-1 rounded-full
                font-medium"
                >
                  {t('productsCountLabel', { count: Category.totalItems ?? 0 })}
                </span>
              </div>
            </div>
          </Link>
        ))}
      </div>

      <PaginationControls
        page={page}
        totalPages={categoriesPage?.totalPages ?? 0}
        onPageChange={setPage}
      />

      <NewCategoryModal />
      <EditCategoryModal />
      <GlobalDeleteModal/>
      <SectionTour sectionId="admin-inventory-categories" steps={tourSteps} />
    </div>
  )
}
