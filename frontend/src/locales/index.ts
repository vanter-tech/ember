import { common as esCommon } from './es/common'
import { common as enCommon } from './en/common'
import { auth as esAuth } from './es/auth'
import { auth as enAuth } from './en/auth'
import { customer as esCustomer } from './es/customer'
import { customer as enCustomer } from './en/customer'

export const dictionaries = {
  es: { common: esCommon, auth: esAuth, customer: esCustomer },
  en: { common: enCommon, auth: enAuth, customer: enCustomer },
}

export type Namespace = keyof typeof dictionaries.es
