import { common as esCommon } from './es/common'
import { common as enCommon } from './en/common'
import { auth as esAuth } from './es/auth'
import { auth as enAuth } from './en/auth'
import { customer as esCustomer } from './es/customer'
import { customer as enCustomer } from './en/customer'
import { waiter as esWaiter } from './es/waiter'
import { waiter as enWaiter } from './en/waiter'
import { kitchen as esKitchen } from './es/kitchen'
import { kitchen as enKitchen } from './en/kitchen'
import { admin as esAdmin } from './es/admin'
import { admin as enAdmin } from './en/admin'

export const dictionaries = {
  es: { common: esCommon, auth: esAuth, customer: esCustomer, waiter: esWaiter, kitchen: esKitchen, admin: esAdmin },
  en: { common: enCommon, auth: enAuth, customer: enCustomer, waiter: enWaiter, kitchen: enKitchen, admin: enAdmin },
}

export type Namespace = keyof typeof dictionaries.es
