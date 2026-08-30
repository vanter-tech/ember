import { ui, defaultLang, type Lang } from './ui';

export type { Lang } from './ui';
export { defaultLang } from './ui';

export function getLang(currentLocale: string | undefined): Lang {
	return currentLocale === 'en' ? 'en' : defaultLang;
}

/** Devuelve t('a.b.c') buscando en ui[lang] con fallback a defaultLang. */
export function useTranslations(lang: Lang) {
	return function t(key: string): string {
		return ui[lang][key] ?? ui[defaultLang][key] ?? key;
	};
}

/** Prefija /en a una ruta interna cuando el idioma es inglés. */
export function localizePath(path: string, lang: Lang): string {
	if (lang === defaultLang) return path;
	if (path === '/') return '/en/';
	// anclas del tipo "/#features"
	if (path.startsWith('/#')) return `/en/${path.slice(1)}`;
	return `/en${path}`;
}

/** Ruta equivalente en el otro idioma (para el selector). */
export function switchLocalePath(pathname: string, target: Lang): string {
	const stripped = pathname.replace(/^\/en(\/|$)/, '/');
	return target === 'en' ? localizePath(stripped || '/', 'en') : stripped || '/';
}
