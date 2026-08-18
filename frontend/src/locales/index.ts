import { common as esCommon } from './es/common'
import { common as enCommon } from './en/common'
import { auth as esAuth } from './es/auth'
import { auth as enAuth } from './en/auth'

export const dictionaries = {
  es: { common: esCommon, auth: esAuth },
  en: { common: enCommon, auth: enAuth },
}

export type Namespace = keyof typeof dictionaries.es
