import type { ImageMetadata } from 'astro';
import cart from '../assets/Cart.png';
import kitchen from '../assets/Kitchen.png';
import tableView from '../assets/Table_view.png';
import analytics from '../assets/Analytics.png';
import { useTranslations, type Lang } from '../i18n/utils';

export interface Feature {
	n: string;
	title: string;
	description: string;
	/** Concrete capabilities shown on the detail view. */
	points: string[];
	/** Captura de la vista del admin para esta funcionalidad. */
	image: ImageMetadata;
}

export function getFeatures(lang: Lang): Feature[] {
	const t = useTranslations(lang);
	return [
		{
			n: '01',
			title: t('feat.cart.title'),
			description: t('feat.cart.desc'),
			points: [t('feat.cart.p1'), t('feat.cart.p2'), t('feat.cart.p3'), t('feat.cart.p4')],
			image: cart,
		},
		{
			n: '02',
			title: t('feat.kds.title'),
			description: t('feat.kds.desc'),
			points: [t('feat.kds.p1'), t('feat.kds.p2'), t('feat.kds.p3'), t('feat.kds.p4')],
			image: kitchen,
		},
		{
			n: '03',
			title: t('feat.floor.title'),
			description: t('feat.floor.desc'),
			points: [t('feat.floor.p1'), t('feat.floor.p2'), t('feat.floor.p3'), t('feat.floor.p4')],
			image: tableView,
		},
		{
			n: '04',
			title: t('feat.analytics.title'),
			description: t('feat.analytics.desc'),
			points: [
				t('feat.analytics.p1'),
				t('feat.analytics.p2'),
				t('feat.analytics.p3'),
				t('feat.analytics.p4'),
			],
			image: analytics,
		},
	];
}
