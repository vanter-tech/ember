import { useQuery, useMutation } from '@tanstack/react-query'
import { menuServices, SessionTableService } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import toast from 'react-hot-toast'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardTitle,
  CardHeader,
} from '@/components/ui/card'
import { useState } from 'react'
import { ArrowLeft, ChevronRight, Clock, MapPin, Phone, Plus, Receipt, UtensilsCrossed } from 'lucide-react'
import { LanguageFabButton } from '@/components/LanguageFabButton'
import { EmptyState } from '@/components/EmptyState'
import { useTranslation } from '@/lib/i18n'
import { useSessionStore } from '@/store/sessionStore'
import { useSettingStore } from '@/store/settingStore'
import { hoursLines } from '@/lib/receiptBusinessInfo'
import { cn } from '@/lib/utils'
import { LoyaltySection } from './components/LoyaltySection'
import { SelectModifiersModal } from './components/SelectModifiersModal'
import { useNavigate } from 'react-router-dom'
import type { MenuItemResponse, menuResponse } from '@/lib/api'

type MenuStep = 'welcome' | 'categories' | 'items'

interface CategoryCardProps {
  category: menuResponse
  onClick: () => void
  variant?: 'hero' | 'grid'
  style?: React.CSSProperties
  className?: string
}

const CategoryCard = ({ category, onClick, variant = 'grid', style, className }: CategoryCardProps) => {
  const { t } = useTranslation('customer')
  const isHero = variant === 'hero'

  return (
    <Card
      style={style}
      className={cn(
        `rounded-4xl shadow-sm hover:shadow-md transition-shadow relative overflow-hidden cursor-pointer animate-in fade-in slide-in-from-bottom-2 fill-mode-both duration-300 ${isHero ? 'min-h-96' : 'min-h-56'}`,
        className
      )}
      onClick={onClick}
    >
      {category.imgUrl ? (
        <img
          src={category.imgUrl}
          alt={category.name}
          className="absolute inset-0 w-full h-full object-cover z-0"
        />
      ) : (
        <div className="absolute inset-0 flex items-center justify-center bg-gray-100 z-0">
          <UtensilsCrossed className={isHero ? 'w-14 h-14 text-gray-300' : 'w-8 h-8 text-gray-300'} />
        </div>
      )}
      <div className="absolute inset-0 bg-linear-to-t from-black/90 via-black/40 to-transparent z-10"></div>
      <Badge
        className={`absolute top-4 right-4 font-bold text-[#8c1717] bg-white rounded-full shadow-md z-20 ${isHero ? 'text-sm px-4 py-1.5' : 'text-xs px-3 py-1'}`}
      >
        {t('categoryItemCountLabel', { count: category.items?.length ?? 0 })}
      </Badge>
      <CardHeader className={`absolute inset-0 flex flex-col justify-end z-20 text-white ${isHero ? 'p-8' : 'p-4'}`}>
        <CardTitle className={isHero ? 'text-4xl font-bold' : 'text-xl font-bold'}>
          {category.name}
        </CardTitle>
        {category.description && (
          <CardDescription className={`text-white/80 ${isHero ? 'text-base max-w-lg' : 'text-sm'}`}>
            {category.description}
          </CardDescription>
        )}
      </CardHeader>
    </Card>
  )
}

