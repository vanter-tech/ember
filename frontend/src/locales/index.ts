import { common as esCommon } from './es/common'
import { common as enCommon } from './en/common'

export const dictionaries = {
  es: { common: esCommon },
  en: { common: enCommon },
}

export type Namespace = keyof typeof dictionaries.es