export const Menu = () => {
  const [activeCategory, setActiveCategory] = useState<number | undefined>()
  const [selectingItem, setSelectingItem] = useState<MenuItemResponse | null>(null)
  const sessionId = useSessionStore((state) => state.id)
  const joinCode = useSessionStore((state) => state.joinCode)
  const hasSeenMenuWelcome = useSessionStore((state) => state.hasSeenMenuWelcome)
  const markMenuWelcomeSeen = useSessionStore((state) => state.markMenuWelcomeSeen)
  const [step, setStep] = useState<MenuStep>(hasSeenMenuWelcome ? 'categories' : 'welcome')
  const navigate = useNavigate()
  const { t } = useTranslation('customer')
  const { settings } = useSettingStore()

  const mutation = useMutation({
    mutationFn: async ({
      sessionId,
      itemId,
      selectedOptionIds,
    }: {
      sessionId: string
      itemId: number
      selectedOptionIds?: number[]
    }) => {
      if (!sessionId) throw new Error('No session ID available')
      return SessionTableService.addItem(sessionId, itemId, selectedOptionIds)
    },
    onSuccess: () => {
      toast.success(t('itemAddedToast'))
      setSelectingItem(null)
    },
    onError: () => {
      toast.error(t('itemAddErrorToast'))
    },
  })

  const {
    data: menuItems = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['digital-menu'],
    queryFn: () => menuServices.getMenu(),
  })

  // Default to the first category once the menu loads. Adjusting state during render (rather than
  // in an effect) is React's recommended pattern here — it converges after one extra render and
  // avoids the flash of an unselected category a post-paint effect would cause.
  if (menuItems.length > 0 && activeCategory === undefined) {
    setActiveCategory(menuItems[0].id)
  }

  const activeCategoryData = menuItems.find((item) => item.id == activeCategory)
  const itemsCategory = activeCategoryData?.items || []

  const businessName = settings?.branding?.businessName || 'Ember'
  const phone = settings?.branding?.phone?.trim()
  const address = settings?.branding?.address?.trim()
  const schedule = hoursLines(settings)

  const goToCategories = () => {
    markMenuWelcomeSeen()
    setStep('categories')
  }

  const goToItems = (categoryId?: number) => {
    setActiveCategory(categoryId)
    setStep('items')
  }

  const handleBack = () => {
    if (step === 'items') setStep('categories')
    else if (step === 'categories') setStep('welcome')
    else navigate('/customer/home')
  }

  if (step !== 'welcome') {
    if (isLoading)
      return <div className="p-6 text-zinc-500">{t('loadingItems')}</div>
    if (isError)
      return (
        <div className="p-6 text-red-500">{t('loadingItemsError')}</div>
      )
  }

  return (
    <>
      <div className="p-2">
        <div className="flex items-center w-full h-20 justify-items-start shadow-sm rounded-3xl p-4 gap-4">
          <Button
            className="h-10 w-10 sm:h-13 sm:w-13 rounded-full hover:bg-gray-200"
            onClick={handleBack}
          >
            <ArrowLeft className="w-4 h-4 sm:w-5 sm:h-5" />
          </Button>
          <h1 className="text-3xl font-bold text-[#8c1717] tracking-tight">
            Ember
          </h1>
          <div className="ml-auto">
            <LanguageFabButton
              side="bottom"
              className="h-10 w-10 text-xs sm:h-13 sm:w-13 sm:text-sm"
            />
          </div>
        </div>

        {step === 'welcome' && (
          <div className="flex flex-col items-center justify-center gap-3 text-center px-6 min-h-[70vh] animate-in fade-in slide-in-from-bottom-2 duration-300">
            <h2 className="text-3xl font-bold text-[#8c1717]">
              {t('welcomeGreeting', { name: businessName })}
            </h2>
            {phone && (
              <p className="flex items-center gap-2 text-sm text-gray-600">
                <Phone className="w-4 h-4 shrink-0" /> {t('welcomePhoneLabel', { phone })}
              </p>
            )}
            {address && (
              <p className="flex items-center gap-2 text-sm text-gray-600">
                <MapPin className="w-4 h-4 shrink-0" /> {address}
              </p>
            )}
            {schedule.length > 0 && (
              <div className="flex flex-col items-center gap-0.5 text-sm text-gray-600">
                {schedule.map((line, index) => (
                  <p key={index} className="flex items-center gap-2">
                    {index === 0 && <Clock className="w-4 h-4 shrink-0" />} {line}
                  </p>
                ))}
              </div>
            )}
            <Button
              className="mt-4 rounded-full h-auto px-10 py-6 text-lg font-semibold bg-[#8c1717] hover:bg-[#8c1717]/90"
              onClick={goToCategories}
            >
              {t('welcomeViewMenuCta')}
            </Button>
          </div>
        )}

        {step === 'categories' && (
          <div className="flex flex-col gap-4 p-4 animate-in fade-in slide-in-from-bottom-2 duration-300">
            <h1 className="text-3xl font-bold">{t('categoriesListTitle')}</h1>
            {menuItems.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-12">
                {t('welcomeCategoriesEmpty')}
              </p>
            ) : (
              <>
                <div className="flex flex-col gap-3 md:hidden">
                  {menuItems.map((category, index) => (
                    <button
                      key={category.id}
                      type="button"
                      style={{ animationDelay: `${index * 40}ms` }}
                      className="flex items-center gap-4 p-3 rounded-3xl shadow-sm hover:shadow-md transition-shadow bg-white text-left cursor-pointer animate-in fade-in slide-in-from-bottom-1 fill-mode-both duration-300"
                      onClick={() => goToItems(category.id)}
                    >
                      <div className="w-16 h-16 rounded-2xl overflow-hidden shrink-0 bg-gray-100 flex items-center justify-center">
                        {category.imgUrl ? (
                          <img
                            src={category.imgUrl}
                            alt={category.name}
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          <UtensilsCrossed className="w-6 h-6 text-gray-300" />
                        )}
                      </div>
                      <div className="flex flex-col flex-1 min-w-0 gap-1">
                        <span className="font-semibold text-lg truncate">{category.name}</span>
                        <Badge variant="outline" className="w-fit text-xs font-normal text-gray-500">
                          {t('categoryItemCountLabel', { count: category.items?.length ?? 0 })}
                        </Badge>
                        {category.description && (
                          <span className="text-sm text-gray-500 line-clamp-2">
                            {category.description}
                          </span>
                        )}
                      </div>
                      <ChevronRight className="w-5 h-5 text-gray-300 shrink-0" />
                    </button>
                  ))}
                </div>

                <div className="hidden md:flex md:flex-col gap-4">
                  <CategoryCard
                    category={menuItems[0]}
                    variant="hero"
                    onClick={() => goToItems(menuItems[0].id)}
                  />
                  {menuItems.length > 1 && (
                    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                      {(() => {
                        const restCategories = menuItems.slice(1)
                        // A last row with exactly 3 of 4 columns filled leaves one empty slot —
                        // stretch that row's last card across the remaining column instead.
                        const lastRowHasOneGap = restCategories.length % 4 === 3
                        return restCategories.map((category, index) => {
                          const isLast = index === restCategories.length - 1
                          return (
                            <CategoryCard
                              key={category.id}
                              category={category}
                              onClick={() => goToItems(category.id)}
                              style={{ animationDelay: `${Math.min(index, 10) * 40}ms` }}
                              className={isLast && lastRowHasOneGap ? 'lg:col-span-2' : undefined}
                            />
                          )
                        })
                      })()}
                    </div>
                  )}
                </div>
              </>
            )}
          </div>
        )}

        {step === 'items' && (
          <>
            <div className="flex flex-col gap-4 p-4 animate-in fade-in slide-in-from-bottom-2 duration-300">
              <div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
                <h1 className="text-3xl font-bold">{activeCategoryData?.name}</h1>
                <div className="hidden sm:flex flex-wrap items-center gap-3">
                  <Badge className="p-6 text-md font-bold flex gap-3">
                    {' '}
                    {t('tableCodeLabel', { code: joinCode ?? '' })}
                  </Badge>
                  <Button
                    variant="secondary"
                    className="rounded-full h-13 px-5"
                    onClick={() => navigate(`${sessionId}/bill`)}
                  >
                    <Receipt className="w-4 h-4 mr-2" /> {t('viewBillLabel')}
                  </Button>
                </div>
              </div>
              <LoyaltySection />
            </div>
            {itemsCategory.length === 0 ? (
              <div className="p-4">
                <EmptyState
                  icon={UtensilsCrossed}
                  title={t('itemsCategoryEmptyTitle')}
                  description={t('itemsCategoryEmptyDescription')}
                />
              </div>
            ) : (
            <div className="p-4 grid grid-cols-1 md:grid-cols-4 lg:grid-cols-4 gap-4">
              {itemsCategory.map((item, index) => (
                <Card
                  key={item.id}
                  style={{ animationDelay: `${Math.min(index, 10) * 40}ms` }}
                  className={`rounded-4xl shadow-sm hover:shadow-md transition-shadow relative overflow-hidden min-h-75 animate-in fade-in slide-in-from-bottom-2 fill-mode-both duration-300 ${index === 0 ? 'md:col-span-2 md:row-span-2' : ''} ${index === 1 ? 'md:col-span-2 md:row-span-1' : ''} `}
                >
                  <img
                    src={item.imageUrl}
                    alt={item.name}
                    className="absolute inset-0 w-full h-full object-cover z-0 "
                  />
                  <div className="absolute inset-0 bg-linear-to-t from-black/90 via-black/40 to-transparent z-10"></div>
                  <div className="absolute inset-0 flex justify-between items-end p-4 z-20 text-white">
                    <Badge
                      className="absolute top-4 left-4 px-3 py-1 text-lg p-5
                    font-bold text-[#8c1717] bg-white rounded-full shadow-md"
                    >
                      ${item.price?.toFixed(2)}
                    </Badge>
                    <CardHeader className="p-3">
                      <CardTitle className="text-2xl font-bold ">
                        {item.name}
                      </CardTitle>
                      <CardDescription>{item.description}</CardDescription>
                    </CardHeader>
                    <CardContent className="flex flex-col items-end gap-2 p-0">
                      <Button
                        className="bg-[#8c1717] hover:bg-[#8c1717]/90 text-white p-7 rounded-full shadow-md"
                        onClick={() => {
                          if (item.modifierGroups && item.modifierGroups.length > 0) {
                            setSelectingItem(item)
                          } else {
                            mutation.mutate({ sessionId: sessionId ?? '', itemId: item.id ?? 0 })
                          }
                        }}
                      >
                        <Plus className="w-5 h-5" />
                      </Button>
                    </CardContent>
                  </div>
                </Card>
              ))}
            </div>
            )}
          </>
        )}

        {selectingItem && (
          <SelectModifiersModal
            item={selectingItem}
            open={!!selectingItem}
            onOpenChange={(open) => !open && setSelectingItem(null)}
            isPending={mutation.isPending}
            onConfirm={(selectedOptionIds) =>
              mutation.mutate({ sessionId: sessionId ?? '', itemId: selectingItem.id ?? 0, selectedOptionIds })
            }
          />
        )}
      </div>
    </>
  )
}
